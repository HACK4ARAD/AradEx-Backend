package aradexperience.service.ai;

import org.springframework.stereotype.Component;

/** T13 · cel mult 20 de itinerarii generate pe minut de la aceeași adresă, separat de chat. */
@Component
public class ItineraryRateLimiter extends RateLimiter {

    static final int MAX_PER_MINUTE = 20;

    public ItineraryRateLimiter() {
        super(MAX_PER_MINUTE, "Too many itinerary requests");
    }
}
