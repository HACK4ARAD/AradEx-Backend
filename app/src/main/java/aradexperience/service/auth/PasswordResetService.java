package aradexperience.service.auth;

import aradexperience.entity.auth.PasswordResetCode;
import aradexperience.entity.users.User;
import aradexperience.enums.Role;
import aradexperience.exceptions.UnauthorizedException;
import aradexperience.repository.auth.PasswordResetCodeRepository;
import aradexperience.repository.users.UserRepository;
import aradexperience.service.mail.MailOutbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * „Am uitat parola”: un cod de 6 cifre pe email (15 minute, 5 încercări greșite), apoi parola nouă.
 * - Răspunsul la cerere e același cu sau fără cont: limitele se aplică oricărei adrese, codul se generează și se hash-uiește
 *   și când nu există cont (aceeași durată), iar emailul pleacă pe alt fir, după commit.
 * - Nu primesc cod: conturile demo din seed (`@demo.ro` — domeniu real al altcuiva, iar parola lor e publică pentru demo),
 *   voluntarii (n-au email) și conturile dezactivate.
 * - La verificare merge orice cod încă deschis al contului (emailurile pot ajunge în altă ordine); o încercare greșită se
 *   numără la toate, iar după reușită toate se închid.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    static final Duration TTL = Duration.ofMinutes(15);
    static final int MAX_ATTEMPTS = 5;
    static final String INVALID_CODE = "Invalid or expired code";
    static final String DEMO_DOMAIN = "@demo.ro";

    private final UserRepository users;
    private final PasswordResetCodeRepository codes;
    private final PasswordEncoder passwordEncoder;
    private final MailOutbox mail;
    private final PasswordResetLimiter limiter;
    private final SecureRandom random = new SecureRandom();
    private Clock clock = Clock.systemUTC();

    /** Pentru teste. */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    /** `email` deja normalizat; `clientKey` = IP-ul clientului (null = fără limita pe IP). */
    @Transactional
    public void request(String email, String clientKey) {
        Instant now = clock.instant();
        limiter.acquire(email, clientKey, now);
        String code = String.format("%06d", random.nextInt(1_000_000));
        String hash = passwordEncoder.encode(code); // și fără cont, ca durata să nu arate dacă adresa există
        Optional<User> found = users.findByEmail(email).filter(PasswordResetService::canReset);
        if (found.isEmpty()) {
            log.info("Password reset requested for an address without a resettable account");
            return;
        }
        User u = found.get();
        PasswordResetCode c = new PasswordResetCode();
        c.setUserId(u.getId());
        c.setCodeHash(hash);
        c.setCreatedAt(now);
        c.setExpiresAt(now.plus(TTL));
        codes.save(c);
        mail.sendAfterCommit(email, PasswordResetMail.subject(u.getLocale()), PasswordResetMail.body(u.getLocale(), u.getDisplayName(), code));
        log.info("Password reset code issued for user {}", u.getId());
    }

    /**
     * Cod corect → parola nouă (hash BCrypt) și contul, pentru sesiune. Cod greșit / expirat / prea multe încercări → 401,
     * fără să spunem dacă adresa are cont; încercarea rămâne numărată (tranzacția nu se anulează la 401).
     */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public User reset(String email, String code, String newPassword) {
        Instant now = clock.instant();
        User u = users.findByEmail(email).filter(PasswordResetService::canReset).orElse(null);
        List<PasswordResetCode> pending = u == null ? List.of()
                : codes.findByUserIdAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(u.getId(), now).stream()
                        .filter(c -> c.getAttempts() < MAX_ATTEMPTS)
                        .toList();
        if (pending.isEmpty()) {
            passwordEncoder.matches(code, AuthService.DUMMY_HASH); // aceeași durată ca o verificare reală
            throw new UnauthorizedException(INVALID_CODE);
        }
        PasswordResetCode match = pending.stream().filter(c -> passwordEncoder.matches(code, c.getCodeHash())).findFirst().orElse(null);
        if (match == null) {
            pending.forEach(c -> { c.setAttempts(c.getAttempts() + 1); codes.save(c); });
            throw new UnauthorizedException(INVALID_CODE);
        }
        pending.forEach(c -> { c.setConsumedAt(now); codes.save(c); });
        u.setPasswordHash(passwordEncoder.encode(newPassword));
        users.save(u);
        log.info("Password reset for user {}", u.getId());
        return u;
    }

    static boolean canReset(User u) {
        return u.getRole() != Role.VOLUNTEER && !u.isDisabled() && u.getEmail() != null && !u.getEmail().endsWith(DEMO_DOMAIN);
    }
}
