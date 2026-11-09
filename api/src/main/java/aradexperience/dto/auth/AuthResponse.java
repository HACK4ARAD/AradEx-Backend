package aradexperience.dto.auth;

public record AuthResponse(String token, UserDto user, String recoveryCode) {

    public AuthResponse(String token, UserDto user) {
        this(token, user, null);
    }
}
