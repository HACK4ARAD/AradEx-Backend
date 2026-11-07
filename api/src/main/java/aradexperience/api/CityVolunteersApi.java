package aradexperience.api;

import aradexperience.dto.volunteer.CoordinatorDto;
import aradexperience.dto.volunteer.VolunteerCreate;
import aradexperience.dto.volunteer.VolunteerDto;
import aradexperience.dto.volunteer.VolunteerUpdate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

/** docs/API.md §Voluntari (T18), panoul Primăriei: conturile, alocarea pe acțiuni, suspendarea (`/city/**`). Primăria nu e în chaturile voluntarilor. */
@Tag(name = "City · Volunteers")
@RequestMapping("/api/v1/city")
public interface CityVolunteersApi {

    @Operation(summary = "Toți voluntarii (cu scanări, mesaje necitite, ultimul mesaj)")
    @GetMapping("/volunteers")
    List<VolunteerDto> list();

    @Operation(summary = "Cont nou: cod de 6 cifre unic, „trimis prin SMS” (simulat); 400 acțiune inexistentă / nepublicată sau coordonator invalid")
    @PostMapping("/volunteers")
    @ResponseStatus(HttpStatus.CREATED)
    VolunteerDto create(@Valid @RequestBody VolunteerCreate request);

    @Operation(summary = "Acțiunile alocate, suspendare / reactivare, coordonator; 404")
    @PatchMapping("/volunteers/{id}")
    VolunteerDto update(@PathVariable long id, @Valid @RequestBody VolunteerUpdate request);

    @Operation(summary = "Coordonatorii posibili (conturile Primăriei)")
    @GetMapping("/coordinators")
    List<CoordinatorDto> coordinators();
}
