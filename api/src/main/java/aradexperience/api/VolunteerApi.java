package aradexperience.api;

import aradexperience.dto.auth.AuthResponse;
import aradexperience.dto.community.ConfirmResultDto;
import aradexperience.dto.community.RegistrationDto;
import aradexperience.dto.volunteer.VolunteerActionDto;
import aradexperience.dto.volunteer.VolunteerLoginRequest;
import aradexperience.dto.volunteer.VolunteerMeDto;
import aradexperience.dto.volunteer.VolunteerScanRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * docs/API.md §Voluntari (T18), partea din aplicație: intrarea cu codul de 6 cifre (public, `/auth/**`) și cele 3 tab-uri ale
 * voluntarului (`/vol/**` → VOLUNTEER în SecurityConfig); chatul de grup e în `VolunteerChatApi`. Un voluntar suspendat sau expirat primește 401 la intrare și 403 aici.
 */
@Tag(name = "Volunteer")
@RequestMapping("/api/v1")
public interface VolunteerApi {

    @Operation(summary = "Intrare cu codul de 6 cifre; 401 cod greșit, cont suspendat sau expirat; 429 după prea multe încercări")
    @PostMapping("/auth/volunteer")
    AuthResponse login(@Valid @RequestBody VolunteerLoginRequest request);

    @Operation(summary = "Antetul „Acțiunile tale”: coordonatorul și contoarele")
    @GetMapping("/vol/me")
    VolunteerMeDto me();

    @Operation(summary = "Acțiunile alocate, în ordinea zilei (LOCKED / OPEN / ENDED)")
    @GetMapping("/vol/actions")
    List<VolunteerActionDto> actions();

    @Operation(summary = "Confirmă participantul după cod (idempotent, ca la Primărie); 403 acțiune nealocată sau încă închisă, 404 cod necunoscut, 409 altă acțiune")
    @PostMapping("/vol/scan")
    ConfirmResultDto scan(@Valid @RequestBody VolunteerScanRequest request);

    @Operation(summary = "Ultimele confirmări făcute de voluntar (Scanări recente)")
    @GetMapping("/vol/scans")
    List<RegistrationDto> scans();
}
