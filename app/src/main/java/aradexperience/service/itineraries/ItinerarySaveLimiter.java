package aradexperience.service.itineraries;

import aradexperience.service.ai.RateLimiter;
import org.springframework.stereotype.Component;

/** T13 · cel mult 20 de itinerarii salvate pe minut de la aceeași adresă (salvarea e publică). */
@Component
public class ItinerarySaveLimiter extends RateLimiter {

    public ItinerarySaveLimiter() {
        super(20, "Too many itineraries saved");
    }
}
