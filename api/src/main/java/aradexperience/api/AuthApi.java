package aradexperience.api;

import aradexperience.dto.auth.AuthResponse;
import aradexperience.dto.auth.LoginRequest;
import aradexperience.dto.auth.OtpRegisterRequest;
import aradexperience.dto.auth.OtpRequest;
import aradexperience.dto.auth.OtpRequestResponse;
import aradexperience.dto.auth.OtpVerifyRequest;
import aradexperience.dto.auth.OtpVerifyResponse;
import aradexperience.dto.auth.RegisterRequest;
import aradexperience.dto.auth.UpdateMeRequest;
import aradexperience.dto.auth.UserDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

/** docs/API.md §Auth & utilizator (T03). Implementat de `controller/auth/AuthController` în `app`. */
@Tag(name = "Auth")
@RequestMapping("/api/v1")
public interface AuthApi {

    @Operation(summary = "Cont nou de firmă (BUSINESS); 409 dacă emailul există. Conturile personale se fac cu telefonul (/auth/otp/*)")
    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    AuthResponse register(@Valid @RequestBody RegisterRequest request);

    @Operation(summary = "Login cu email (firme, Primărie, Admin); 401 la date greșite, 403 pentru conturile personale")
    @PostMapping("/auth/login")
    AuthResponse login(@Valid @RequestBody LoginRequest request);

    @Operation(summary = "T21: trimite codul OTP (SMS; numerele de demo 0799000xxx primesc codul în răspuns); 429 prea des")
    @PostMapping("/auth/otp/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    OtpRequestResponse requestOtp(@Valid @RequestBody OtpRequest request);

    @Operation(summary = "T21: verifică codul; cont existent → token + user, număr nou → signupToken; 401 cod invalid/expirat")
    @PostMapping("/auth/otp/verify")
    OtpVerifyResponse verifyOtp(@Valid @RequestBody OtpVerifyRequest request);

    @Operation(summary = "T21: cont personal nou (VISITOR | LOCAL) cu signupToken; 401 token expirat, 409 numărul are cont")
    @PostMapping("/auth/otp/register")
    @ResponseStatus(HttpStatus.CREATED)
    AuthResponse registerWithPhone(@Valid @RequestBody OtpRegisterRequest request);

    @Operation(summary = "Utilizatorul curent")
    @GetMapping("/me")
    UserDto me();

    @Operation(summary = "Actualizează numele afișat / limba")
    @PatchMapping("/me")
    UserDto updateMe(@Valid @RequestBody UpdateMeRequest request);
}
