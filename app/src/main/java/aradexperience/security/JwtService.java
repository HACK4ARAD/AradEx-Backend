package aradexperience.security;

import aradexperience.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import aradexperience.config.AppProps;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Emite și validează token-uri JWT (HS256). Claims: sub = userId, email (lipsește la conturile cu telefon), role.
 * T21: tokenul de înregistrare (`purpose=signup`, `phone`, 15 min) dovedește doar că numărul a fost verificat; nu e
 * acceptat ca autentificare de {@link #parse}.
 */
@Service
public class JwtService {

    static final String PURPOSE = "purpose";
    static final String SIGNUP = "signup";
    static final Duration SIGNUP_TTL = Duration.ofMinutes(15);

    private final SecretKey key;
    private final Duration ttl;

    @Autowired
    public JwtService(AppProps props) {
        this(props.jwt().secret(), Duration.ofHours(props.jwt().expirationHours()));
    }

    JwtService(String secret, Duration ttl) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = ttl;
    }

    public String issue(Long userId, String email, Role role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /** T21: tokenul de înregistrare după un cod OTP corect, pentru un număr fără cont. */
    public String issueSignup(String phone) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(SIGNUP)
                .claim(PURPOSE, SIGNUP)
                .claim("phone", phone)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(SIGNUP_TTL)))
                .signWith(key)
                .compact();
    }

    /** Numărul verificat din tokenul de înregistrare, sau Optional.empty() dacă tokenul e invalid, expirat sau de alt tip. */
    public Optional<String> parseSignup(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return SIGNUP.equals(c.get(PURPOSE, String.class)) ? Optional.ofNullable(c.get("phone", String.class)) : Optional.empty();
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Întoarce utilizatorul din token sau Optional.empty() dacă tokenul e invalid/expirat sau nu e de autentificare. */
    public Optional<CurrentUser> parse(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            if (c.get(PURPOSE) != null || c.get("role") == null) return Optional.empty();
            return Optional.of(new CurrentUser(
                    Long.valueOf(c.getSubject()),
                    c.get("email", String.class),
                    Role.valueOf(c.get("role", String.class))));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
