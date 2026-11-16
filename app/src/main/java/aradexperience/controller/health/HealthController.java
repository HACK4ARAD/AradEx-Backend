package aradexperience.controller.health;

import aradexperience.api.HealthApi;
import aradexperience.dto.common.HealthDto;
import aradexperience.service.health.HealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/** Șablonul unui controller: implementează interfața din `api`, zero logică, delegă la service. */
@RestController
@RequiredArgsConstructor
public class HealthController implements HealthApi {

    private final HealthService service;

    @Override
    public HealthDto health() {
        service.check();
        return new HealthDto("ok", OffsetDateTime.now().toString(), "0.1.0");
    }
}
