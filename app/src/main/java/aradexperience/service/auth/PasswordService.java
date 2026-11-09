package aradexperience.service.auth;

import aradexperience.entity.users.User;
import aradexperience.enums.Role;
import aradexperience.exceptions.BadRequestException;
import aradexperience.exceptions.ForbiddenException;
import aradexperience.exceptions.NotFoundException;
import aradexperience.exceptions.UnauthorizedException;
import aradexperience.repository.users.UserRepository;
import aradexperience.security.CurrentUser;
import aradexperience.util.RecoveryCodes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;

/**
 * Parolele fără email și fără SMS:
 * - codul de recuperare (12 caractere, doar hash BCrypt) se dă la crearea contului și se poate regenera din cont cu parola;
 *   „Am uitat parola” = email + cod + parola nouă, iar codul folosit se înlocuiește cu unul nou;
 * - dacă omul pierde și codul, Adminul îi pune o parolă temporară (`mustChangePassword`), pe care i-o dă personal; la
 *   intrare aplicația cere imediat o parolă nouă și dă un cod de recuperare nou.
 * Conturile demo (`@demo.ro`) au parola publică, folosită de toți: nu li se poate schimba parola și n-au cod de recuperare
 * (altfel oricine i-ar putea bloca pe ceilalți). Voluntarii intră cu codul de la Primărie, nu cu parolă.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordService {

    static final String DEMO_DOMAIN = "@demo.ro";
    static final String INVALID_RECOVERY = "Invalid email or recovery code";
    static final String DEMO_LOCKED = "Demo accounts cannot change their password";
    static final String WRONG_PASSWORD = "Current password is incorrect";
    static final String SAME_PASSWORD = "The new password must be different";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final RecoveryLimiter limiter;
    private final SecureRandom random = new SecureRandom();
    private Clock clock = Clock.systemUTC();

    /** Pentru teste. */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    public static boolean isDemo(User u) {
        return u.getEmail() != null && u.getEmail().endsWith(DEMO_DOMAIN);
    }

    /** Cod de recuperare nou pentru cont (vechiul nu mai merge). Întoarce codul în clar, o singură dată; salvează apelantul. */
    public String issueRecoveryCode(User u) {
        String code = RecoveryCodes.generate(random);
        u.setRecoveryCodeHash(passwordEncoder.encode(RecoveryCodes.normalize(code)));
        return code;
    }

    /**
     * „Am uitat parola”: email (normalizat) + cod corect → parola nouă. Orice greșeală (adresă fără cont, cont fără cod, cont
     * demo, cod greșit) dă același 401 după aceeași verificare BCrypt; după 5 greșeli pe oră → 429. Contul dezactivat → 403.
     */
    @Transactional
    public User recover(String email, String rawCode, String newPassword, String clientKey) {
        Instant now = clock.instant();
        limiter.check(email, clientKey, now);
        String code = RecoveryCodes.normalize(rawCode);
        User u = users.findByEmail(email).filter(x -> x.getRole() != Role.VOLUNTEER && !isDemo(x) && x.getRecoveryCodeHash() != null).orElse(null);
        boolean ok = passwordEncoder.matches(code == null ? "" : code, u == null ? AuthService.DUMMY_HASH : u.getRecoveryCodeHash())
                && u != null && code != null;
        if (!ok) {
            limiter.failed(email, clientKey, now);
            throw new UnauthorizedException(INVALID_RECOVERY);
        }
        if (u.isDisabled()) throw new ForbiddenException(AuthService.ACCOUNT_DISABLED);
        limiter.succeeded(email);
        u.setPasswordHash(passwordEncoder.encode(newPassword));
        u.setMustChangePassword(false);
        users.save(u);
        log.info("Password recovered with the recovery code for user {}", u.getId());
        return u;
    }

    /**
     * Parola nouă din cont (sau după parola temporară). Întoarce un cod de recuperare nou dacă contul avea parolă temporară
     * sau n-avea cod, altfel null. Parola actuală greșită → 400 (nu 401: aplicația ar deconecta omul).
     */
    @Transactional
    public String changePassword(User u, String currentPassword, String newPassword) {
        requirePassword(u, currentPassword);
        if (passwordEncoder.matches(newPassword, u.getPasswordHash())) throw new BadRequestException(SAME_PASSWORD);
        boolean newCode = u.isMustChangePassword() || u.getRecoveryCodeHash() == null;
        u.setPasswordHash(passwordEncoder.encode(newPassword));
        u.setMustChangePassword(false);
        String code = newCode ? issueRecoveryCode(u) : null;
        users.save(u);
        log.info("User {} changed the password{}", u.getId(), newCode ? " and got a new recovery code" : "");
        return code;
    }

    /** Cod de recuperare nou din cont, cu parola (un token furat singur nu ajunge). */
    @Transactional
    public String regenerateRecoveryCode(User u, String password) {
        requirePassword(u, password);
        String code = issueRecoveryCode(u);
        users.save(u);
        log.info("User {} generated a new recovery code", u.getId());
        return code;
    }

    /**
     * Admin: parolă temporară pentru un cont care și-a pierdut și codul; Adminul o dă personal omului. Codul vechi de
     * recuperare nu mai merge (poate fi pe mâna altcuiva). Nu pentru alți Admini, voluntari, conturi fără email sau demo.
     */
    @Transactional
    public String temporaryPassword(long userId, CurrentUser by) {
        User u = users.findById(userId).orElseThrow(() -> NotFoundException.of("User", userId));
        if (u.getRole() == Role.ADMIN) throw new ForbiddenException("Admin accounts change their own password");
        if (u.getRole() == Role.VOLUNTEER) throw new BadRequestException("Volunteers sign in with the code from City Hall");
        if (u.getEmail() == null) throw new BadRequestException("This account has no email address");
        if (isDemo(u)) throw new BadRequestException("Demo accounts keep the demo password");
        String temporary = RecoveryCodes.generate(random);
        u.setPasswordHash(passwordEncoder.encode(temporary));
        u.setMustChangePassword(true);
        u.setRecoveryCodeHash(null);
        users.save(u);
        log.info("Admin {} set a temporary password for user {}", by.id(), u.getId());
        return temporary;
    }

    private void requirePassword(User u, String password) {
        if (isDemo(u)) throw new ForbiddenException(DEMO_LOCKED);
        if (u.getPasswordHash() == null || !passwordEncoder.matches(password, u.getPasswordHash())) {
            throw new BadRequestException(WRONG_PASSWORD);
        }
    }
}
