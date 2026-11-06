package aradexperience.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Utilizatorul curent, pentru controllere (interfețele din `api` nu știu de Spring Security).
 * `require()` pe rute protejate (SecurityConfig garantează că există), `current()` pe rute publice cu conținut personalizat.
 */
public final class AuthContext {

    private AuthContext() {}

    public static Optional<CurrentUser> current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CurrentUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public static CurrentUser require() {
        return current().orElseThrow(() -> new IllegalStateException("No authenticated user; route should be protected in SecurityConfig"));
    }
}
