package aradexperience.dto.auth;

/**
 * POST /auth/otp/verify (T21): numărul are cont → `token` + `user`; număr nou → doar `signupToken` (15 min), cu care
 * aplicația cere numele și rolul la POST /auth/otp/register.
 */
public record OtpVerifyResponse(String token, UserDto user, String signupToken) {
}
