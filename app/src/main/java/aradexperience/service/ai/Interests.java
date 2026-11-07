package aradexperience.service.ai;

import aradexperience.enums.EventCategory;
import aradexperience.enums.PlaceType;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * T13 · interesele omului (texte libere din aplicație: „Istorie”, „Crame & vin”, „Natură”…) traduse în tag-urile locurilor
 * (`places.tags`), tipurile de loc și categoriile de evenimente. Un interes necunoscut devine un tag căutat ca atare.
 */
final class Interests {

    record Rule(String name, Set<String> tags, Set<PlaceType> types, Set<EventCategory> events) {}

    private record Known(Pattern match, Rule rule) {}

    private static final List<Known> KNOWN = List.of(
            known("\\b(istor|history|histor)", "history", Set.of("istorie", "arhitectura"), EnumSet.of(PlaceType.ATTRACTION), EnumSet.of(EventCategory.EXHIBITION)),
            known("\\b(cram|vin|wine|winer|podgor)", "wine", Set.of("vin"), EnumSet.of(PlaceType.WINERY), EnumSet.noneOf(EventCategory.class)),
            known("\\b(muze|museum)", "museums", Set.of("muzee"), EnumSet.of(PlaceType.MUSEUM), EnumSet.of(EventCategory.EXHIBITION)),
            known("\\b(natur|parc|park|outdoor|aer liber)", "nature", Set.of("natura", "in-aer-liber", "munte", "relaxare", "animale"),
                    EnumSet.of(PlaceType.NATURE, PlaceType.PARK), EnumSet.of(EventCategory.OUTDOOR, EventCategory.SPORT)),
            known("\\b(famil|copi|kid|child)", "family", Set.of("familie", "animale", "in-aer-liber"), EnumSet.of(PlaceType.PARK),
                    EnumSet.of(EventCategory.FAMILY, EventCategory.OUTDOOR)),
            known("\\b(gastr|food|mancare|culinar|restaurant|cafe|cafea|coffee)", "food", Set.of("gastronomie", "cafea", "dulciuri"),
                    EnumSet.of(PlaceType.RESTAURANT, PlaceType.CAFE), EnumSet.of(EventCategory.FOOD, EventCategory.FAIR)),
            known("\\b(arta|arte|art|cultur|teatr|theat)", "art", Set.of("arta", "cultura"), EnumSet.of(PlaceType.VENUE, PlaceType.MUSEUM),
                    EnumSet.of(EventCategory.EXHIBITION, EventCategory.THEATRE, EventCategory.CONCERT)),
            known("\\b(arhitect|architect)", "architecture", Set.of("arhitectura", "foto"), EnumSet.noneOf(PlaceType.class), EnumSet.noneOf(EventCategory.class)),
            known("\\b(spiritual|biseric|church|catedral)", "spiritual", Set.of("spiritual"), EnumSet.noneOf(PlaceType.class), EnumSet.noneOf(EventCategory.class)),
            known("\\b(muzic|music|concert)", "music", Set.of("cultura"), EnumSet.of(PlaceType.VENUE), EnumSet.of(EventCategory.CONCERT)));

    private final List<Rule> rules;

    private Interests(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    static Interests of(List<String> raw) {
        List<Rule> out = new ArrayList<>();
        if (raw != null) {
            for (String r : raw) {
                if (r == null || r.isBlank()) continue;
                String f = fold(r);
                boolean matched = false;
                for (Known k : KNOWN) {
                    if (k.match().matcher(f).find()) {
                        matched = true;
                        if (out.stream().noneMatch(x -> x.name().equals(k.rule().name()))) out.add(k.rule());
                    }
                }
                if (!matched) {
                    String tag = f.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
                    if (!tag.isEmpty()) out.add(new Rule(tag, Set.of(tag), Set.of(), Set.of()));
                }
            }
        }
        return new Interests(out);
    }

    private static Known known(String regex, String name, Set<String> tags, Set<PlaceType> types, Set<EventCategory> events) {
        return new Known(Pattern.compile(regex), new Rule(name, tags, types, events));
    }

    /** Numele interesului cunoscut (`history`, `wine`…) pentru un text al omului, sau null dacă nu-l recunoaștem. */
    static String knownName(String raw) {
        String f = fold(raw);
        for (Known k : KNOWN) if (k.match().matcher(f).find()) return k.rule().name();
        return null;
    }

    boolean isEmpty() {
        return rules.isEmpty();
    }

    boolean has(String name) {
        return rules.stream().anyMatch(r -> r.name().equals(name));
    }

    boolean wine() {
        return has("wine");
    }

    boolean food() {
        return has("food");
    }

    /** Câte interese atinge candidatul (după tag-uri, tipul locului sau categoria evenimentului). */
    int matches(PlanItem it) {
        int n = 0;
        for (Rule r : rules) {
            boolean hit = (it.placeType() != null && r.types().contains(it.placeType()))
                    || (it.eventCategory() != null && r.events().contains(it.eventCategory()))
                    || r.tags().stream().anyMatch(it.tags()::contains);
            if (hit) n++;
        }
        return n;
    }

    /** Litere mici, fără diacritice: „Natură” → „natura”. */
    static String fold(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
