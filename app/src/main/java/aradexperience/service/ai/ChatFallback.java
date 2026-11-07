package aradexperience.service.ai;

import aradexperience.entity.events.Event;
import aradexperience.enums.TicketType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * T12 · plasa de siguranță a chatului: când Claude nu răspunde (timeout, eroare, fără cheie), un răspuns pregătit din
 * `ai/fallback/answers.json`, ales după cuvintele omului, cu evenimente reale din catalog. Răspunsul e marcat `offline`.
 */
@Component
public class ChatFallback {

    static final ZoneId ARAD = ZoneId.of("Europe/Bucharest");
    static final int MAX_EVENTS = 3;

    record Rule(Pattern match, Pattern unless, List<String> events, String days, Text ro, Text en) {}
    record Text(String reply, List<String> quickReplies) {}
    public record Answer(String reply, List<Long> eventIds, List<String> quickReplies) {}

    private final List<Rule> rules;

    public ChatFallback() {
        try (InputStream in = getClass().getResourceAsStream("/ai/fallback/answers.json")) {
            JsonNode root = new ObjectMapper().readTree(in);
            List<Rule> out = new ArrayList<>();
            for (JsonNode r : root.get("rules")) {
                List<String> events = new ArrayList<>();
                if (r.has("events")) r.get("events").forEach(e -> events.add(e.asText()));
                out.add(new Rule(Pattern.compile(r.get("match").asText()),
                        r.has("unless") ? Pattern.compile(r.get("unless").asText()) : null,
                        events, r.has("days") ? r.get("days").asText() : null, text(r.get("ro")), text(r.get("en"))));
            }
            rules = List.copyOf(out);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing ai/fallback/answers.json", e);
        }
    }

    private static Text text(JsonNode n) {
        List<String> chips = new ArrayList<>();
        n.get("quickReplies").forEach(c -> chips.add(c.asText()));
        return new Text(n.get("reply").asText(), List.copyOf(chips));
    }

    /** `userText` = tot ce a scris omul în conversație; `catalog` = evenimentele din următoarele 14 zile, ordonate după dată. */
    public Answer answer(String userText, String locale, List<Event> catalog, ZonedDateTime now) {
        String n = fold(userText);
        Rule rule = rules.stream()
                .filter(r -> r.match().matcher(n).find() && (r.unless() == null || !r.unless().matcher(n).find()))
                .findFirst().orElse(rules.get(rules.size() - 1));
        Text t = "en".equals(locale) ? rule.en() : rule.ro();
        List<Long> ids = rule.events().isEmpty() ? List.of() : pick(rule, catalog, now);
        return new Answer(t.reply(), ids, t.quickReplies());
    }

    private static List<Long> pick(Rule rule, List<Event> catalog, ZonedDateTime now) {
        LocalDate today = now.toLocalDate();
        List<Long> ids = new ArrayList<>();
        for (Event e : catalog) {
            if (ids.size() == MAX_EVENTS) break;
            LocalDate day = e.getStartAt().atZoneSameInstant(ARAD).toLocalDate();
            if (!inDays(rule.days(), day, today) || !inCategories(rule.events(), e)) continue;
            ids.add(e.getId());
        }
        return ids;
    }

    private static boolean inCategories(List<String> cats, Event e) {
        if (cats.contains("ALL")) return true;
        if (cats.contains("FREE") && e.getTicketType() == TicketType.FREE) return true;
        return e.getCategory() != null && cats.contains(e.getCategory().name());
    }

    private static boolean inDays(String days, LocalDate day, LocalDate today) {
        if (days == null) return true;
        return switch (days) {
            case "WEEKEND" -> !day.isBefore(today) && !day.isAfter(nextSunday(today))
                    && (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY);
            case "SATURDAY" -> day.equals(today.getDayOfWeek() == DayOfWeek.SATURDAY ? today : today.with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.SATURDAY)));
            default -> !day.isAfter(today.plusDays(Long.parseLong(days) - 1));
        };
    }

    private static LocalDate nextSunday(LocalDate today) {
        return today.getDayOfWeek() == DayOfWeek.SUNDAY ? today : today.with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.SUNDAY));
    }

    static String fold(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
