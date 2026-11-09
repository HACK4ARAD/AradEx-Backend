package aradexperience.controller.auth;

import aradexperience.api.AuthApi;
import aradexperience.dto.auth.AuthResponse;
import aradexperience.dto.auth.ForgotPasswordRequest;
import aradexperience.dto.auth.LoginRequest;
import aradexperience.dto.auth.RegisterRequest;
import aradexperience.dto.auth.ResetPasswordRequest;
import aradexperience.dto.auth.UpdateMeRequest;
import aradexperience.dto.auth.UserDto;
import aradexperience.security.AuthContext;
import aradexperience.service.auth.AuthService;
import aradexperience.util.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import static aradexperience.mapper.users.UserMapper.toDto;

@RestController
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private final AuthService service;
    private final HttpServletRequest http;

    @Override
    public AuthResponse register(RegisterRequest request) {
        return toResponse(service.register(request));
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        return toResponse(service.login(request));
    }

    @Override
    public void forgotPassword(ForgotPasswordRequest request) {
        service.forgotPassword(request.email(), ClientIp.of(http));
    }

    @Override
    public AuthResponse resetPassword(ResetPasswordRequest request) {
        return toResponse(service.resetPassword(request.email(), request.code(), request.password()));
    }

    @Override
    public UserDto me() {
        AuthService.Profile p = service.me(AuthContext.require());
        return toDto(p.user(), p.business());
    }

    @Override
    public UserDto updateMe(UpdateMeRequest request) {
        AuthService.Profile p = service.updateMe(AuthContext.require(), request);
        return toDto(p.user(), p.business());
    }

    private static AuthResponse toResponse(AuthService.Session s) {
        return new AuthResponse(s.token(), toDto(s.user(), s.business()));
    }
}
