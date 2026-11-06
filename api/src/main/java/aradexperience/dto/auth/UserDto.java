package aradexperience.dto.auth;

import aradexperience.enums.Role;

/** docs/API.md: UserDto. `business` e null pentru rolurile fără firmă. */
public record UserDto(Long id, String email, String displayName, Role role, String locale, int pointsBalance, BusinessSummary business) {
}
