package aradexperience.api;

import aradexperience.dto.community.ActionCreate;
import aradexperience.dto.community.ActionDto;
import aradexperience.dto.community.CityDashboardDto;
import aradexperience.dto.community.ConfirmCodeRequest;
import aradexperience.dto.community.ConfirmResultDto;
import aradexperience.dto.community.RegistrationDto;
import aradexperience.enums.RegistrationStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

/** docs/API.md §Comunitate & puncte (T11), panoul Primăriei (`/city/**` = CITY_HALL, ADMIN). Implementat de `CityCommunityController`. */
@Tag(name = "City · Community")
@RequestMapping("/api/v1/city")
public interface CityCommunityApi {

    @Operation(summary = "Panoul Primăriei: KPI, participarea la acțiuni, activitate recentă")
    @GetMapping("/dashboard")
    CityDashboardDto dashboard();

    @Operation(summary = "Toate acțiunile (inclusiv ciorne), cele mai noi primele")
    @GetMapping("/actions")
    List<ActionDto> actions();

    @Operation(summary = "Acțiune nouă (PUBLISHED sau DRAFT); 400 dacă endAt <= startAt")
    @PostMapping("/actions")
    @ResponseStatus(HttpStatus.CREATED)
    ActionDto create(@Valid @RequestBody ActionCreate request);

    @Operation(summary = "Editează o acțiune; 409 dacă locurile scad sub numărul de înscriși")
    @PutMapping("/actions/{id}")
    ActionDto update(@PathVariable long id, @Valid @RequestBody ActionCreate request);

    @Operation(summary = "Înscrișii unei acțiuni (cu user), opțional filtrați după stare")
    @GetMapping("/actions/{id}/registrations")
    List<RegistrationDto> registrations(@PathVariable long id, @RequestParam(required = false) RegistrationStatus status);

    @Operation(summary = "Confirmă după cod (QR); idempotent: a doua oară alreadyConfirmed=true, 0 puncte; 404 cod necunoscut")
    @PostMapping("/registrations/confirm")
    ConfirmResultDto confirmCode(@Valid @RequestBody ConfirmCodeRequest request);

    @Operation(summary = "Confirmă din listă; idempotent")
    @PostMapping("/registrations/{id}/confirm")
    ConfirmResultDto confirm(@PathVariable long id);

    @Operation(summary = "Respinge (NO_SHOW); 409 dacă e deja confirmată")
    @PostMapping("/registrations/{id}/reject")
    RegistrationDto reject(@PathVariable long id);
}
