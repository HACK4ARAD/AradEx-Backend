package aradexperience.controller.auth;

import aradexperience.api.AuthApi;
import aradexperience.dto.auth.AuthResponse;
import aradexperience.dto.auth.LoginRequest;
import aradexperience.dto.auth.OtpRegisterRequest;
import aradexperience.dto.auth.OtpRequest;
import aradexperience.dto.auth.OtpRequestResponse;
import aradexperience.dto.auth.OtpVerifyRequest;
import aradexperience.dto.auth.OtpVerifyResponse;
import aradexperience.service.auth.OtpService;
import aradexperience.dto.auth.RegisterRequest;
import aradexperience.dto.auth.UpdateMeRequest;
import aradexperience.dto.auth.UserDto;
import aradexperience.security.AuthContext;
import aradexperience.service.auth.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import static aradexperience.mapper.users.UserMapper.toDto;

@RestController
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private final AuthService service;

    @Override
    public AuthResponse register(RegisterRequest request) {
        return toResponse(service.register(request));
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        return toResponse(service.login(request));
    }

    @Override
    public OtpRequestResponse requestOtp(OtpRequest request) {
        OtpService.RequestResult r = service.requestOtp(request.phone());
        return new OtpRequestResponse(r.retryAfterSeconds(), r.demoCode());
    }

    @Override
    public OtpVerifyResponse verifyOtp(OtpVerifyRequest request) {
        AuthService.PhoneLogin r = service.verifyOtp(request.phone(), request.code());
        if (r.session() == null) return new OtpVerifyResponse(null, null, r.signupToken());
        return new OtpVerifyResponse(r.session().token(), toDto(r.session().user(), r.session().business()), null);
    }

    @Override
    public AuthResponse registerWithPhone(OtpRegisterRequest request) {
        return toResponse(service.registerWithPhone(request));
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
