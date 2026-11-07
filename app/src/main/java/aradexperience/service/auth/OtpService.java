package aradexperience.service.auth;

import aradexperience.entity.auth.OtpCode;
import aradexperience.exceptions.ApiException;
import aradexperience.exceptions.BadRequestException;
import aradexperience.exceptions.TooManyRequestsException;
import aradexperience.exceptions.UnauthorizedException;
import aradexperience.repository.auth.OtpCodeRepository;
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
import java.util.regex.Pattern;

/**
 * Codurile OTP pentru conturile personale (T21).
 * - Numerele de demo (`+40799000xxx`): codul îl generăm noi, îl ținem doar ca hash și îl întoarcem în răspuns (fără SMS).
 *   Fără limite (codul e public oricum), ca nimeni să nu poată bloca conturile demo; orice cod demo încă valabil merge,
 *   ca mai mulți oameni să poată intra simultan pe același cont.
 * - Celelalte numere: SMS prin Twilio Verify (doar numere românești, +40), care generează și verifică el codul.
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
    static final long GLOBAL_DAILY_SMS = 300;
    static final String SMS_COUNTRY_PREFIX = "+40";
    static final String INVALID_CODE = "Invalid or expired code";
    private static final Pattern SIX_DIGITS = Pattern.compile("^\\d{6}$");

    private final OtpCodeRepository codes;
    private final PasswordEncoder passwordEncoder;
    private final TwilioVerifyClient twilio;
    private final SecureRandom random = new SecureRandom();
    private Clock clock = Clock.systemUTC();

    /** Pentru teste. */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    /** `retryAfterSeconds` = peste cât se poate cere alt cod; `demoCode` doar pentru numerele de demo. */
    public record RequestResult(long retryAfterSeconds, String demoCode) {}

    @Transactional
    public RequestResult request(String rawPhone) {
        String phone = PhoneNumbers.normalize(rawPhone);
        boolean demo = PhoneNumbers.isDemo(phone);
        if (!demo && !phone.startsWith(SMS_COUNTRY_PREFIX)) throw new BadRequestException("SMS codes are only sent to Romanian numbers");
        codes.lockPhone(phone);
        Instant now = clock.instant();
        if (!demo) checkLimits(phone, now);

        OtpCode c = new OtpCode();
        c.setPhone(phone);
        c.setCreatedAt(now);
        c.setExpiresAt(now.plus(TTL));
        String demoCode = null;
        if (demo) {
            demoCode = String.format("%06d", random.nextInt(1_000_000));
            c.setProvider(OtpCode.Provider.LOCAL);
            c.setCodeHash(passwordEncoder.encode(demoCode));
        } else {
            twilio.start(phone); // dacă Twilio refuză sau lipsește, nu salvăm nimic (nu consumă limita)
            c.setProvider(OtpCode.Provider.TWILIO);
        }
        codes.save(c);
        log.info("OTP requested for {} via {}", mask(phone), c.getProvider());
        return new RequestResult(COOLDOWN.toSeconds(), demoCode);
    }

    /** Limitele numerelor reale: 60 s între cereri, 5 pe zi pe număr, limita globală de SMS-uri pe zi. */
    private void checkLimits(String phone, Instant now) {
        codes.findFirstByPhoneOrderByCreatedAtDesc(phone).ifPresent(last -> {
            long wait = COOLDOWN.minus(Duration.between(last.getCreatedAt(), now)).toSeconds();
            if (wait > 0) throw new TooManyRequestsException("Wait before requesting a new code", wait);
        });
        Instant dayAgo = now.minus(Duration.ofDays(1));
        if (codes.countByPhoneAndCreatedAtAfter(phone, dayAgo) >= DAILY_LIMIT) {
            long wait = codes.findFirstByPhoneAndCreatedAtAfterOrderByCreatedAtAsc(phone, dayAgo)
                    .map(oldest -> Duration.between(now, oldest.getCreatedAt().plus(Duration.ofDays(1))).toSeconds())
                    .orElse(Duration.ofDays(1).toSeconds());
            throw new TooManyRequestsException("Too many codes requested today", Math.max(1, wait));
        }
        if (codes.countByProviderAndCreatedAtAfter(OtpCode.Provider.TWILIO, dayAgo) >= GLOBAL_DAILY_SMS) {
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
                .filter(x -> x.getConsumedAt() == null && x.getExpiresAt().isAfter(now) && x.getAttempts() < MAX_ATTEMPTS)
                .orElseThrow(() -> new UnauthorizedException(INVALID_CODE));
        c.setAttempts(c.getAttempts() + 1);
        boolean ok = wellFormed && c.getProvider() == OtpCode.Provider.TWILIO && twilio.check(phone, code);
        if (!ok) {
            codes.save(c); // încercarea se numără și când codul e greșit
            throw new UnauthorizedException(INVALID_CODE);
        }
        c.setConsumedAt(now);
        codes.save(c);
        return phone;
    }

    /** Demo: orice cod încă valabil și nefolosit al numărului (fără limită de încercări: codul e public oricum). */
    private String verifyDemo(String phone, String code, boolean wellFormed, Instant now) {
        OtpCode match = !wellFormed ? null : codes.findTop10ByPhoneAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(phone, now).stream()
                .filter(c -> c.getProvider() == OtpCode.Provider.LOCAL && passwordEncoder.matches(code, c.getCodeHash()))
                .findFirst().orElse(null);
        if (match == null) throw new UnauthorizedException(INVALID_CODE);
        match.setConsumedAt(now);
        codes.save(match);
        return phone;
    }

    /** În log doar ultimele 3 cifre. */
    static String mask(String phone) {
        return phone.length() <= 3 ? "***" : "***" + phone.substring(phone.length() - 3);
    }
}
