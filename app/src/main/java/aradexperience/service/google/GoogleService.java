package aradexperience.service.google;

import aradexperience.dto.google.GoogleInfoDto;
import aradexperience.dto.google.GoogleLinkDto;
import aradexperience.dto.google.GooglePlaceIdRequest;
import aradexperience.exceptions.ApiException;
import aradexperience.exceptions.NotFoundException;
import aradexperience.repository.google.GoogleLinkRepository;
import aradexperience.repository.google.GoogleLinkRepository.BusinessRow;
import aradexperience.repository.google.GoogleLinkRepository.PlaceRow;
import aradexperience.security.CurrentUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static aradexperience.mapper.google.GoogleMapper.toInfo;

/**
 * T17: ratingul și recenziile Google pentru obiective și firme (docs/API.md §Google).
 * <ul>
 *   <li>Cache în memorie 24 h pe (Place ID, limbă); dacă Google nu răspunde, se dă ultima copie (chiar expirată), altfel 503,
 *       iar o eroare se ține minte 5 min ca să nu lovim Google la fiecare ecran.</li>
 *   <li>Obiectivele (locuri reale din Arad) își caută singure ID-ul Google la prima cerere: Text Search după nume + adresă,
 *       doar în jurul coordonatelor lor, doar ID-ul (cel mai ieftin SKU), apoi se verifică numele locului găsit (`NameMatch`):
 *       Google întoarce cel mai relevant loc din zonă chiar dacă nu e cel căutat. ID-ul potrivit se salvează în
 *       `places.google_place_id`, „negăsit / alt nume” se ține minte 24 h. Primăria îl poate pune / corecta din panou.</li>
 *   <li>Firmele nu se caută automat (firmele demo sunt fictive și ar prinde recenziile altui local): doar dacă firma
 *       și-a legat singură locul de pe Google.</li>
 * </ul>
 * Fără cheie nu se face nicio cerere: endpoint-urile publice dau 404, aplicația ascunde blocul.
 */
@Slf4j
@Service
public class GoogleService {

    static final Duration TTL = Duration.ofHours(24);
    static final Duration ERROR_TTL = Duration.ofMinutes(5);
    static final Duration NOT_FOUND_TTL = Duration.ofHours(24);

    private record Cached(GoogleInfoDto info, Instant expiresAt) {}

    private final GoogleLinkRepository links;
    private final GooglePlacesClient google;
    private final Clock clock;

    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private final Map<String, Instant> failedUntil = new ConcurrentHashMap<>();
    private final Map<Long, Instant> notOnGoogleUntil = new ConcurrentHashMap<>();

    @Autowired
    public GoogleService(GoogleLinkRepository links, GooglePlacesClient google) {
        this(links, google, Clock.systemUTC());
    }

    GoogleService(GoogleLinkRepository links, GooglePlacesClient google, Clock clock) {
        this.links = links;
        this.google = google;
        this.clock = clock;
    }

    /* ---------------- public: aplicația ---------------- */

    public GoogleInfoDto placeInfo(long placeId, String lang) {
        PlaceRow place = links.place(placeId).orElseThrow(() -> NotFoundException.of("Place", placeId));
        requireConfigured();
        String googleId = blankToNull(place.googlePlaceId());
        if (googleId == null) googleId = resolve(place);
        return withRatings(info(googleId, language(lang)));
    }

    public GoogleInfoDto businessInfo(long businessId, String lang) {
        BusinessRow business = links.business(businessId).orElseThrow(() -> NotFoundException.of("Business", businessId));
        requireConfigured();
        String googleId = blankToNull(business.googlePlaceId());
        if (googleId == null) throw new NotFoundException("Business " + businessId + " is not linked to Google");
        return withRatings(info(googleId, language(lang)));
    }

    /* ---------------- panouri: Primăria, firma ---------------- */

    public GoogleLinkDto placeLink(long placeId) {
        PlaceRow place = links.place(placeId).orElseThrow(() -> NotFoundException.of("Place", placeId));
        return link(place.googlePlaceId());
    }

    public GoogleLinkDto setPlaceLink(long placeId, GooglePlaceIdRequest req) {
        links.place(placeId).orElseThrow(() -> NotFoundException.of("Place", placeId));
        String googleId = blankToNull(req.googlePlaceId());
        links.setPlaceGoogleId(placeId, googleId);
        notOnGoogleUntil.remove(placeId);
        log.info("Place {} linked to Google: {}", placeId, googleId != null);
        return link(googleId);
    }

    public GoogleLinkDto myLink(CurrentUser user) {
        return link(myBusiness(user).googlePlaceId());
    }

