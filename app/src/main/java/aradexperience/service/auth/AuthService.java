package aradexperience.service.auth;

import aradexperience.dto.auth.LoginRequest;
import aradexperience.dto.auth.OtpRegisterRequest;
import aradexperience.dto.auth.RegisterRequest;
import aradexperience.dto.auth.UpdateMeRequest;
import aradexperience.entity.business.Business;
import aradexperience.entity.users.User;
import aradexperience.enums.BusinessCategory;
import aradexperience.enums.Role;
import aradexperience.exceptions.BadRequestException;
import aradexperience.exceptions.ConflictException;
import aradexperience.exceptions.ForbiddenException;
import aradexperience.exceptions.UnauthorizedException;
import aradexperience.repository.business.BusinessRepository;
import aradexperience.repository.users.UserRepository;
import aradexperience.security.CurrentUser;
import aradexperience.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

import static aradexperience.mapper.users.UserMapper.toBusiness;

/**
 * Conturi: profilul curent; firmele, Primăria și Adminul intră cu email + parolă (înregistrare liberă doar BUSINESS);
 * conturile personale (VISITOR, LOCAL) intră și se creează doar cu telefon + cod OTP (T21, {@link OtpService}).
 * Emite JWT prin {@link JwtService}. Mesajele de eroare sunt generice la login (nu spunem dacă emailul există).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    static final String INVALID_CREDENTIALS = "Invalid email or password";
    static final String EMAIL_TAKEN = "Email already registered";
    static final String PHONE_TAKEN = "Phone number already registered";
    static final String USE_PHONE = "Personal accounts sign in with their phone number";
    /**
     * Un hash BCrypt valid (cost 10, același ca în seed) folosit doar ca `matches` să dureze la fel când emailul nu există;
     * rezultatul e ignorat în acel caz (nu există utilizator), deci nu contează ce parolă îi corespunde.
     */
    static final String DUMMY_HASH = "$2a$10$FurXRhcQxrujUFWGKsDPaeK66hpMPWwvIMwIg2xwAFaldOgioPSXO";

    private final UserRepository users;
    private final BusinessRepository businesses;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final OtpService otp;

    /** Rezultatul login/register: utilizatorul, firma lui (sau null) și tokenul. Controllerul îl mapează în AuthResponse. */
    public record Session(String token, User user, Business business) {}

    /** Profilul curent: utilizatorul + firma lui (sau null). */
    public record Profile(User user, Business business) {}

    /** T21, după cod: cont existent → `session`; număr nou → `signupToken` (numele și rolul vin la {@link #registerWithPhone}). */
    public record PhoneLogin(Session session, String signupToken) {}

    @Transactional
    public Session register(RegisterRequest req) {
        if (!req.role().selfRegistrable()) {
            throw new BadRequestException("Role " + req.role() + " cannot self-register");
        }
        if (req.role() != Role.BUSINESS) throw new BadRequestException(USE_PHONE);
        String email = normalize(req.email());
        if (users.existsByEmail(email)) {
            throw new ConflictException(EMAIL_TAKEN);
        }
        BusinessCategory category = null;
        if (req.role() == Role.BUSINESS) {
            if (req.business() == null) throw new BadRequestException("Business details are required for role BUSINESS");
            category = parseCategory(req.business().category());
        } else if (req.business() != null) {
            throw new BadRequestException("Business details are only accepted for role BUSINESS");
        }

        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(req.password()));
        u.setDisplayName(req.displayName().trim());
        u.setRole(req.role());
        u.setLocale("ro");
        try {
            u = users.saveAndFlush(u);
        } catch (DataIntegrityViolationException e) {
            // două înregistrări simultane cu același email: a doua lovește UNIQUE(users.email) → tot 409, nu 500
            throw new ConflictException(EMAIL_TAKEN);
        }

        Business b = null;
        if (req.role() == Role.BUSINESS) {
            b = businesses.save(toBusiness(req.business(), category, u.getId()));
        }
        log.info("User {} registered with role {}", u.getId(), u.getRole());
        return new Session(issue(u), u, b);
    }

    public Session login(LoginRequest req) {
        Optional<User> found = users.findByEmail(normalize(req.email()));
        // BCrypt rulează și pentru emailuri inexistente, ca timpul de răspuns să nu arate ce conturi există
        boolean ok = passwordEncoder.matches(req.password(), found.map(User::getPasswordHash).orElse(DUMMY_HASH));
        User u = found.filter(x -> ok && x.getPasswordHash() != null).orElseThrow(() -> new UnauthorizedException(INVALID_CREDENTIALS));
        // după parola corectă, ca răspunsul să nu spună ce conturi există
        if (isPersonal(u.getRole())) throw new ForbiddenException(USE_PHONE);
        return new Session(issue(u), u, businessOf(u));
    }

    /** T21: trimite codul OTP (numerele de demo primesc codul în răspuns). Scrie în otp_codes, deci nu e read-only. */
    @Transactional
    public OtpService.RequestResult requestOtp(String phone) {
        return otp.request(phone);
    }

    /**
     * T21: cod corect → sesiunea contului cu acest număr, sau un signupToken dacă numărul e nou. Un cod greșit (401) nu
     * anulează tranzacția: încercarea numărată de OtpService trebuie să rămână salvată (limita de 5 încercări).
     */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public PhoneLogin verifyOtp(String phone, String code) {
        String e164 = otp.verify(phone, code);
        return users.findByPhone(e164)
                .map(u -> new PhoneLogin(new Session(issue(u), u, businessOf(u)), null))
                .orElseGet(() -> new PhoneLogin(null, jwtService.issueSignup(e164)));
    }

    /** T21: contul personal nou (VISITOR | LOCAL), fără email și fără parolă. */
    @Transactional
    public Session registerWithPhone(OtpRegisterRequest req) {
        if (!isPersonal(req.role())) throw new BadRequestException("Only VISITOR or LOCAL accounts sign up with a phone number");
        String phone = jwtService.parseSignup(req.signupToken())
                .orElseThrow(() -> new UnauthorizedException("Signup expired, request a new code"));
        if (users.existsByPhone(phone)) throw new ConflictException(PHONE_TAKEN);

        User u = new User();
        u.setPhone(phone);
        u.setDisplayName(req.displayName().trim());
        u.setRole(req.role());
        u.setLocale("ro");
        try {
            u = users.saveAndFlush(u);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(PHONE_TAKEN); // două înregistrări simultane cu același număr
        }
        log.info("User {} registered with phone, role {}", u.getId(), u.getRole());
        return new Session(issue(u), u, null);
    }

    private static boolean isPersonal(Role role) {
        return role == Role.VISITOR || role == Role.LOCAL;
    }

    public Profile me(CurrentUser current) {
        User u = load(current);
        return new Profile(u, businessOf(u));
    }

    @Transactional
    public Profile updateMe(CurrentUser current, UpdateMeRequest req) {
        User u = load(current);
        if (req.displayName() != null) {
            String name = req.displayName().trim();
            if (name.isEmpty()) throw new BadRequestException("Display name cannot be blank");
            u.setDisplayName(name);
        }
        if (req.locale() != null) u.setLocale(req.locale());
        return new Profile(u, businessOf(u));
    }

    private User load(CurrentUser current) {
        // tokenul e valid, dar contul poate să nu mai existe (ex. baza a fost recreată) → 401, FE face logout
        return users.findById(current.id()).orElseThrow(() -> new UnauthorizedException("Account no longer exists"));
    }

    private Business businessOf(User u) {
        return u.getRole() == Role.BUSINESS ? businesses.findByOwnerUserId(u.getId()).orElse(null) : null;
    }

    private String issue(User u) {
        return jwtService.issue(u.getId(), u.getEmail(), u.getRole());
    }

    /** Emailurile se stochează normalizate (trim + lowercase), deci căutarea exactă folosește indexul UNIQUE(users.email). */
    static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static BusinessCategory parseCategory(String raw) {
        return Optional.ofNullable(raw)
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .flatMap(s -> {
                    try { return Optional.of(BusinessCategory.valueOf(s)); } catch (IllegalArgumentException e) { return Optional.empty(); }
                })
                .orElseThrow(() -> new BadRequestException("Unknown business category: " + raw));
    }
}
