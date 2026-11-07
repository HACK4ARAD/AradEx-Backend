package aradexperience.service.itineraries;

import aradexperience.dto.itineraries.ItineraryDto;
import aradexperience.entity.itineraries.Itinerary;
import aradexperience.exceptions.NotFoundException;
import aradexperience.mapper.itineraries.ItineraryMapper;
import aradexperience.repository.itineraries.ItineraryRepository;
import aradexperience.util.Codes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import static aradexperience.mapper.itineraries.ItineraryMapper.toDto;

/**
 * T13 · itinerariile salvate: POST /itineraries (cu sau fără cont, 20/min de la aceeași adresă), cu opririle refăcute din DB
 * (`ItineraryStops`); public se deschid **doar după codul de partajare** `AX-I-XXXXXX` (id-urile sunt consecutive), după id doar
 * de proprietar; „Salvează la mine” leagă un itinerariu fără proprietar de contul meu sau îmi face o copie; lista mea.
 */
@Slf4j
@Service
public class ItinerariesService {

    static final String CODE_PREFIX = "AX-I";
    private static final Pattern NUMERIC = Pattern.compile("\\d{1,18}");
    private static final int CODE_ATTEMPTS = 10;

    private final ItineraryRepository repo;
    private final ItineraryStops stops;
    private final ItinerarySaveLimiter limiter;
    private Clock clock = Clock.systemUTC();

    @Autowired
    public ItinerariesService(ItineraryRepository repo, ItineraryStops stops, ItinerarySaveLimiter limiter) {
        this.repo = repo;
        this.stops = stops;
        this.limiter = limiter;
    }

    /** Pentru teste. */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    @Transactional
    public ItineraryDto save(ItineraryDto in, Optional<Long> userId, String clientKey) {
        limiter.check(clientKey);
        ItineraryDto body = new ItineraryDto(null, null, in.title(), in.summary(), in.ask(), in.date(), in.days(), in.transport(), in.startTime(),
                in.totalMinutes(), in.totalKm(), in.offline(), stops.canonical(in.stops()), null);
        Itinerary i = ItineraryMapper.toEntity(body);
        i.setUserId(userId.orElse(null));
        i.setShareCode(newCode());
        i.setCreatedAt(OffsetDateTime.now(clock));
        Itinerary saved = repo.save(i);
        log.info("Itinerary {} saved ({} stops, {})", saved.getId(), body.stops().size(), userId.isPresent() ? "user" : "anonymous");
        return toDto(saved);
    }

    /** Codul de partajare: oricine; id-ul numeric: doar proprietarul (altfel 404, ca să nu se poată număra itinerariile). */
    @Transactional(readOnly = true)
    public ItineraryDto get(String idOrShareCode, Optional<Long> userId) {
        String key = idOrShareCode == null ? "" : idOrShareCode.trim();
        Optional<Itinerary> found = NUMERIC.matcher(key).matches()
                ? repo.findById(Long.parseLong(key)).filter(i -> i.getUserId() != null && userId.isPresent() && i.getUserId().equals(userId.get()))
                : repo.findByShareCode(key.toUpperCase(Locale.ROOT));
        return found.map(ItineraryMapper::toDto).orElseThrow(() -> new NotFoundException("Itinerary not found"));
    }

    /**
     * „Salvează la mine”: un itinerariu fără proprietar (generat înainte de login) devine al meu; unul al meu rămâne la fel;
     * unul al altcuiva (primit printr-un link) îmi devine copie, cu cod nou.
     */
    @Transactional
    public ItineraryDto saveToMine(String shareCode, long userId) {
        Itinerary i = repo.findByShareCode(shareCode == null ? "" : shareCode.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new NotFoundException("Itinerary not found"));
        if (i.getUserId() == null) {
            i.setUserId(userId);
            return toDto(repo.save(i));
        }
        if (i.getUserId() == userId) return toDto(i);
        Itinerary copy = ItineraryMapper.toEntity(toDto(i));
        copy.setUserId(userId);
        copy.setShareCode(newCode());
        copy.setCreatedAt(OffsetDateTime.now(clock));
        return toDto(repo.save(copy));
    }

    @Transactional(readOnly = true)
    public List<ItineraryDto> mine(long userId) {
        return repo.findByUserIdOrderByCreatedAtDescIdDesc(userId).stream().map(ItineraryMapper::toDto).toList();
    }

    private String newCode() {
        for (int n = 0; n < CODE_ATTEMPTS; n++) {
            String code = Codes.generate(CODE_PREFIX);
            if (!repo.existsByShareCode(code)) return code;
        }
        throw new IllegalStateException("Could not generate a unique itinerary share code");
    }
}