    public GoogleLinkDto setMyLink(CurrentUser user, GooglePlaceIdRequest req) {
        BusinessRow business = myBusiness(user);
        String googleId = blankToNull(req.googlePlaceId());
        links.setBusinessGoogleId(business.id(), googleId);
        log.info("Business {} linked to Google: {}", business.id(), googleId != null);
        return link(googleId);
    }

    /* ---------------- intern ---------------- */

    private BusinessRow myBusiness(CurrentUser user) {
        return links.businessOfOwner(user.id())
                .orElseThrow(() -> new NotFoundException("You don't have a business"));
    }

    /** Pentru panouri: niciodată eroare din cauza lui Google, doar `info` lipsă. */
    private GoogleLinkDto link(String googlePlaceId) {
        String googleId = blankToNull(googlePlaceId);
        GoogleInfoDto info = null;
        if (googleId != null && google.configured()) {
            try {
                info = info(googleId, "ro");
            } catch (ApiException e) {
                info = null;
            }
        }
        return new GoogleLinkDto(googleId, google.configured(), info);
    }

    private void requireConfigured() {
        if (!google.configured()) throw new NotFoundException("Google reviews are not configured");
    }

    /** Caută ID-ul Google al obiectivului (o singură dată; „negăsit” se ține minte 24 h) și îl salvează. */
    private String resolve(PlaceRow place) {
        Instant now = clock.instant();
        Instant until = notOnGoogleUntil.get(place.id());
        if (until != null && now.isBefore(until)) throw notOnGoogle(place.id());
        Optional<String> found;
        try {
            found = google.findId(query(place), place.lat(), place.lng());
        } catch (RestClientException e) {
            log.warn("Google search failed for place {}: {}", place.id(), e.getClass().getSimpleName());
            throw unavailable();
        }
        if (found.isEmpty()) {
            notOnGoogleUntil.put(place.id(), now.plus(NOT_FOUND_TTL));
            throw notOnGoogle(place.id());
        }
        // Google dă cel mai relevant loc din zonă chiar dacă nu e cel căutat: legăm doar dacă numele seamănă.
        GooglePlaces.Place details;
        try {
            details = google.details(found.get(), "ro");
        } catch (RestClientException e) {
            log.warn("Google details failed for place {}: {}", place.id(), e.getClass().getSimpleName());
            throw unavailable();
        }
        String googleName = details == null || details.displayName() == null ? null : details.displayName().text();
        if (!NameMatch.similar(place.name(), googleName) || details.userRatingCount() == null || details.userRatingCount() == 0) {
            notOnGoogleUntil.put(place.id(), now.plus(NOT_FOUND_TTL));
            log.info("Place {} not linked: the Google result has another name or no ratings", place.id());
            throw notOnGoogle(place.id());
        }
        links.setPlaceGoogleId(place.id(), found.get());
        cache.put(found.get() + "|ro", new Cached(toInfo(details), now.plus(TTL)));
        log.info("Place {} found on Google", place.id());
        return found.get();
    }

    private GoogleInfoDto info(String googleId, String lang) {
        String key = googleId + "|" + lang;
        Instant now = clock.instant();
        Cached hit = cache.get(key);
        if (hit != null && now.isBefore(hit.expiresAt())) return hit.info();
        Instant failed = failedUntil.get(key);
        if (failed != null && now.isBefore(failed)) {
            if (hit != null) return hit.info();
            throw unavailable();
        }
        try {
            GooglePlaces.Place place = google.details(googleId, lang);
            if (place == null) throw new RestClientException("empty Google response");
            GoogleInfoDto info = toInfo(place);
            cache.put(key, new Cached(info, now.plus(TTL)));
            failedUntil.remove(key);
            return info;
        } catch (RestClientException e) {
            log.warn("Google details failed: {}", e.getClass().getSimpleName());
            failedUntil.put(key, now.plus(ERROR_TTL));
            if (hit != null) return hit.info();
            throw unavailable();
        }
    }

    /** Un loc fără rating și fără recenzii (ex. un sat) nu are ce arăta: 404, blocul nu apare. */
    private static GoogleInfoDto withRatings(GoogleInfoDto info) {
        if (!hasRatings(info)) throw new NotFoundException("No Google ratings for this place yet");
        return info;
    }

    private static boolean hasRatings(GoogleInfoDto info) {
        return info.rating() != null || !info.reviews().isEmpty();
    }

    static String query(PlaceRow place) {
        String address = blankToNull(place.address());
        return address == null || address.toLowerCase().contains(place.name().toLowerCase())
                ? place.name() + ", Arad"
                : place.name() + ", " + address;
    }

    static String language(String lang) {
        return "en".equalsIgnoreCase(lang) ? "en" : "ro";
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static NotFoundException notOnGoogle(long placeId) {
        return new NotFoundException("Place " + placeId + " was not found on Google");
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Google is not responding right now, try again later");
    }
}
