package aradexperience.service.ai;

import aradexperience.dto.ai.ChatMessageDto;
import aradexperience.dto.ai.ChatRequest;
import aradexperience.dto.ai.ChatResponse;
import aradexperience.entity.events.Event;
import aradexperience.entity.places.Place;
import aradexperience.enums.ContentStatus;
import aradexperience.enums.TicketType;
import aradexperience.repository.events.EventRepository;
import aradexperience.repository.places.GuidePointRepository;
import aradexperience.repository.places.PlaceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * T12 · chatul (S8): Claude răspunde din catalogul evenimentelor din următoarele 14 zile (+ obiectivele cu ghid), pune o
 * întrebare de clarificare când lipsesc ziua sau compania, recomandă doar id-uri din catalog (BE le filtrează).
 * Dacă Claude nu răspunde sau răspunsul nu e JSON valid → răspuns pregătit (`ChatFallback`), `offline = true`.
 */
@Slf4j
@Service
public class ChatService {

    static final ZoneId ARAD = ZoneId.of("Europe/Bucharest");
    static final int CATALOG_DAYS = 14;
    static final int MAX_CATALOG = 60;
    static final int MAX_PLACES = 25;
    static final int MAX_HISTORY = 12;
    static final int MAX_SUGGESTED = 4;
    static final int MAX_CHIPS = 4;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE yyyy-MM-dd HH:mm", Locale.ROOT);
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ClaudeClient claude;
    private final EventRepository events;
    private final PlaceRepository places;
    private final GuidePointRepository guide;
    private final ChatFallback fallback;
    private final ChatRateLimiter limiter;
    private Clock clock = Clock.systemUTC();

    @Autowired
    public ChatService(ClaudeClient claude, EventRepository events, PlaceRepository places, GuidePointRepository guide,
                       ChatFallback fallback, ChatRateLimiter limiter) {
        this.claude = claude;
        this.events = events;
        this.places = places;
        this.guide = guide;
        this.fallback = fallback;
        this.limiter = limiter;
    }

    /** Pentru teste. */
    void setClock(Clock clock) {
        this.clock = clock;
        limiter.setClock(clock);
    }

    @Transactional(readOnly = true)
    public ChatResponse chat(ChatRequest req, String clientKey) {
        limiter.check(clientKey);
        String locale = "en".equals(req.locale()) ? "en" : "ro";
        ZonedDateTime now = clock.instant().atZone(ARAD);
        List<Event> catalog = catalog(now);
        List<ChatMessageDto> history = history(req.messages());

        Optional<ChatResponse> answer = claude.complete(systemPrompt(locale, now, catalog, guidedPlaces()), history)
                .flatMap(raw -> parse(raw, catalog));
        if (answer.isPresent()) return answer.get();

        String userText = req.messages().stream().filter(m -> "user".equals(m.role())).map(ChatMessageDto::content)
                .collect(Collectors.joining(" "));
        ChatFallback.Answer a = fallback.answer(userText, locale, catalog, now);
        log.info("Chat answered offline");
        return new ChatResponse(a.reply(), a.eventIds(), a.quickReplies(), true);
    }

    /** Evenimentele publicate care nu s-au terminat și încep în următoarele 14 zile, după dată, cel mult 60. */
    List<Event> catalog(ZonedDateTime now) {
        OffsetDateTime limit = now.plusDays(CATALOG_DAYS).toOffsetDateTime();
        return events.findCandidates(List.of(ContentStatus.PUBLISHED), now.toOffsetDateTime()).stream()
                .filter(e -> e.getStartAt() != null && e.getStartAt().isBefore(limit))
                .limit(MAX_CATALOG)
                .toList();
    }

