package aradexperience.dto.auth;

import aradexperience.enums.Role;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * POST /auth/otp/register (T21): contul personal nou, după codul OTP. `role` doar VISITOR | LOCAL (verificat în service).
 * `acceptTerms` = bifa „Accept Termenii și condițiile și Politica de confidențialitate” (GDPR): obligatoriu `true`.
 */
public record OtpRegisterRequest(@NotBlank String signupToken, @NotBlank @Size(max = 80) String displayName, @NotNull Role role,
                                 @NotNull @AssertTrue Boolean acceptTerms) {
}
