package aradexperience.service.events;

import aradexperience.entity.events.Event;

/**
 * Un eveniment cu câmpurile calculate pentru utilizatorul curent: distanța (când cererea are `near`), salvat (doar logat),
 * „Popular” (pragurile din `app.events`) și creditul pozei (din obiectivul legat). Mapper-ul îl transformă în DTO.
 */
public record EventView(Event event, Double distanceKm, Boolean saved, boolean hot, String imageCredit) {
}
