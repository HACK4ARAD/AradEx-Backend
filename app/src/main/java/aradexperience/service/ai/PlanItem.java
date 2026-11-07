package aradexperience.service.ai;

import aradexperience.enums.EventCategory;
import aradexperience.enums.ItineraryStopKind;
import aradexperience.enums.PlaceType;

import java.time.OffsetDateTime;
import java.util.Set;

/**
 * T13 · un candidat pentru itinerariu, oricare ar fi sursa (loc, eveniment, ofertă): ce trebuie ca să-l punem în prompt, să-l
 * punctăm după interese și să-i calculăm orele. `county` = în afara orașului (tag-ul „judet” sau la peste 12 km de centru).
 */
record PlanItem(ItineraryStopKind kind, long refId, String name, String subtitle, double lat, double lng, int visitMinutes,
                Set<String> tags, PlaceType placeType, EventCategory eventCategory, boolean guided, String imageUrl,
                String parkingHint, OffsetDateTime startAt, OffsetDateTime endAt, boolean exteriorOnly, boolean county,
                double rating, String openingHours, double kmFromCentre, String offerLabel) {

    String key() {
        return key(kind, refId);
    }

    static String key(ItineraryStopKind kind, long refId) {
        return kind.name() + ":" + refId;
    }

    /** O pauză de cafea sau de masă: oferta unei cafenele/restaurant sau un loc de tip RESTAURANT/CAFE. */
    boolean food() {
        return kind == ItineraryStopKind.OFFER || placeType == PlaceType.RESTAURANT || placeType == PlaceType.CAFE;
    }

    boolean hasAnyTag(String... wanted) {
        for (String t : wanted) if (tags.contains(t)) return true;
        return false;
    }
}
