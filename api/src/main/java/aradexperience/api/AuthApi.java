package aradexperience.api;

import aradexperience.dto.auth.AuthResponse;
import aradexperience.dto.auth.LoginRequest;
import aradexperience.dto.auth.ChangePasswordRequest;
import aradexperience.dto.auth.ChangePasswordResponse;
import aradexperience.dto.auth.RegisterRequest;
import aradexperience.dto.auth.RecoverPasswordRequest;
import aradexperience.dto.auth.RecoveryCodeRequest;
import aradexperience.dto.auth.RecoveryCodeResponse;
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

    @Operation(summary = "Cont nou cu email + parolă: VISITOR, LOCAL sau BUSINESS (cu `business`) → token + user + codul de recuperare (arătat o singură dată); 409 dacă emailul există")
    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    AuthResponse register(@Valid @RequestBody RegisterRequest request);

    @Operation(summary = "Login cu email + parolă, pentru toate rolurile (fără voluntari); 401 date greșite, 403 cont dezactivat")
    @PostMapping("/auth/login")
    AuthResponse login(@Valid @RequestBody LoginRequest request);

    @Operation(summary = "Am uitat parola: email + codul de recuperare + parola nouă → token + user + un cod de recuperare NOU (cel folosit nu mai merge); 401 date greșite, 403 cont dezactivat, 429 prea multe încercări greșite")
    @PostMapping("/auth/password/recover")
    AuthResponse recoverPassword(@Valid @RequestBody RecoverPasswordRequest request);

    @Operation(summary = "Utilizatorul curent")
    @GetMapping("/me")
    UserDto me();

    @Operation(summary = "Actualizează numele afișat / limba")
    @PatchMapping("/me")
    UserDto updateMe(@Valid @RequestBody UpdateMeRequest request);

    @Operation(summary = "Schimbă parola (parola actuală sau cea temporară + cea nouă) → { recoveryCode } nou doar după o parolă temporară sau dacă contul n-avea cod; 400 parola actuală greșită, 403 cont demo")
    @PostMapping("/me/password")
    ChangePasswordResponse changePassword(@Valid @RequestBody ChangePasswordRequest request);

    @Operation(summary = "Cod de recuperare nou (cere parola) → { recoveryCode }, arătat o singură dată; vechiul nu mai merge; 400 parolă greșită, 403 cont demo")
    @PostMapping("/me/recovery-code")
    RecoveryCodeResponse newRecoveryCode(@Valid @RequestBody RecoveryCodeRequest request);
}
