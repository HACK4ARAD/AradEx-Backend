package aradexperience.service.auth;

import aradexperience.entity.auth.OtpCode;
import aradexperience.exceptions.TooManyRequestsException;
import aradexperience.exceptions.UnauthorizedException;
import aradexperience.repository.auth.OtpCodeRepository;
import aradexperience.util.PhoneNumbers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * - Celelalte numere: SMS prin Twilio Verify, care generează și verifică el codul.
 * Pentru ambele: o cerere la 60 s și maximum 5 pe zi pe număr, cod valabil 5 minute, maximum 5 încercări pe cod.
 * Furnizorul se alege după număr, nu după ce trimite clientul (un cod de demo nu merge pe un număr real).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    static final Duration TTL = Duration.ofMinutes(5);
    static final Duration COOLDOWN = Duration.ofSeconds(60);
    static final int DAILY_LIMIT = 5;
    static final int MAX_ATTEMPTS = 5;
    static final long DAILY_RETRY_SECONDS = 3600;
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
        Instant now = clock.instant();
        codes.findFirstByPhoneOrderByCreatedAtDesc(phone).ifPresent(last -> {
            long wait = COOLDOWN.minus(Duration.between(last.getCreatedAt(), now)).toSeconds();
            if (wait > 0) throw new TooManyRequestsException("Wait before requesting a new code", wait);
        });
        if (codes.countByPhoneAndCreatedAtAfter(phone, now.minus(Duration.ofDays(1))) >= DAILY_LIMIT) {
            throw new TooManyRequestsException("Too many codes requested today", DAILY_RETRY_SECONDS);
        }

        OtpCode c = new OtpCode();
        c.setPhone(phone);
        c.setCreatedAt(now);
        c.setExpiresAt(now.plus(TTL));
        String demoCode = null;
        if (PhoneNumbers.isDemo(phone)) {
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

    /** Verifică codul pentru număr; întoarce numărul în E.164. Cod greșit, expirat, folosit sau prea multe încercări → 401. */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public String verify(String rawPhone, String code) {
        String phone = PhoneNumbers.normalize(rawPhone);
        Instant now = clock.instant();
        OtpCode c = codes.findFirstByPhoneOrderByCreatedAtDesc(phone)
                .filter(x -> x.getConsumedAt() == null && x.getExpiresAt().isAfter(now) && x.getAttempts() < MAX_ATTEMPTS)
                .orElseThrow(() -> new UnauthorizedException(INVALID_CODE));
        c.setAttempts(c.getAttempts() + 1);
        boolean ok = code != null && SIX_DIGITS.matcher(code).matches() && matches(c, phone, code);
        if (!ok) {
            codes.save(c); // încercarea se numără și când codul e greșit
            throw new UnauthorizedException(INVALID_CODE);
        }
        c.setConsumedAt(now);
        codes.save(c);
        return phone;
    }

    private boolean matches(OtpCode c, String phone, String code) {
        if (PhoneNumbers.isDemo(phone)) {
            return c.getProvider() == OtpCode.Provider.LOCAL && passwordEncoder.matches(code, c.getCodeHash());
        }
        return c.getProvider() == OtpCode.Provider.TWILIO && twilio.check(phone, code);
    }

    /** În log doar ultimele 3 cifre. */
    static String mask(String phone) {
        return phone.length() <= 3 ? "***" : "***" + phone.substring(phone.length() - 3);
    }
}
