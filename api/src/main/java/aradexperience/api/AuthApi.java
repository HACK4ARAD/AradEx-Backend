package aradexperience.api;

import aradexperience.dto.auth.AuthResponse;
import aradexperience.dto.auth.LoginRequest;
import aradexperience.dto.auth.ForgotPasswordRequest;
import aradexperience.dto.auth.RegisterRequest;
import aradexperience.dto.auth.ResetPasswordRequest;
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

    @Operation(summary = "Cont nou cu email + parolă: VISITOR, LOCAL sau BUSINESS (cu `business`); 409 dacă emailul există")
    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    AuthResponse register(@Valid @RequestBody RegisterRequest request);

    @Operation(summary = "Login cu email + parolă, pentru toate rolurile (fără voluntari); 401 date greșite, 403 cont dezactivat")
    @PostMapping("/auth/login")
    AuthResponse login(@Valid @RequestBody LoginRequest request);

    @Operation(summary = "Am uitat parola: trimite pe email un cod de 6 cifre (15 min). Mereu 204, indiferent dacă există contul; 429 prea des")
    @PostMapping("/auth/password/forgot")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request);

    @Operation(summary = "Parolă nouă cu codul primit pe email → token + user; 401 cod invalid/expirat, 403 cont dezactivat, 429 prea des")
    @PostMapping("/auth/password/reset")
    AuthResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request);

    @Operation(summary = "Utilizatorul curent")
    @GetMapping("/me")
    UserDto me();

    @Operation(summary = "Actualizează numele afișat / limba")
    @PatchMapping("/me")
    UserDto updateMe(@Valid @RequestBody UpdateMeRequest request);
}
