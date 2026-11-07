package aradexperience.api;

import aradexperience.dto.points.PointsDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** docs/API.md §Auth & utilizator: GET /me/points (T11). Implementat de `controller/points/PointsController`. */
@Tag(name = "Points")
@RequestMapping("/api/v1")
public interface PointsApi {

    @Operation(summary = "Soldul meu, punctele lunii, locul în clasamentul lunii, seria de luni active, ultimele tranzacții")
    @GetMapping("/me/points")
    PointsDto myPoints();
}
