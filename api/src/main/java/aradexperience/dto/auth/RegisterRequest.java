package aradexperience.dto.auth;

import aradexperience.enums.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** POST /auth/register. `business` e obligatoriu doar când role = BUSINESS (verificat în service). */
public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 80) String displayName,
        @NotNull Role role,
        @Valid BusinessCreate business) {

    public record BusinessCreate(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 40) String category,
            @NotBlank @Size(max = 200) String address,
            Double lat,
            Double lng) {
    }
}
