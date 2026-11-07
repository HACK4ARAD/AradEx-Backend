package aradexperience.service.business;

import aradexperience.dto.business.AnnouncementCreate;
import aradexperience.dto.business.BusinessActivityDto;
import aradexperience.dto.business.BusinessUpdate;
import aradexperience.dto.events.EventCreate;
import aradexperience.entity.business.Announcement;
import aradexperience.entity.business.Business;
import aradexperience.entity.events.Event;
import aradexperience.entity.offers.Offer;
import aradexperience.entity.offers.OfferRedemption;
import aradexperience.enums.BusinessActivityType;
import aradexperience.enums.ContentStatus;
import aradexperience.enums.OrganizerType;
import aradexperience.enums.RedemptionStatus;
import aradexperience.enums.TicketType;
import aradexperience.exceptions.BadRequestException;
import aradexperience.exceptions.ForbiddenException;
import aradexperience.exceptions.NotFoundException;
import aradexperience.repository.business.AnnouncementRepository;
import aradexperience.repository.business.BusinessRepository;
import aradexperience.repository.events.EventRepository;
import aradexperience.repository.offers.OfferRedemptionRepository;
import aradexperience.repository.points.PointsTransactionRepository;
import aradexperience.repository.offers.OfferRepository;
import aradexperience.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static aradexperience.mapper.business.BusinessMapper.apply;
import static aradexperience.mapper.business.BusinessMapper.toAnnouncement;
import static aradexperience.mapper.events.EventMapper.apply;

