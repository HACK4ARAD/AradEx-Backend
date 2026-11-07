package aradexperience.dto.auth;

/** 202 la POST /auth/otp/request: peste cât se poate cere alt cod; `demoCode` doar pentru numerele de demo (0799000xxx). */
public record OtpRequestResponse(long retryAfterSeconds, String demoCode) {
}
