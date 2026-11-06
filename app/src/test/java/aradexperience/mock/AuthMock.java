package aradexperience.mock;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import aradexperience.security.CurrentUser;
import aradexperience.enums.Role;

import java.util.List;

/**
 * Autentificare pentru testele de controller: `mvc.perform(post("/api/v1/...").with(AuthMock.as(1L, Role.LOCAL)))`.
 * Pune în SecurityContext exact ce pune JwtAuthFilter (un CurrentUser + ROLE_x), deci @AuthenticationPrincipal funcționează.
 */
public final class AuthMock {

    private AuthMock() {}

    public static RequestPostProcessor as(long userId, Role role) {
        var user = new CurrentUser(userId, role.name().toLowerCase() + "@demo.ro", role);
        var auth = new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        return SecurityMockMvcRequestPostProcessors.authentication(auth);
    }
}
