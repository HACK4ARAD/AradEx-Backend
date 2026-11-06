package aradexperience.dto.auth;

public record AuthResponse(String token, UserDto user) {
}
