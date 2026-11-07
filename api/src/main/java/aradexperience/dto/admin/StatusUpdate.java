package aradexperience.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** docs/API.md §Admin (T14): `PATCH /admin/{events|offers|actions}/{id}/status` → `{ status }`; valorile permise se verifică pe tip în service. */
public record StatusUpdate(@NotBlank @Size(max = 20) String status) {
}
