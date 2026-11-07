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
 * T13 · itinerariile salvate: POST /itineraries (cu sau fără cont), deschise după id sau după codul de partajare `AX-I-XXXXXX`,
 * și lista mea (cele mai noi primele). Itinerariul se salvează așa cum l-a primit aplicația de la POST /ai/itinerary.
 */
@Slf4j
@Service
public class ItinerariesService {

    static final String CODE_PREFIX = "AX-I";
    private static final Pattern NUMERIC = Pattern.compile("\\d{1,18}");
    private static final int CODE_ATTEMPTS = 10;

    private final ItineraryRepository repo;
    private Clock clock = Clock.systemUTC();

    @Autowired
    public ItinerariesService(ItineraryRepository repo) {
        this.repo = repo;
    }

    /** Pentru teste. */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    @Transactional
    public ItineraryDto save(ItineraryDto body, Optional<Long> userId) {
        Itinerary i = ItineraryMapper.toEntity(body);
        i.setUserId(userId.orElse(null));
        i.setShareCode(newCode());
        i.setCreatedAt(OffsetDateTime.now(clock));
        Itinerary saved = repo.save(i);
        log.info("Itinerary {} saved ({} stops, {})", saved.getId(), body.stops().size(), userId.isPresent() ? "user" : "anonymous");
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public ItineraryDto get(String idOrShareCode) {
        String key = idOrShareCode == null ? "" : idOrShareCode.trim();
        Optional<Itinerary> found = NUMERIC.matcher(key).matches()
                ? repo.findById(Long.parseLong(key))
                : repo.findByShareCode(key.toUpperCase(Locale.ROOT));
        return found.map(ItineraryMapper::toDto).orElseThrow(() -> new NotFoundException("Itinerary not found"));
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
