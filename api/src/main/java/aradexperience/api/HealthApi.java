package aradexperience.api;

import aradexperience.dto.common.HealthDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** Contractul REST al unui modul: interfață cu mapping-uri + documentație; controllerul din `app` o implementează. */
@Tag(name = "Health")
@RequestMapping("/api/v1/health")
public interface HealthApi {

    @Operation(summary = "Starea API-ului (public)")
    @GetMapping
    HealthDto health();
}
