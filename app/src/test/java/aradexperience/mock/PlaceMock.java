package aradexperience.mock;

import aradexperience.dto.places.GuidePointRequest;
import aradexperience.dto.places.PlaceRequest;
import aradexperience.entity.places.GuidePoint;
import aradexperience.entity.places.Place;
import aradexperience.enums.PlaceType;
import aradexperience.repository.places.GuidePointRepository;

import java.math.BigDecimal;
import java.util.List;

/** Date de test pentru PlaceService / CityPlaceService / controllerele de locuri (coordonate reale din V3_1). */
public final class PlaceMock {

    private PlaceMock() {}

    public static Place place(long id, String name, PlaceType type, double lat, double lng, String tags) {
        Place p = new Place();
        p.setId(id);
        p.setName(name);
        p.setSlug(name.toLowerCase().replace(' ', '-'));
        p.setType(type);
        p.setDescription("Descrierea pentru " + name);
        p.setAddress("Arad");
        p.setLat(lat);
        p.setLng(lng);
        p.setVisitMinutes(30);
        p.setTags(tags);
        p.setRating(new BigDecimal("4.6"));
        return p;
    }

    public static Place cetatea() {
        Place p = place(1L, "Cetatea Aradului", PlaceType.ATTRACTION, 46.1711, 21.3319, "istorie,familie,foto");
        p.setExteriorOnly(true);
        return p;
    }

    public static Place palatulCultural() {
        return place(2L, "Palatul Cultural", PlaceType.MUSEUM, 46.1757, 21.3171, "muzee,istorie,arta");
    }

    public static Place padureaCeala() {
        return place(10L, "Pădurea Ceala", PlaceType.NATURE, 46.1675, 21.2425, "natura,familie,sport");
    }

    public static List<Place> all() {
        return List.of(cetatea(), palatulCultural(), padureaCeala());
    }

    public static GuidePoint point(long id, long placeId, int position, Integer durationSec) {
        GuidePoint g = new GuidePoint();
        g.setId(id);
        g.setPlaceId(placeId);
        g.setPosition(position);
        g.setTitle("Punctul " + position);
        g.setText("Text de citit cu voce tare pentru punctul " + position + ".");
        g.setDurationSec(durationSec);
        return g;
    }

    public static GuidePointRepository.GuideStats stats(long placeId, long count, long seconds) {
        return new GuidePointRepository.GuideStats() {
            @Override public Long getPlaceId() { return placeId; }
            @Override public Long getCount() { return count; }
            @Override public Long getSeconds() { return seconds; }
        };
    }

    public static PlaceRequest request(String name, String slug) {
        return new PlaceRequest(name, slug, PlaceType.MUSEUM, "Muzeu nou în Arad", "Str. X 1, Arad", 46.18, 21.31, null, null,
                "Marți–duminică 10–18", 45, List.of("Muzee", "istorie", "muzee"), 4.5, null, false);
    }

    public static GuidePointRequest pointRequest(Integer position) {
        return new GuidePointRequest(position, "Intrarea", "Bun venit la muzeu. Aici începe turul.", null, null, null, null, null);
    }
}
