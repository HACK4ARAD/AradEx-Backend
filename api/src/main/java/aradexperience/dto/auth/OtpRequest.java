package aradexperience.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /auth/otp/request (T21). `phone` în orice formă uzuală (`0799 000 001`, `+40…`); serverul îl normalizează. */
public record OtpRequest(@NotBlank @Size(max = 32) String phone) {
}
