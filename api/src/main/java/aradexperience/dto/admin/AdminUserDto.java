package aradexperience.dto.admin;

import aradexperience.dto.auth.BusinessSummary;
import aradexperience.enums.Role;

import java.time.OffsetDateTime;

/** docs/API.md §Admin (T14): rândul din `/admin/users` = UserDto + { disabled, createdAt, ticketsCount }. Fără parole sau date de plată. */
public record AdminUserDto(Long id, String email, String phone, String displayName, Role role, String locale, int pointsBalance,
                           BusinessSummary business, boolean disabled, OffsetDateTime createdAt, long ticketsCount) {
}
