package aradexperience.service.places;

import aradexperience.dto.places.GuidePointRequest;
import aradexperience.dto.places.PlaceRequest;
import aradexperience.entity.places.GuidePoint;
import aradexperience.entity.places.Place;
import aradexperience.exceptions.ConflictException;
import aradexperience.exceptions.NotFoundException;
import aradexperience.repository.places.GuidePointRepository;
import aradexperience.repository.places.PlaceRepository;
import aradexperience.service.places.PlaceService.PlaceDetail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static aradexperience.mapper.places.PlaceMapper.apply;

/** „Obiective & ghiduri” (W25): Primăria și Adminul editează obiectivele și punctele de ghid. Rolurile le verifică SecurityConfig (`/city/**`). */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CityPlaceService {

    private static final int MAX_SLUG = 160;

    private final PlaceRepository places;
    private final GuidePointRepository guidePoints;

    public PlaceDetail create(PlaceRequest req) {
        String slug = req.slug() != null && !req.slug().isBlank() ? req.slug() : null;
        if (slug != null && places.existsBySlug(slug)) throw new ConflictException("Slug '" + slug + "' is already used");
        if (slug == null) slug = uniqueSlug(slugify(req.name()));
        Place p = new Place();
        apply(p, req, slug);
        p = saveUnique(p);
        log.info("Place {} created", p.getId());
        return new PlaceDetail(p, List.of(), null);
    }

    public PlaceDetail update(long id, PlaceRequest req) {
        Place p = place(id);
        String slug = req.slug() == null || req.slug().isBlank() ? p.getSlug() : req.slug();
        if (!slug.equals(p.getSlug()) && places.existsBySlugAndIdNot(slug, id)) throw new ConflictException("Slug '" + slug + "' is already used");
        apply(p, req, slug);
        p = saveUnique(p);
        log.info("Place {} updated", id);
        return new PlaceDetail(p, guidePoints.findByPlaceIdOrderByPositionAsc(id), null);
    }

    public void delete(long id) {
        Place p = place(id);
        try {
            places.delete(p);
            places.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Place " + id + " is used by events; move or delete them first");
        }
        log.info("Place {} deleted", id);
    }

    public GuidePoint addGuidePoint(long placeId, GuidePointRequest req) {
        place(placeId);
        int position = req.position() != null ? req.position() : guidePoints.maxPosition(placeId) + 1;
        if (guidePoints.findByPlaceIdAndPosition(placeId, position).isPresent()) {
            throw new ConflictException("Position " + position + " is already used in place " + placeId);
        }
        GuidePoint g = new GuidePoint();
        apply(g, req, placeId, position);
        try {
            return guidePoints.saveAndFlush(g);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Position " + position + " is already used in place " + placeId);
        }
    }

    /** O poziție ocupată de alt punct face schimb cu el (mută sus / jos), fără să încalce UNIQUE (place_id, position). */
    public GuidePoint updateGuidePoint(long placeId, long gpId, GuidePointRequest req) {
        GuidePoint g = guidePoints.findByIdAndPlaceId(gpId, placeId).orElseThrow(() -> NotFoundException.of("Guide point", gpId));
        int oldPosition = g.getPosition();
        int newPosition = req.position() != null ? req.position() : oldPosition;
        if (newPosition != oldPosition) {
            var other = guidePoints.findByPlaceIdAndPosition(placeId, newPosition);
            if (other.isPresent()) {
                GuidePoint o = other.get();
                o.setPosition(-1);
                guidePoints.saveAndFlush(o);
                apply(g, req, placeId, newPosition);
                guidePoints.saveAndFlush(g);
                o.setPosition(oldPosition);
                guidePoints.saveAndFlush(o);
                return g;
            }
        }
        apply(g, req, placeId, newPosition);
        return guidePoints.saveAndFlush(g);
    }

    public void deleteGuidePoint(long placeId, long gpId) {
        GuidePoint g = guidePoints.findByIdAndPlaceId(gpId, placeId).orElseThrow(() -> NotFoundException.of("Guide point", gpId));
        guidePoints.delete(g);
    }

    private Place place(long id) {
        return places.findById(id).orElseThrow(() -> NotFoundException.of("Place", id));
    }

    private Place saveUnique(Place p) {
        try {
            return places.saveAndFlush(p);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Slug '" + p.getSlug() + "' is already used");
        }
    }

    /** „Castelul Macea” → `castelul-macea`, „-2”, „-3” … dacă există. */
    private String uniqueSlug(String base) {
        String slug = base;
        for (int i = 2; places.existsBySlug(slug); i++) {
            String suffix = "-" + i;
            slug = base.substring(0, Math.min(base.length(), MAX_SLUG - suffix.length())) + suffix;
        }
        return slug;
    }

    static String slugify(String name) {
        String s = PlaceService.normalize(name).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        if (s.isEmpty()) s = "obiectiv";
        return s.length() > MAX_SLUG ? s.substring(0, MAX_SLUG).replaceAll("-+$", "") : s;
    }
}
