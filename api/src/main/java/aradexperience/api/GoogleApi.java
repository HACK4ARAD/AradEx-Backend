package aradexperience.api;

import aradexperience.dto.google.GoogleInfoDto;
import aradexperience.dto.google.GoogleLinkDto;
import aradexperience.dto.google.GooglePlaceIdRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * docs/API.md §Google (T17): ratingul și recenziile Google pentru obiective și firme, din Google Places API (New),
 * doar prin backend (cheia `GOOGLE_PLACES_API_KEY` nu ajunge în aplicație), cu cache 24 h. Doar citire: nimic nu se
 * scrie spre Google. Implementat de `controller/google/GoogleController`.
 */
@Tag(name = "Google", description = "Rating și recenzii Google (Places API New) pentru obiective și firme. Powered by Google")
@RequestMapping("/api/v1")
public interface GoogleApi {

    @Operation(summary = "Ratingul și recenziile Google ale unui obiectiv; ID-ul Google se caută automat la prima cerere. "
            + "404 dacă obiectivul nu există, nu are loc pe Google sau backend-ul n-are cheie; 503 dacă Google nu răspunde")
    @GetMapping("/places/{id}/google")
    GoogleInfoDto place(@PathVariable("id") long id,
                        @Parameter(description = "limba recenziilor: ro (implicit) sau en") @RequestParam(required = false) String lang);

    @Operation(summary = "Ratingul și recenziile Google ale unei firme (doar dacă firma și-a legat locul de pe Google). "
            + "404 dacă firma nu există, nu e legată sau backend-ul n-are cheie; 503 dacă Google nu răspunde")
    @GetMapping("/businesses/{id}/google")
    GoogleInfoDto business(@PathVariable("id") long id,
                           @Parameter(description = "limba recenziilor: ro (implicit) sau en") @RequestParam(required = false) String lang);

    @Operation(summary = "Primăria: legătura obiectivului cu Google (ID + rating, dacă e legat); nu caută automat")
    @GetMapping("/city/places/{id}/google")
    GoogleLinkDto placeLink(@PathVariable("id") long id);

    @Operation(summary = "Primăria: leagă obiectivul de un Place ID Google (gol = dezleagă); 400 dacă ID-ul nu e valid")
    @PutMapping("/city/places/{id}/google")
    GoogleLinkDto setPlaceLink(@PathVariable("id") long id, @Valid @RequestBody GooglePlaceIdRequest request);

    @Operation(summary = "Firma: legătura firmei mele cu Google (ID + rating, dacă e legată); 404 dacă n-am firmă")
    @GetMapping("/business/me/google")
    GoogleLinkDto myLink();

    @Operation(summary = "Firma: leagă firma mea de locul ei de pe Google (gol = dezleagă); 400 dacă ID-ul nu e valid")
    @PutMapping("/business/me/google")
    GoogleLinkDto setMyLink(@Valid @RequestBody GooglePlaceIdRequest request);
}
