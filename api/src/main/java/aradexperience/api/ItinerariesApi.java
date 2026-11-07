package aradexperience.api;

import aradexperience.dto.itineraries.ItineraryDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

/** docs/API.md §AI (T13): itinerariile salvate. Implementat de `controller/itineraries/ItinerariesController`. */
@Tag(name = "Itineraries")
@RequestMapping("/api/v1")
public interface ItinerariesApi {

    @Operation(summary = "Salvează un itinerariu generat (și fără cont; cu login se leagă de utilizator) → id + shareCode")
    @PostMapping("/itineraries")
    @ResponseStatus(HttpStatus.CREATED)
    ItineraryDto save(@Valid @RequestBody ItineraryDto itinerary);

    @Operation(summary = "T13: îl leg de contul meu (dacă n-are proprietar) sau îmi fac o copie; 404 cod necunoscut")
    @PostMapping("/itineraries/{shareCode}/save")
    ItineraryDto saveToMine(@PathVariable("shareCode") String shareCode);

    @Operation(summary = "Un itinerariu salvat: după codul de partajare (AX-I-…) oricine, după id doar proprietarul; 404 altfel")
    @GetMapping("/itineraries/{idOrShareCode}")
    ItineraryDto get(@PathVariable("idOrShareCode") String idOrShareCode);

    @Operation(summary = "Itinerariile mele, cele mai noi primele")
    @GetMapping("/me/itineraries")
    List<ItineraryDto> mine();
}
