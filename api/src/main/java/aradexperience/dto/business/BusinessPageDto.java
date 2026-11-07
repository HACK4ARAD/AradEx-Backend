package aradexperience.dto.business;

import aradexperience.dto.events.EventSummary;
import aradexperience.dto.offers.OfferSummary;

import java.util.List;

/** GET `/businesses/{id}`: pagina publică a firmei — profil, oferte active (T10), evenimente viitoare, anunțuri publicate. */
public record BusinessPageDto(BusinessDto business, List<OfferSummary> offers, List<EventSummary> events,
                              List<AnnouncementDto> announcements) {
}