/**
 * Rolul Firmă locală (T09): pagina publică, profilul, evenimentele și anunțurile firmei, statisticile din panou.
 * Reguli: o firmă editează/șterge doar conținutul ei (403 altfel; ADMIN poate orice); firma neverificată poate crea,
 * dar conținutul rămâne `DRAFT` până la verificare (T14).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BusinessService {

    static final String NO_BUSINESS = "No business for this account";
    private static final ZoneId ARAD = ZoneId.of("Europe/Bucharest");

    private final BusinessRepository businesses;
    private final EventRepository events;
    private final AnnouncementRepository announcements;
    private final OfferRepository offers;
    private final OfferRedemptionRepository redemptions;
    private final PointsTransactionRepository pointsTransactions;

    /** Pagina publică: firma, ofertele active (T10), evenimentele ei viitoare publicate, anunțurile publicate. */
    public record Page(Business business, List<Offer> offers, List<Event> events, List<Announcement> announcements) {}

    /** Panoul: firma, codurile folosite (T10: suma `used_count` a ofertelor), plățile cu puncte la ofertele firmei (T11), evenimentele viitoare. */
    public record Dashboard(Business business, long codesUsed, long pointsPayments, long upcomingEvents) {}

    @Transactional
    public Page page(long businessId) {
        if (businesses.incrementViews(businessId) == 0) {
            throw NotFoundException.of("Business", businessId);
        }
        Business b = businesses.findById(businessId).orElseThrow(() -> NotFoundException.of("Business", businessId));
        return new Page(b, offers.findAvailableByBusiness(businessId, ContentStatus.PUBLISHED, LocalDate.now(ARAD)),
                events.findUpcomingByBusiness(businessId, ContentStatus.PUBLISHED, now()),
                announcements.findByBusinessIdAndStatusOrderByCreatedAtDesc(businessId, ContentStatus.PUBLISHED));
    }

    public Dashboard dashboard(CurrentUser user) {
        Business b = mine(user);
        return new Dashboard(b, offers.sumUsedByBusiness(b.getId()), pointsTransactions.countOfferPaymentsByBusiness(b.getId()), events.countUpcomingByBusiness(b.getId(), now()));
    }

    @Transactional
    public Business updateMe(CurrentUser user, BusinessUpdate req) {
        Business b = mine(user);
        apply(req, b);
        log.info("Business {} updated its profile", b.getId());
        return b;
    }

    public List<Event> myEvents(CurrentUser user) {
        return events.findByOrganizerBusinessIdOrderByStartAtDesc(mine(user).getId());
    }

    @Transactional
    public Event createEvent(CurrentUser user, EventCreate req) {
        Business b = mine(user);
        validate(req);
        Event e = new Event();
        apply(req, e);
        setLocation(e, req, b);
        e.setOrganizerType(OrganizerType.BUSINESS);
        e.setOrganizerBusinessId(b.getId());
        e.setOrganizerName(b.getName());
        e.setStatus(b.isVerified() ? ContentStatus.PUBLISHED : ContentStatus.DRAFT);
        e.setCreatedBy(user.id());
        setPrices(e);
        Event saved = events.save(e);
        log.info("Business {} created event {} ({})", b.getId(), saved.getId(), saved.getStatus());
        return saved;
    }

    @Transactional
    public Event updateEvent(CurrentUser user, long eventId, EventCreate req) {
        Event e = ownedEvent(user, eventId);
        validate(req);
        apply(req, e);
        setLocation(e, req, null);
        setPrices(e);
        log.info("Event {} updated by user {}", eventId, user.id());
        return e;
    }

    @Transactional
    public void deleteEvent(CurrentUser user, long eventId) {
        Event e = ownedEvent(user, eventId);
        events.delete(e);
        log.info("Event {} deleted by user {}", eventId, user.id());
    }

    public List<Announcement> myAnnouncements(CurrentUser user) {
        return announcements.findByBusinessIdOrderByCreatedAtDesc(mine(user).getId());
    }

    @Transactional
    public Announcement createAnnouncement(CurrentUser user, AnnouncementCreate req) {
        Business b = mine(user);
        ContentStatus status = b.isVerified() ? ContentStatus.PUBLISHED : ContentStatus.DRAFT;
        Announcement a = announcements.save(toAnnouncement(req, b.getId(), status, now()));
        log.info("Business {} created announcement {} ({})", b.getId(), a.getId(), status);
        return a;
    }

    @Transactional
    public void deleteAnnouncement(CurrentUser user, long announcementId) {
        Announcement a = announcements.findById(announcementId)
                .orElseThrow(() -> NotFoundException.of("Announcement", announcementId));
        if (!user.isAdmin() && !Objects.equals(a.getBusinessId(), mine(user).getId())) {
            throw new ForbiddenException("Announcement " + announcementId + " belongs to another business");
        }
        announcements.delete(a);
        log.info("Announcement {} deleted by user {}", announcementId, user.id());
    }

    /**
     * Ultimele 20 de întâmplări ale firmei: coduri folosite la casă, oferte revendicate (T10) și plăți cu puncte (T11).
     * Biletele (T07) își adaugă aici rândurile lor.
     */
    public BusinessActivityDto activity(CurrentUser user) {
        Business b = mine(user);
        List<OfferRedemption> recent = redemptions.findByBusiness(b.getId(), null, PageRequest.of(0, 20));
        Map<Long, Offer> byId = offers.findAllById(recent.stream().map(OfferRedemption::getOfferId).distinct().toList()).stream()
                .collect(Collectors.toMap(Offer::getId, Function.identity()));
        return new BusinessActivityDto(recent.stream().map(r -> {
            Offer o = byId.get(r.getOfferId());
            String title = o == null ? r.getCode() : o.getTitle();
            return r.getStatus() == RedemptionStatus.USED
                    ? new BusinessActivityDto.Item(BusinessActivityType.CODE_USED, "Cod folosit: " + title, r.getUsedAt())
                    : r.getPointsSpent() > 0
                    ? new BusinessActivityDto.Item(BusinessActivityType.POINTS_PAYMENT, "Plată cu " + r.getPointsSpent() + " de puncte: " + title, r.getIssuedAt())
                    : new BusinessActivityDto.Item(BusinessActivityType.OFFER_REDEEMED, "Ofertă revendicată: " + title, r.getIssuedAt());
        }).toList());
    }

    // ---------------------------------------------------------------------------------------------------------------

    private Business mine(CurrentUser user) {
        return businesses.findByOwnerUserId(user.id()).orElseThrow(() -> new NotFoundException(NO_BUSINESS));
    }

    /** Evenimentul există (404) și e al firmei utilizatorului (403); ADMIN trece. */
    private Event ownedEvent(CurrentUser user, long eventId) {
        Event e = events.findById(eventId).orElseThrow(() -> NotFoundException.of("Event", eventId));
        if (!user.isAdmin() && !Objects.equals(e.getOrganizerBusinessId(), mine(user).getId())) {
            throw new ForbiddenException("Event " + eventId + " belongs to another organizer");
        }
        return e;
    }

    private static void validate(EventCreate req) {
        if (req.endAt() != null && !req.endAt().isAfter(req.startAt())) {
            throw new BadRequestException("endAt must be after startAt");
        }
        if ((req.lat() == null) != (req.lng() == null)) {
            throw new BadRequestException("lat and lng must be sent together");
        }
        if (req.ticketType() == TicketType.IN_APP && req.priceAdult() == null) {
            throw new BadRequestException("priceAdult is required for IN_APP tickets");
        }
        if (req.ticketType() == TicketType.EXTERNAL && (req.externalUrl() == null || req.externalUrl().isBlank())) {
            throw new BadRequestException("externalUrl is required for EXTERNAL tickets");
        }
    }

    /**
     * Locația din cerere, dacă are coordonate; altfel, la creare, sediul firmei (`business`); la editare rămâne cea existentă.
     */
    private static void setLocation(Event e, EventCreate req, Business business) {
        if (req.lat() != null) {
            e.setLat(req.lat());
            e.setLng(req.lng());
            e.setLocationName(firstNonBlank(req.locationName(), business == null ? e.getLocationName() : business.getName()));
            e.setAddress(firstNonBlank(req.address(), null));
            return;
        }
        if (business != null) {
            if (business.getLat() == null || business.getLng() == null) {
                throw new BadRequestException("Event location is required (the business has no coordinates)");
            }
            e.setLat(business.getLat());
            e.setLng(business.getLng());
            e.setLocationName(firstNonBlank(req.locationName(), business.getName()));
            e.setAddress(firstNonBlank(req.address(), business.getAddress()));
            return;
        }
        if (req.locationName() != null && !req.locationName().isBlank()) e.setLocationName(req.locationName().trim());
        if (req.address() != null && !req.address().isBlank()) e.setAddress(req.address().trim());
    }

    /** `priceFrom` pentru card: 0 la FREE (fără prețuri), minimul adult/copil la IN_APP, prețul adult (opțional) la EXTERNAL. */
    private static void setPrices(Event e) {
        switch (e.getTicketType()) {
            case FREE -> {
                e.setPriceAdult(null);
                e.setPriceChild(null);
                e.setPriceFrom(BigDecimal.ZERO);
            }
            case IN_APP -> e.setPriceFrom(Stream.of(e.getPriceAdult(), e.getPriceChild())
                    .filter(Objects::nonNull).min(BigDecimal::compareTo).orElse(null));
            case EXTERNAL -> e.setPriceFrom(e.getPriceAdult());
        }
        if (e.getTicketType() != TicketType.EXTERNAL) e.setExternalUrl(null);
    }

    private static String firstNonBlank(String value, String fallback) {
        return value != null && !value.isBlank() ? value.trim() : fallback;
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now();
    }
}
