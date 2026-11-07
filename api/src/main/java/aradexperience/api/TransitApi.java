package aradexperience.api;

import aradexperience.dto.transit.StopArrivalsDto;
import aradexperience.dto.transit.TransitRouteDto;
import aradexperience.dto.transit.TransitStopDto;
import aradexperience.dto.transit.TransitVehiclesDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * docs/API.md §Transport public (T22) — public, fără cont. Stațiile și liniile din seed (V12_1), vehiculele și sosirile
 * live din API-ul public Telelink, printr-un cache comun de 25 s; dacă sursa pică: rezervă statică, niciodată 500.
 * Date: CTP Arad / Telelink. Implementat de `controller/transit/TransitController`.
 */
@Tag(name = "Transit", description = "Transport public CTP Arad. Date: CTP Arad / Telelink")
@RequestMapping("/api/v1")
public interface TransitApi {

    String ATTRIBUTION = "Date: CTP Arad / Telelink";

    @Operation(summary = "Toate stațiile CTP (299), cu coordonate și liniile care opresc în fiecare")
    @GetMapping("/transit/stops")
    List<TransitStopDto> stops();

    @Operation(summary = "Liniile CTP (tramvai + autobuz) cu variantele: opririle ordonate și traseul [lat, lng]")
    @GetMapping("/transit/routes")
    List<TransitRouteDto> routes();

    @Operation(summary = "Vehiculele live (cache 25 s); `live=false` + listă goală / poziții vechi dacă sursa nu răspunde")
    @GetMapping("/transit/vehicles")
    TransitVehiclesDto vehicles();

    @Operation(summary = "Sosirile următoare într-o stație (cache 25 s); 404 dacă stația nu există")
    @GetMapping("/transit/stops/{id}/arrivals")
    StopArrivalsDto arrivals(@PathVariable int id);

    @Operation(summary = "Stația cea mai apropiată de un punct + sosirile următoare (și din peronul de vizavi, același nume)")
    @GetMapping("/transit/nearest")
    StopArrivalsDto nearest(
            @Parameter(description = "latitudine, ex. 46.1754") @RequestParam(required = false) Double lat,
            @Parameter(description = "longitudine, ex. 21.3193") @RequestParam(required = false) Double lng);
}
