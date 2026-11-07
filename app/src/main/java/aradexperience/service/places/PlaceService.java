package aradexperience.service.places;

import aradexperience.entity.places.GuidePoint;
import aradexperience.entity.places.Place;
import aradexperience.enums.PlaceType;
import aradexperience.exceptions.BadRequestException;
import aradexperience.exceptions.NotFoundException;
import aradexperience.repository.places.GuidePointRepository;
import aradexperience.repository.places.PlaceRepository;
import aradexperience.util.Geo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static aradexperience.mapper.places.PlaceMapper.splitTags;

/**
 * Citirea obiectivelor (public): listă cu filtre și detaliul cu ghidul. Sunt < 100 de locuri, deci filtrarea și distanța
 * se fac în memorie (docs/API.md: fără paginare).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceService {

    private final PlaceRepository places;
    private final GuidePointRepository guidePoints;

    /** Un obiectiv din listă + statisticile ghidului + distanța (doar cu `near`). */
    public record PlaceItem(Place place, long pointsCount, long seconds, Double distanceKm) {}

    /** Detaliul: obiectivul + punctele de ghid ordonate + distanța (doar cu `near`). */
    public record PlaceDetail(Place place, List<GuidePoint> points, Double distanceKm) {}

    public List<PlaceItem> list(PlaceType type, String q, String near, String tags) {
        double[] from = parseNear(near);
        String needle = normalize(q);
        Set<String> wanted = tags == null ? Set.of() : Arrays.stream(tags.split(","))
                .map(t -> normalize(t)).filter(t -> !t.isEmpty()).collect(Collectors.toSet());
        Map<Long, GuidePointRepository.GuideStats> stats = guidePoints.stats().stream()
                .collect(Collectors.toMap(GuidePointRepository.GuideStats::getPlaceId, Function.identity()));

        var items = places.findAll(Sort.by("id")).stream()
                .filter(p -> type == null || p.getType() == type)
                .filter(p -> wanted.isEmpty() || splitTags(p.getTags()).stream().map(PlaceService::normalize).anyMatch(wanted::contains))
                .filter(p -> needle.isEmpty() || matches(p, needle))
                .map(p -> {
                    var s = stats.get(p.getId());
                    return new PlaceItem(p, s == null ? 0 : s.getCount(), s == null ? 0 : s.getSeconds(), distance(p, from));
                });
        if (from != null) items = items.sorted(Comparator.comparingDouble(PlaceItem::distanceKm));
        return items.toList();
    }

    public PlaceDetail get(long id, String near) {
        double[] from = parseNear(near);
        Place p = places.findById(id).orElseThrow(() -> NotFoundException.of("Place", id));
        return new PlaceDetail(p, guidePoints.findByPlaceIdOrderByPositionAsc(id), distance(p, from));
    }

    private static boolean matches(Place p, String needle) {
        return normalize(p.getName()).contains(needle)
                || normalize(p.getDescription()).contains(needle)
                || normalize(p.getTags()).contains(needle);
    }

    private static Double distance(Place p, double[] from) {
        return from == null ? null : Geo.round1(Geo.distanceKm(from[0], from[1], p.getLat(), p.getLng()));
    }

    /** `near=lat,lng` → [lat, lng]; null dacă lipsește; 400 dacă nu e valid. */
    static double[] parseNear(String near) {
        if (near == null || near.isBlank()) return null;
        String[] parts = near.split(",");
        try {
            if (parts.length != 2) throw new NumberFormatException();
            double lat = Double.parseDouble(parts[0].trim()), lng = Double.parseDouble(parts[1].trim());
            if (Math.abs(lat) > 90 || Math.abs(lng) > 180) throw new NumberFormatException();
            return new double[]{lat, lng};
        } catch (NumberFormatException e) {
            throw new BadRequestException("near must be 'lat,lng', e.g. 46.1866,21.3123");
        }
    }

    /** Litere mici, fără diacritice (ă → a, ș → s), pentru căutare. */
    static String normalize(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }
}