    private List<Place> guidedPlaces() {
        Set<Long> ids = guide.stats().stream().map(GuidePointRepository.GuideStats::getPlaceId).collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) return List.of();
        return places.findAllById(ids).stream().limit(MAX_PLACES).toList();
    }

    /** Ultimele mesaje, începând cu unul al omului (Claude cere ca primul mesaj să fie al utilizatorului). */
    static List<ChatMessageDto> history(List<ChatMessageDto> all) {
        List<ChatMessageDto> tail = all.size() > MAX_HISTORY ? all.subList(all.size() - MAX_HISTORY, all.size()) : all;
        int start = 0;
        while (start < tail.size() && !"user".equals(tail.get(start).role())) start++;
        return List.copyOf(tail.subList(start, tail.size()));
    }

    String systemPrompt(String locale, ZonedDateTime now, List<Event> catalog, List<Place> guided) {
        StringBuilder sb = new StringBuilder();
        sb.append("Ești asistentul aplicației AradExperience din Arad, România: ajuți vizitatorii și localnicii să găsească ")
          .append("evenimente și locuri din Arad.\n");
        sb.append("Răspunde în limba ").append("en".equals(locale) ? "engleză (English)" : "română, cu diacritice corecte").append(".\n");
        sb.append("Acum este ").append(now.format(DAY)).append(" (ora Aradului).\n\n");
        sb.append("""
                Reguli:
                - Răspunde scurt și prietenos: 1–3 propoziții.
                - Recomandă doar evenimente din CATALOG, prin id-ul lor în `suggestedEventIds` (cel mult 4). Nu inventa evenimente, \
                ore, prețuri sau locuri; dacă nimic din catalog nu se potrivește, spune asta.
                - Dacă omul cere idei dar lipsește ziua sau cu cine merge (de ex. „cu copiii în weekend” fără zi), pune o singură întrebare \
                de clarificare, fără evenimente, și dă variantele ca `quickReplies` (de ex. „Sâmbătă”, „Duminică”, „Tot weekendul”).
                - `quickReplies`: 0–4 răspunsuri scurte pe care omul le poate atinge, scrise ca de el, în limba răspunsului.
                - Pentru obiectivele din lista de mai jos poți spune cum se ajunge și că au ghid audio în aplicație.
                - Pentru un itinerariu, propune în text un program din evenimentele din catalog pentru ziua cerută.
                - Nu faci plăți și nu rezervi bilete; biletele se iau din pagina evenimentului, în aplicație.
                - Nu răspunde la subiecte fără legătură cu Aradul și cu ce oferă aplicația.

                """);
        sb.append("CATALOG (id | titlu | categorie | când | loc | preț):\n");
        if (catalog.isEmpty()) sb.append("(niciun eveniment în următoarele 14 zile)\n");
        for (Event e : catalog) sb.append(line(e)).append('\n');
        if (!guided.isEmpty()) {
            sb.append("\nOBIECTIVE CU GHID AUDIO (nume | adresă):\n");
            for (Place p : guided) sb.append(p.getName()).append(" | ").append(nz(p.getAddress())).append('\n');
        }
        return sb.toString();
    }

    private static String line(Event e) {
        ZonedDateTime start = e.getStartAt().atZoneSameInstant(ARAD);
        String when = start.format(DAY) + (e.getEndAt() != null ? "–" + e.getEndAt().atZoneSameInstant(ARAD).format(HOUR) : "");
        return e.getId() + " | " + nz(e.getTitle()) + " | " + (e.getCategory() == null ? "" : e.getCategory().name()) + " | " + when
                + " | " + nz(e.getLocationName()) + " | " + price(e);
    }

    private static String price(Event e) {
        if (e.getTicketType() == TicketType.FREE) return "gratuit";
        BigDecimal p = e.getPriceFrom() != null ? e.getPriceFrom() : e.getPriceAdult();
        String amount = p == null ? "" : "de la " + p.stripTrailingZeros().toPlainString() + " lei";
        return e.getTicketType() == TicketType.EXTERNAL ? (amount + " (bilete la organizator)").trim() : (amount + " (bilete în aplicație)").trim();
    }

    private static String nz(String s) {
        return s == null ? "" : s.replace('\n', ' ').replace('|', '/');
    }

    /** JSON-ul lui Claude → răspuns; id-urile care nu sunt în catalog se scot, la fel dublurile și chips-urile goale. */
    static Optional<ChatResponse> parse(String raw, List<Event> catalog) {
        try {
            JsonNode n = JSON.readTree(raw);
            String reply = n.path("reply").asText("").trim();
            if (reply.isEmpty()) return Optional.empty();
            Set<Long> known = catalog.stream().map(Event::getId).collect(Collectors.toSet());
            LinkedHashSet<Long> ids = new LinkedHashSet<>();
            for (JsonNode id : n.path("suggestedEventIds")) {
                if (id.canConvertToLong() && known.contains(id.asLong())) ids.add(id.asLong());
            }
            List<String> chips = new ArrayList<>();
            for (JsonNode c : n.path("quickReplies")) {
                String t = c.asText("").trim();
                if (!t.isEmpty() && t.length() <= 80 && chips.size() < MAX_CHIPS) chips.add(t);
            }
            return Optional.of(new ChatResponse(reply, ids.stream().limit(MAX_SUGGESTED).toList(), chips, false));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
