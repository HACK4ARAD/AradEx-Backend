package aradexperience.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /auth/otp/verify (T21). */
public record OtpVerifyRequest(@NotBlank @Size(max = 32) String phone, @NotBlank @Size(max = 10) String code) {
}
