package aradexperience.dto.admin;

import jakarta.validation.constraints.Size;

/** docs/API.md §Admin (T14): `POST /city/businesses/{id}/reject` → `{ reason? }`. */
public record BusinessReject(@Size(max = 300) String reason) {
}
