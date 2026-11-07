package aradexperience.api;

import aradexperience.dto.admin.BusinessReject;
import aradexperience.dto.admin.CityBusinessCreate;
import aradexperience.dto.admin.CityBusinessCreated;
import aradexperience.dto.admin.CityBusinessDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

/**
 * docs/API.md §Admin (T14): firmele partenere în panoul Primăriei (`/city/**`: CITY_HALL și ADMIN).
 * Implementat de `controller/admin/CityBusinessesController` în `app`.
 */
@Tag(name = "Partner businesses")
@RequestMapping("/api/v1/city/businesses")
public interface CityBusinessesApi {

    @Operation(summary = "Firmele partenere (neverificatele primele); verified=false → doar cele de verificat")
    @GetMapping
    List<CityBusinessDto> list(@RequestParam(name = "verified", required = false) Boolean verified);

    @Operation(summary = "Cont de firmă nou, verificat; invitația pe e-mail e simulată (parola de unică folosință vine în răspuns)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CityBusinessCreated create(@Valid @RequestBody CityBusinessCreate request);

    @Operation(summary = "Verifică firma: conținutul ei în ciornă (evenimente, oferte, anunțuri) devine public")
    @PostMapping("/{id}/verify")
    CityBusinessDto verify(@PathVariable("id") long id);

    @Operation(summary = "Respinge firma (sau o suspendă, dacă era verificată): conținutul ei rămâne / redevine ciornă")
    @PostMapping("/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reject(@PathVariable("id") long id, @Valid @RequestBody(required = false) BusinessReject request);
}
