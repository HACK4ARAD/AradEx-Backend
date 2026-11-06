package aradexperience.api;

import aradexperience.dto.auth.AuthResponse;
import aradexperience.dto.auth.LoginRequest;
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

    @Operation(summary = "Cont nou (VISITOR | LOCAL | BUSINESS); 409 dacă emailul există")
    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    AuthResponse register(@Valid @RequestBody RegisterRequest request);

    @Operation(summary = "Login; 401 la date greșite")
    @PostMapping("/auth/login")
    AuthResponse login(@Valid @RequestBody LoginRequest request);

    @Operation(summary = "Utilizatorul curent")
    @GetMapping("/me")
    UserDto me();

    @Operation(summary = "Actualizează numele afișat / limba")
    @PatchMapping("/me")
    UserDto updateMe(@Valid @RequestBody UpdateMeRequest request);
}
