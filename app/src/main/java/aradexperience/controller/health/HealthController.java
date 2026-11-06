package aradexperience.controller.health;

import aradexperience.api.HealthApi;
import aradexperience.dto.common.HealthDto;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/** Șablonul unui controller: implementează interfața din `api`, zero logică, delegă la service când există. */
@RestController
public class HealthController implements HealthApi {

    @Override
    public HealthDto health() {
        return new HealthDto("ok", OffsetDateTime.now().toString(), "0.1.0");
    }
}
