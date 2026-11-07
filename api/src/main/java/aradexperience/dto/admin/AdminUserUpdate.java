package aradexperience.dto.admin;

import aradexperience.enums.Role;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * docs/API.md §Admin (T14): `PATCH /admin/users/{id}` — toate câmpurile opționale. `pointsAdjust` (≠ 0) trece prin PointsService
 * ca tranzacție ADJUST cu `note`; `disabled` = true dezactivează contul (nu mai poate intra), false îl reactivează.
 */
public record AdminUserUpdate(Role role, @Min(-100000) @Max(100000) Integer pointsAdjust, @Size(max = 200) String note, Boolean disabled) {
}
