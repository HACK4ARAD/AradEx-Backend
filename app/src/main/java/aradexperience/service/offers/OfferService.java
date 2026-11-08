package aradexperience.service.offers;

import aradexperience.dto.offers.OfferCreate;
import aradexperience.entity.business.Business;
import aradexperience.entity.offers.Offer;
import aradexperience.entity.offers.OfferRedemption;
import aradexperience.enums.ContentStatus;
import aradexperience.enums.OfferCategory;
import aradexperience.exceptions.BadRequestException;
import aradexperience.exceptions.ForbiddenException;
import aradexperience.exceptions.NotFoundException;
import aradexperience.repository.business.BusinessRepository;
import aradexperience.repository.offers.OfferRedemptionRepository;
import aradexperience.repository.offers.OfferRepository;
import aradexperience.security.CurrentUser;
import aradexperience.util.Geo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static aradexperience.mapper.offers.OfferMapper.apply;

/**
 * Ofertele (T10): lista publică cu filtre, detaliul, ofertele firmei (creare / editare / pauză / ștergere).
 * Reguli: publice doar cele `PUBLISHED` și valabile azi (ora Aradului); firma neverificată → `DRAFT`; o firmă nu atinge ofertele
 * altei firme (403; ADMIN poate orice); o ofertă cu coduri emise nu se șterge, se ascunde (`HIDDEN`) ca istoricul codurilor să rămână.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OfferService {

    static final ZoneId ARAD = ZoneId.of("Europe/Bucharest");
    static final String NO_BUSINESS = "No business for this account";

    private final OfferRepository offers;
    private final OfferRedemptionRepository redemptions;
    private final BusinessRepository businesses;

    /** O ofertă cu firma ei, distanța (dacă s-a cerut `near`) și ultimul meu cod (dacă sunt logat). */
    public record OfferView(Offer offer, Business business, Double distanceKm, OfferRedemption mine) {}

    /** Filtrele din `GET /offers`. */
    public record Filter(OfferCategory category, Boolean localProduct, Boolean points, String near, String q) {}

    public List<OfferView> list(Filter f) {
        double[] near = parseNear(f.near());
        List<Offer> available = offers.findAvailable(today());
        Map<Long, Business> byId = businessesOf(available);
        String q = f.q() == null || f.q().isBlank() ? null : f.q().trim().toLowerCase(Locale.ROOT);
        List<OfferView> out = available.stream()
                .filter(o -> f.category() == null || o.getCategory() == f.category())
                .filter(o -> !Boolean.TRUE.equals(f.localProduct()) || o.isLocalProduct() || o.getCategory() == OfferCategory.LOCAL)
                .filter(o -> !Boolean.TRUE.equals(f.points()) || o.getPointsPrice() != null)
                .filter(o -> q == null || matches(o, byId.get(o.getBusinessId()), q))
                .map(o -> new OfferView(o, byId.get(o.getBusinessId()), distance(byId.get(o.getBusinessId()), near), null))
                .toList();
        if (near != null) {
            // V19: ofertele promovate rămân primele și când lista e sortată după distanță
            return out.stream().sorted(Comparator.<OfferView, Boolean>comparing(v -> !v.offer().isFeatured())
                    .thenComparing(v -> v.distanceKm() == null ? Double.MAX_VALUE : v.distanceKm())).toList();
        }
        return out;
    }

    /** Detaliul public: doar oferte publicate (404 altfel); +1 afișare. `user` poate fi null (vizitator fără cont). */
    @Transactional
    public OfferView get(long id, CurrentUser user) {
        Offer o = offers.findById(id).filter(x -> x.getStatus() == ContentStatus.PUBLISHED)
                .orElseThrow(() -> NotFoundException.of("Offer", id));
        offers.incrementViews(id);
        o = offers.findById(id).orElseThrow(() -> NotFoundException.of("Offer", id));
        OfferRedemption mine = user == null ? null : redemptions.findFirstByOfferIdAndUserIdOrderByIssuedAtDesc(id, user.id()).orElse(null);
        return new OfferView(o, businesses.findById(o.getBusinessId()).orElse(null), null, mine);
    }

    // --- panoul firmei ------------------------------------------------------------------------------------------

    public List<OfferView> businessOffers(CurrentUser user) {
        Business b = mine(user);
        return offers.findByBusinessIdAndStatusNotOrderByIdDesc(b.getId(), ContentStatus.HIDDEN).stream()
                .map(o -> new OfferView(o, b, null, null)).toList();
    }

    @Transactional
    public OfferView create(CurrentUser user, OfferCreate req) {
        Business b = mine(user);
        Offer o = new Offer();
        o.setBusinessId(b.getId());
        applyRequest(req, o, b);
        Offer saved = offers.save(o);
        log.info("Business {} created offer {} ({})", b.getId(), saved.getId(), saved.getStatus());
        return new OfferView(saved, b, null, null);
    }

    @Transactional
    public OfferView update(CurrentUser user, long id, OfferCreate req) {
        Offer o = owned(user, id);
        Business b = businesses.findById(o.getBusinessId()).orElseThrow(() -> NotFoundException.of("Business", o.getBusinessId()));
        applyRequest(req, o, b);
        log.info("Offer {} updated by user {} ({})", id, user.id(), o.getStatus());
        return new OfferView(o, b, null, null);
    }

    @Transactional
    public void delete(CurrentUser user, long id) {
        Offer o = owned(user, id);
        if (redemptions.existsByOfferId(id)) {
            o.setStatus(ContentStatus.HIDDEN);
            log.info("Offer {} hidden by user {} (has redemptions)", id, user.id());
        } else {
            offers.delete(o);
            log.info("Offer {} deleted by user {}", id, user.id());
        }
    }

    // ---------------------------------------------------------------------------------------------------------------

    private void applyRequest(OfferCreate req, Offer o, Business b) {
        if (req.status() == ContentStatus.HIDDEN) {
            throw new BadRequestException("status must be DRAFT or PUBLISHED");
        }
        LocalDate from = req.validFrom() != null ? req.validFrom() : (o.getValidFrom() != null ? o.getValidFrom() : today());
        if (req.validUntil() != null && req.validUntil().isBefore(from)) {
            throw new BadRequestException("validUntil must not be before validFrom");
        }
        apply(req, o);
        o.setValidFrom(from);
        o.setCategory(req.category() != null ? req.category() : OfferCategory.defaultFor(b.getCategory()));
        ContentStatus wanted = req.status() != null ? req.status() : ContentStatus.PUBLISHED;
        o.setStatus(b.isVerified() ? wanted : ContentStatus.DRAFT);
    }

    private Business mine(CurrentUser user) {
        return businesses.findByOwnerUserId(user.id()).orElseThrow(() -> new NotFoundException(NO_BUSINESS));
    }

    /** Oferta există și nu e ștearsă (404) și e a firmei mele (403); ADMIN trece. */
    private Offer owned(CurrentUser user, long id) {
        Offer o = offers.findById(id).filter(x -> x.getStatus() != ContentStatus.HIDDEN).orElseThrow(() -> NotFoundException.of("Offer", id));
        if (!user.isAdmin() && !Objects.equals(o.getBusinessId(), mine(user).getId())) {
            throw new ForbiddenException("Offer " + id + " belongs to another business");
        }
        return o;
    }

    private Map<Long, Business> businessesOf(List<Offer> list) {
        return businesses.findAllById(list.stream().map(Offer::getBusinessId).distinct().toList()).stream()
                .collect(Collectors.toMap(Business::getId, Function.identity()));
    }

    private static boolean matches(Offer o, Business b, String q) {
        return o.getTitle().toLowerCase(Locale.ROOT).contains(q) || o.getDescription().toLowerCase(Locale.ROOT).contains(q)
                || (b != null && b.getName().toLowerCase(Locale.ROOT).contains(q));
    }

    /** "46.17,21.31" → [lat, lng]; 400 la format greșit. */
    static double[] parseNear(String near) {
        if (near == null || near.isBlank()) return null;
        String[] p = near.split(",");
        try {
            double lat = Double.parseDouble(p[0].trim()), lng = Double.parseDouble(p[1].trim());
            if (p.length != 2 || Math.abs(lat) > 90 || Math.abs(lng) > 180) throw new NumberFormatException();
            return new double[]{lat, lng};
        } catch (RuntimeException e) {
            throw new BadRequestException("near must be lat,lng");
        }
    }

    private static Double distance(Business b, double[] near) {
        if (near == null || b == null || b.getLat() == null || b.getLng() == null) return null;
        return Geo.round1(Geo.distanceKm(near[0], near[1], b.getLat(), b.getLng()));
    }

    static LocalDate today() {
        return LocalDate.now(ARAD);
    }
}
