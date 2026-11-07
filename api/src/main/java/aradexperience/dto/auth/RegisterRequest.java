package aradexperience.dto.auth;

import aradexperience.enums.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * POST /auth/register. `business` e obligatoriu doar când role = BUSINESS și refuzat pentru celelalte roluri (verificat în service).
 * Limitele de lungime = coloanele din V2 (users.email 190, businesses.name 160 / address 250).
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 190) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 80) String displayName,
        @NotNull Role role,
        @Valid BusinessCreate business) {

    public record BusinessCreate(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 40) String category,
            @NotBlank @Size(max = 200) String address,
            @DecimalMin("-90") @DecimalMax("90") Double lat,
            @DecimalMin("-180") @DecimalMax("180") Double lng) {
    }
}
