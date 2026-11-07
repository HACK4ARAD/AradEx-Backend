package aradexperience.api;

import aradexperience.dto.places.PlaceDto;
import aradexperience.dto.places.PlaceSummary;
import aradexperience.enums.PlaceType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/** docs/API.md §Locuri & ghid (T05) — citire publică. Implementat de `controller/places/PlacesController`. */
@Tag(name = "Places")
@RequestMapping("/api/v1")
public interface PlacesApi {

    @Operation(summary = "Obiectivele (fără paginare, < 100); filtre pe tip, text, etichete; `near=lat,lng` adaugă distanța și sortează după ea")
    @GetMapping("/places")
    List<PlaceSummary> list(
            @RequestParam(required = false) PlaceType type,
            @Parameter(description = "caută în nume, descriere și etichete (fără diacritice)") @RequestParam(required = false) String q,
            @Parameter(description = "lat,lng — ex. 46.1866,21.3123") @RequestParam(required = false) String near,
            @Parameter(description = "etichete separate prin virgulă; potrivire pe oricare") @RequestParam(required = false) String tags);

    @Operation(summary = "Detaliul unui obiectiv cu punctele de ghid ordonate; 404 dacă nu există")
    @GetMapping("/places/{id}")
    PlaceDto get(@PathVariable long id,
                 @Parameter(description = "lat,lng — opțional, pentru distanță / „Ești aici”") @RequestParam(required = false) String near);
}
