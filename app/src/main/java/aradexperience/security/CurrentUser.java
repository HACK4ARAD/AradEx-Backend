package aradexperience.security;

import aradexperience.enums.Role;
/** Utilizatorul autentificat, pus în SecurityContext de JwtAuthFilter. Folosit cu @AuthenticationPrincipal. */
public record CurrentUser(Long id, String email, Role role) {

    public boolean is(Role r) {
        return role == r;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
