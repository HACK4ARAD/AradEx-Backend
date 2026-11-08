package aradexperience.service.auth;

import aradexperience.entity.auth.OtpCode;
import aradexperience.exceptions.ApiException;
import aradexperience.exceptions.BadRequestException;
import aradexperience.exceptions.TooManyRequestsException;
import aradexperience.exceptions.UnauthorizedException;
import aradexperience.repository.auth.OtpCodeRepository;
import aradexperience.service.sms.SmsGatewayService;
import aradexperience.util.PhoneNumbers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Codurile OTP pentru conturile personale (T21).
 * - Numerele de demo (`+40799000xxx`): codul îl generăm noi, îl ținem doar ca hash și îl întoarcem în răspuns (fără SMS).
 *   Fără limite (codul e public oricum), ca nimeni să nu poată bloca conturile demo; orice cod demo încă valabil merge,
 *   ca mai mulți oameni să poată intra simultan pe același cont.
 * - Celelalte numere (doar românești, +40): dacă gateway-ul e configurat (T24), codul îl generăm noi (hash în DB) și SMS-ul
 *   pleacă de pe telefonul lui Zoltan (Mac + iPhone, `SmsGatewayService`); altfel prin Twilio Verify, care generează și
 *   verifică el codul.
 *   O cerere la 60 s și maximum 5 pe zi pe număr, maximum 5 încercări pe cod, plus o limită globală de SMS-uri pe zi.
 * Cod valabil 5 minute. Furnizorul se alege după număr, nu după ce trimite clientul (un cod de demo nu merge pe un număr real).
 * Fiecare cerere și verificare blochează numărul în tranzacție (advisory lock), ca limitele să nu poată fi ocolite în paralel.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    static final Duration TTL = Duration.ofMinutes(5);
    static final Duration COOLDOWN = Duration.ofSeconds(60);
    static final int DAILY_LIMIT = 5;
    static final int MAX_ATTEMPTS = 5;
    /** Limita globală de SMS-uri reale pe zi (contra SMS pumping); peste ea → 503. */
    static final long GLOBAL_DAILY_SMS = 250;
    /** T24: și pe oră, ca un val de cereri să nu golească plafonul zilei (și să nu pară spam pentru operator). */
    static final long GLOBAL_HOURLY_SMS = 80;
    static final String SMS_COUNTRY_PREFIX = "+40";
    static final String INVALID_CODE = "Invalid or expired code";
    /** Furnizorii care trimit SMS-uri reale (intră în limita globală pe zi). */
    static final List<OtpCode.Provider> REAL_SMS = List.of(OtpCode.Provider.TWILIO, OtpCode.Provider.GATEWAY);
    private static final Pattern SIX_DIGITS = Pattern.compile("^\\d{6}$");

    private final OtpCodeRepository codes;
    private final PasswordEncoder passwordEncoder;
    private final TwilioVerifyClient twilio;
    private final SmsGatewayService gateway;
    private final OtpIpLimiter ipLimiter;
    private final SecureRandom random = new SecureRandom();
    private Clock clock = Clock.systemUTC();

    /** Pentru teste. */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    /**
     * `retryAfterSeconds` = peste cât se poate cere alt cod; `demoCode` doar pentru numerele de demo;
     * `alreadySent` = există deja un cod nefolosit, cerut de mai puțin de 60 s: nu trimitem alt SMS, omul îl folosește pe acela.
     */
    public record RequestResult(long retryAfterSeconds, String demoCode, boolean alreadySent) {
        public RequestResult(long retryAfterSeconds, String demoCode) {
            this(retryAfterSeconds, demoCode, false);
        }
    }

    @Transactional
    public RequestResult request(String rawPhone) {
        return request(rawPhone, null);
    }

    /** `clientKey` = adresa IP a clientului (limita pe adresă, T24); null = fără limita pe adresă (apel intern). */
    @Transactional
    public RequestResult request(String rawPhone, String clientKey) {
        String phone = PhoneNumbers.normalize(rawPhone);
        boolean demo = PhoneNumbers.isDemo(phone);
        if (!demo && !phone.startsWith(SMS_COUNTRY_PREFIX)) throw new BadRequestException("SMS codes are only sent to Romanian numbers");
        codes.lockPhone(phone);
        Instant now = clock.instant();
        if (!demo) {
            // T21 fix: după logout omul cere iar cod; dacă cel de acum câteva secunde e încă bun, îl refolosim (fără 429, fără alt SMS)
            OtpCode pending = codes.findFirstByPhoneOrderByCreatedAtDesc(phone)
                    .filter(last -> usable(last, now) && Duration.between(last.getCreatedAt(), now).compareTo(COOLDOWN) < 0)
                    .orElse(null);
            if (pending != null) {
                long wait = Math.max(1, COOLDOWN.minus(Duration.between(pending.getCreatedAt(), now)).toSeconds());
                log.info("OTP for {} reused (sent {} s ago)", mask(phone), Duration.between(pending.getCreatedAt(), now).toSeconds());
                return new RequestResult(wait, null, true);
            }
            checkLimits(phone, now);
            ipLimiter.check(clientKey, now);
        }

        OtpCode c = new OtpCode();
        c.setPhone(phone);
        c.setCreatedAt(now);
        c.setExpiresAt(now.plus(TTL));
        String demoCode = null;
        if (demo) {
            demoCode = newCode();
            c.setProvider(OtpCode.Provider.LOCAL);
            c.setCodeHash(demoHash(phone, demoCode));
            codes.save(c);
        } else if (gateway.enabled()) {
            String code = newCode();
            c.setProvider(OtpCode.Provider.GATEWAY);
            c.setCodeHash(passwordEncoder.encode(code));
            codes.save(c);
            gateway.enqueue(phone, code, c.getExpiresAt()); // aceeași tranzacție: codul și SMS-ul apar împreună
        } else {
            twilio.start(phone); // dacă Twilio refuză sau lipsește, nu salvăm nimic (nu consumă limita)
            c.setProvider(OtpCode.Provider.TWILIO);
            codes.save(c);
        }
        if (!demo) ipLimiter.record(clientKey, now);
        log.info("OTP requested for {} via {}", mask(phone), c.getProvider());
        return new RequestResult(COOLDOWN.toSeconds(), demoCode);
    }

    /** Cod încă bun: nefolosit, neexpirat, sub 5 încercări greșite. */
    private static boolean usable(OtpCode c, Instant now) {
        return c.getConsumedAt() == null && c.getExpiresAt().isAfter(now) && c.getAttempts() < MAX_ATTEMPTS;
    }

    /**
     * Limitele numerelor reale: 60 s după un cod nefolosit, 5 coduri nefolosite pe zi pe număr, limita globală de SMS-uri pe zi.
     * Un cod folosit la login nu blochează nimic (cine l-a folosit are telefonul): după logout se poate cere imediat altul,
     * iar login-urile reușite nu consumă limita zilnică (aceasta prinde doar codurile cerute și nefolosite, adică abuzul).
     */
    private void checkLimits(String phone, Instant now) {
        codes.findFirstByPhoneOrderByCreatedAtDesc(phone).filter(last -> last.getConsumedAt() == null).ifPresent(last -> {
            long wait = COOLDOWN.minus(Duration.between(last.getCreatedAt(), now)).toSeconds();
            if (wait > 0) throw new TooManyRequestsException("Wait before requesting a new code", wait);
        });
        Instant dayAgo = now.minus(Duration.ofDays(1));
        if (codes.countByPhoneAndCreatedAtAfterAndConsumedAtIsNull(phone, dayAgo) >= DAILY_LIMIT) {
            long wait = codes.findFirstByPhoneAndCreatedAtAfterAndConsumedAtIsNullOrderByCreatedAtAsc(phone, dayAgo)
                    .map(oldest -> Duration.between(now, oldest.getCreatedAt().plus(Duration.ofDays(1))).toSeconds())
                    .orElse(Duration.ofDays(1).toSeconds());
            throw new TooManyRequestsException("Too many codes requested today", Math.max(1, wait));
        }
        if (codes.countByProviderInAndCreatedAtAfter(REAL_SMS, now.minus(Duration.ofHours(1))) >= GLOBAL_HOURLY_SMS) {
            log.warn("Global hourly SMS cap reached ({})", GLOBAL_HOURLY_SMS);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, TwilioVerifyClient.UNAVAILABLE);
        }
        if (codes.countByProviderInAndCreatedAtAfter(REAL_SMS, dayAgo) >= GLOBAL_DAILY_SMS) {
            log.warn("Global daily SMS cap reached ({})", GLOBAL_DAILY_SMS);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, TwilioVerifyClient.UNAVAILABLE);
        }
    }

    /** Verifică codul pentru număr; întoarce numărul în E.164. Cod greșit, expirat, folosit sau prea multe încercări → 401. */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public String verify(String rawPhone, String code) {
        String phone = PhoneNumbers.normalize(rawPhone);
        boolean wellFormed = code != null && SIX_DIGITS.matcher(code).matches();
        codes.lockPhone(phone);
        Instant now = clock.instant();
        if (PhoneNumbers.isDemo(phone)) return verifyDemo(phone, code, wellFormed, now);

        OtpCode c = codes.findFirstByPhoneOrderByCreatedAtDesc(phone)
                .filter(x -> usable(x, now))
                .orElseThrow(() -> new UnauthorizedException(INVALID_CODE));
        if (c.getProvider() == OtpCode.Provider.GATEWAY) return verifyGateway(phone, code, wellFormed, now);
        c.setAttempts(c.getAttempts() + 1);
        boolean ok = wellFormed && switch (c.getProvider()) {
            case TWILIO -> twilio.check(phone, code);
            case GATEWAY -> passwordEncoder.matches(code, c.getCodeHash());
            case LOCAL -> false;
        };
        if (!ok) {
            codes.save(c); // încercarea se numără și când codul e greșit
            throw new UnauthorizedException(INVALID_CODE);
        }
        c.setConsumedAt(now);
        codes.save(c);
        return phone;
    }

    /**
     * Gateway (SMS-urile de pe iPhone): la „Retrimite codul” omul poate primi SMS-urile în altă ordine și tastează codul din
     * primul. Merge orice cod încă bun al numărului; după login celelalte nu mai merg, iar o încercare greșită se numără la toate
     * codurile în așteptare (tot 5 încercări în total, ca înainte).
     */
    private String verifyGateway(String phone, String code, boolean wellFormed, Instant now) {
        List<OtpCode> pending = codes.findTop10ByPhoneAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(phone, now).stream()
                .filter(x -> x.getProvider() == OtpCode.Provider.GATEWAY && usable(x, now))
                .toList();
        OtpCode match = !wellFormed ? null
                : pending.stream().filter(x -> passwordEncoder.matches(code, x.getCodeHash())).findFirst().orElse(null);
        if (match == null) {
            pending.forEach(x -> { x.setAttempts(x.getAttempts() + 1); codes.save(x); });
            throw new UnauthorizedException(INVALID_CODE);
        }
        pending.forEach(x -> { x.setConsumedAt(now); codes.save(x); });
        return phone;
    }

    /**
     * Demo: orice cod încă valabil și nefolosit al numărului, oricâți oameni ar cere coduri deodată pe același cont demo
     * (căutare directă după hash, nu printre ultimele N coduri). Fără limită de încercări: codul e public oricum.
     */
    private String verifyDemo(String phone, String code, boolean wellFormed, Instant now) {
        OtpCode match = !wellFormed ? null : codes.findFirstByPhoneAndProviderAndCodeHashAndConsumedAtIsNullAndExpiresAtAfter(
                phone, OtpCode.Provider.LOCAL, demoHash(phone, code), now).orElse(null);
        if (match == null) throw new UnauthorizedException(INVALID_CODE);
        match.setConsumedAt(now);
        codes.save(match);
        return phone;
    }

    /**
     * Hash-ul codurilor de demo: SHA-256 rapid (codul e oricum afișat public în aplicație), legat de număr, ca verificarea să fie
     * o căutare directă în DB. Codurile reale rămân cu BCrypt (GATEWAY) sau la Twilio.
     */
    static String demoHash(String phone, String code) {
        try {
            byte[] d = java.security.MessageDigest.getInstance("SHA-256").digest((phone + ":" + code).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return "demo:" + java.util.HexFormat.of().formatHex(d);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String newCode() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    /** În log doar ultimele 3 cifre. */
    static String mask(String phone) {
        return phone.length() <= 3 ? "***" : "***" + phone.substring(phone.length() - 3);
    }
}
