package aradexperience.mapper.places;

import aradexperience.dto.places.GuidePointDto;
import aradexperience.dto.places.GuidePointRequest;
import aradexperience.dto.places.PlaceDto;
import aradexperience.dto.places.PlaceRequest;
import aradexperience.dto.places.PlaceSummary;
import aradexperience.entity.places.GuidePoint;
import aradexperience.entity.places.Place;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Place / GuidePoint ↔ DTO-urile din `dto/places`. Metodele sunt explicit `static` (import static). */
@UtilityClass
public class PlaceMapper {

    public static PlaceSummary toSummary(Place p, long pointsCount, long seconds, Double distanceKm) {
        return new PlaceSummary(p.getId(), p.getName(), p.getType(), p.getImageUrl(), p.getLat(), p.getLng(), p.getVisitMinutes(),
                splitTags(p.getTags()), distanceKm, (int) pointsCount, guideMinutes(pointsCount, seconds), p.isExteriorOnly());
    }

    /** @param points punctele de ghid, deja ordonate după `position` */
    public static PlaceDto toDto(Place p, List<GuidePoint> points, Double distanceKm) {
        long seconds = points.stream().mapToLong(g -> g.getDurationSec() == null ? 0 : g.getDurationSec()).sum();
        return new PlaceDto(p.getId(), p.getName(), p.getType(), p.getImageUrl(), p.getLat(), p.getLng(), p.getVisitMinutes(),
                splitTags(p.getTags()), distanceKm, points.size(), guideMinutes(points.size(), seconds), p.isExteriorOnly(),
                p.getSlug(), p.getDescription(), p.getAddress(), p.getOpeningHours(),
                p.getRating() == null ? null : p.getRating().doubleValue(), p.getParkingHint(), p.getImageCredit(),
                points.stream().map(PlaceMapper::toGuidePointDto).toList());
    }

    public static GuidePointDto toGuidePointDto(GuidePoint g) {
        return new GuidePointDto(g.getId(), g.getPosition(), g.getTitle(), g.getText(), g.getImageUrl(), g.getAudioUrl(),
                g.getLat(), g.getLng(), g.getDurationSec());
    }

    /** Copiază câmpurile editabile din cerere în entitate (creare și modificare). Slug-ul îl alege service-ul. */
    public static void apply(Place p, PlaceRequest r, String slug) {
        p.setName(r.name().trim());
        p.setSlug(slug);
        p.setType(r.type());
        p.setDescription(r.description().trim());
        p.setAddress(blankToNull(r.address()));
        p.setLat(r.lat());
        p.setLng(r.lng());
        p.setImageUrl(blankToNull(r.imageUrl()));
        p.setImageCredit(blankToNull(r.imageCredit()));
        p.setOpeningHours(blankToNull(r.openingHours()));
        p.setVisitMinutes(r.visitMinutes() == null ? 60 : r.visitMinutes());
        p.setTags(joinTags(r.tags()));
        p.setRating(r.rating() == null ? null : BigDecimal.valueOf(r.rating()).setScale(1, RoundingMode.HALF_UP));
        p.setParkingHint(blankToNull(r.parkingHint()));
        p.setExteriorOnly(r.exteriorOnly());
    }

    public static void apply(GuidePoint g, GuidePointRequest r, long placeId, int position) {
        g.setPlaceId(placeId);
        g.setPosition(position);
        g.setTitle(r.title().trim());
        g.setText(r.text().trim());
        g.setImageUrl(blankToNull(r.imageUrl()));
        g.setAudioUrl(blankToNull(r.audioUrl()));
        g.setLat(r.lat());
        g.setLng(r.lng());
        g.setDurationSec(r.durationSec() != null ? r.durationSec() : readingSeconds(r.text()));
    }

    /** Minutele ghidului: suma duratelor, rotunjită în sus; fără durate, un minut pe punct. */
    public static int guideMinutes(long pointsCount, long seconds) {
        if (pointsCount == 0) return 0;
        return seconds > 0 ? (int) Math.ceil(seconds / 60.0) : (int) pointsCount;
    }

    /** Durata de citit cu voce tare: ~140 de cuvinte pe minut, minimum 20 s. */
    public static int readingSeconds(String text) {
        int words = text == null || text.isBlank() ? 0 : text.trim().split("\\s+").length;
        return Math.max(20, (int) Math.round(words * 60 / 140.0));
    }

    public static List<String> splitTags(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /** Etichete normalizate: litere mici, fără virgule și dubluri; null dacă nu e niciuna. */
    public static String joinTags(List<String> tags) {
        if (tags == null) return null;
        var clean = new LinkedHashSet<String>();
        for (String t : tags) {
            if (t == null) continue;
            String s = t.replace(",", " ").trim().toLowerCase(Locale.ROOT);
            if (!s.isEmpty()) clean.add(s);
        }
        return clean.isEmpty() ? null : String.join(",", clean);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
