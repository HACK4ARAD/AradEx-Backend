package aradexperience.dto.transit;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * `GET /transit/vehicles`. `live=false` = sursa nu a răspuns în ultimele 120 s (rezervă: lista e goală sau
 * ultimele poziții, toate `stale`); `updatedAt` = ultima citire reușită, lipsește dacă n-a fost niciuna.
 */
public record TransitVehiclesDto(
        boolean live,
        OffsetDateTime updatedAt,
        String attribution,
        List<TransitVehicleDto> vehicles) {
}
