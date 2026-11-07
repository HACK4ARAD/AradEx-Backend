package aradexperience.api;

import aradexperience.dto.places.GuidePointDto;
import aradexperience.dto.places.GuidePointRequest;
import aradexperience.dto.places.PlaceDto;
import aradexperience.dto.places.PlaceRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

/** docs/API.md §Locuri & ghid (T05) — „Obiective & ghiduri” (W25) pentru Primărie și Admin (`/city/**` în SecurityConfig). */
@Tag(name = "City · Places")
@RequestMapping("/api/v1/city/places")
public interface CityPlacesApi {

    @Operation(summary = "Obiectiv nou; 409 dacă slug-ul există")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PlaceDto create(@Valid @RequestBody PlaceRequest request);

    @Operation(summary = "Modifică un obiectiv; 404 / 409 (slug)")
    @PutMapping("/{id}")
    PlaceDto update(@PathVariable long id, @Valid @RequestBody PlaceRequest request);

    @Operation(summary = "Șterge un obiectiv și punctele lui de ghid; 409 dacă are evenimente legate")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long id);

    @Operation(summary = "Punct de ghid nou (la final dacă lipsește `position`); 409 dacă poziția e ocupată")
    @PostMapping("/{id}/guide-points")
    @ResponseStatus(HttpStatus.CREATED)
    GuidePointDto addGuidePoint(@PathVariable long id, @Valid @RequestBody GuidePointRequest request);

    @Operation(summary = "Modifică un punct de ghid; o poziție ocupată face schimb cu punctul de acolo")
    @PutMapping("/{id}/guide-points/{gpId}")
    GuidePointDto updateGuidePoint(@PathVariable long id, @PathVariable long gpId, @Valid @RequestBody GuidePointRequest request);

    @Operation(summary = "Șterge un punct de ghid")
    @DeleteMapping("/{id}/guide-points/{gpId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteGuidePoint(@PathVariable long id, @PathVariable long gpId);
}
