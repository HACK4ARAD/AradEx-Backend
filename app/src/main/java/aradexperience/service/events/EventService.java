package aradexperience.service.events;

import aradexperience.config.AppProps;
import aradexperience.dto.events.EventCreate;
import aradexperience.entity.events.Event;
import aradexperience.entity.events.SavedEvent;
import aradexperience.enums.EventCategory;
import aradexperience.enums.ContentStatus;
import aradexperience.enums.OrganizerType;
import aradexperience.enums.Role;
import aradexperience.enums.TicketType;
import aradexperience.exceptions.BadRequestException;
import aradexperience.exceptions.ForbiddenException;
import aradexperience.exceptions.NotFoundException;
import aradexperience.mapper.events.EventMapper;
import aradexperience.repository.events.EventRepository;
import aradexperience.repository.events.SavedEventRepository;
import aradexperience.security.CurrentUser;
import aradexperience.util.Geo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Evenimente (T06): listă cu filtre, detaliu, salvate, creare de către Primărie/Admin. Metodele `*ForOrganizer` sunt pentru T09
 * (firmele își publică evenimentele prin `BusinessController`, fără să modifice acest pachet).
 * Lista e mică (zeci de evenimente): se citesc candidații din DB (status + nu s-au terminat) și filtrele fine se aplică aici.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {

    public static final ZoneId ZONE = ZoneId.of("Europe/Bucharest");
    static final int MAX_PAGE_SIZE = 100;
    static final String CITY_NAME = "Primăria Arad";
    static final String PLATFORM_NAME = "AradExperience";

    private final EventRepository events;
    private final SavedEventRepository saved;
    private final AppProps props;

    /** Ceasul pentru „acum” (testele îl fixează). */
    private Clock clock = Clock.system(ZONE);

    void setClock(Clock clock) {
        this.clock = clock;
    }

    /* ------------------------------------------------------------------ citire */

    /** Validează parametrii din GET /events și construiește filtrul. Statusurile non-publice doar pentru Primărie/Admin. */
    public EventQuery query(String from, String to, String category, String near, Double radiusKm, Long organizerBusinessId,
                            String organizerType, String ticketType, Boolean hot, String q, String sort, String status,
                            int page, int size, Optional<CurrentUser> user) {
        OffsetDateTime fromTs = from == null || from.isBlank() ? OffsetDateTime.now(clock) : day(from, "from").atStartOfDay(ZONE).toOffsetDateTime();
        OffsetDateTime toTs = to == null || to.isBlank() ? null : day(to, "to").plusDays(1).atStartOfDay(ZONE).toOffsetDateTime();
        EventQuery.LatLng point = near == null || near.isBlank() ? null : latLng(near);
        boolean byDistance = "distance".equalsIgnoreCase(sort);
        if (byDistance && point == null) throw new BadRequestException("sort=distance requires near=lat,lng");
        if (radiusKm != null && radiusKm <= 0) throw new BadRequestException("radiusKm must be positive");
        if (page < 0 || size < 1) throw new BadRequestException("page >= 0 and size >= 1");
        return new EventQuery(fromTs, toTs, enums(category, EventCategory.class, "category"), point, radiusKm, organizerBusinessId,
                enums(organizerType, OrganizerType.class, "organizerType"), enums(ticketType, TicketType.class, "ticketType"),
                Boolean.TRUE.equals(hot), normalize(q), byDistance, statuses(status, user), page, Math.min(size, MAX_PAGE_SIZE));
    }

    public Page<EventView> list(EventQuery q, Optional<CurrentUser> user) {
        List<Event> candidates = events.findCandidates(q.statuses(), q.from());
        List<EventView> rows = candidates.stream()
                .filter(e -> q.toExclusive() == null || e.getStartAt().isBefore(q.toExclusive()))
                .filter(e -> q.categories().isEmpty() || q.categories().contains(e.getCategory()))
                .filter(e -> q.organizerTypes().isEmpty() || q.organizerTypes().contains(e.getOrganizerType()))
                .filter(e -> q.ticketTypes().isEmpty() || q.ticketTypes().contains(e.getTicketType()))
                .filter(e -> q.organizerBusinessId() == null || q.organizerBusinessId().equals(e.getOrganizerBusinessId()))
                .filter(e -> !q.hotOnly() || isHot(e))
                .filter(e -> q.q() == null || matches(e, q.q()))
                .map(e -> new EventView(e, distance(e, q.near()), null, isHot(e), null))
                .filter(v -> q.radiusKm() == null || v.distanceKm() == null || v.distanceKm() <= q.radiusKm())
                .sorted(q.byDistance() ? Comparator.comparing(EventView::distanceKm) : Comparator.comparing((EventView v) -> v.event().getStartAt()))
                .toList();
        int fromIdx = Math.min(q.page() * q.size(), rows.size());
        List<EventView> pageRows = withSaved(rows.subList(fromIdx, Math.min(fromIdx + q.size(), rows.size())), user);
        return new PageImpl<>(pageRows, PageRequest.of(q.page(), q.size()), rows.size());
    }

    /** Detaliul: ciornele și evenimentele ascunse le văd doar creatorul, Primăria și Adminul (pentru ceilalți: 404). */
    public EventView get(long id, String near, Optional<CurrentUser> user) {
        Event e = events.findById(id).filter(ev -> visibleTo(ev, user)).orElseThrow(() -> NotFoundException.of("Event", id));
        EventQuery.LatLng point = near == null || near.isBlank() ? null : latLng(near);
        Boolean isSaved = user.map(u -> saved.existsByUserIdAndEventId(u.id(), id)).orElse(null);
        return new EventView(e, distance(e, point), isSaved, isHot(e), imageCredit(e));
    }

    public List<EventView> savedBy(CurrentUser user, String near) {
        Set<Long> ids = saved.findEventIds(user.id());
        if (ids.isEmpty()) return List.of();
        EventQuery.LatLng point = near == null || near.isBlank() ? null : latLng(near);
        return events.findByIdInAndStatusOrderByStartAtAsc(ids, ContentStatus.PUBLISHED).stream()
                .map(e -> new EventView(e, distance(e, point), true, isHot(e), null)).toList();
    }

    /* ------------------------------------------------------------------ salvate */

    @Transactional
    public void save(long eventId, CurrentUser user) {
        Event e = events.findById(eventId).filter(ev -> ev.getStatus() == ContentStatus.PUBLISHED).orElseThrow(() -> NotFoundException.of("Event", eventId));
        if (saved.existsByUserIdAndEventId(user.id(), eventId)) return;
        try {
            saved.saveAndFlush(new SavedEvent(user.id(), eventId));
        } catch (DataIntegrityViolationException alreadySaved) {
            return; // două cereri simultane: a doua nu mai numără
        }
        e.setSavesCount(e.getSavesCount() + 1);
    }

    @Transactional
    public void unsave(long eventId, CurrentUser user) {
        if (saved.deleteOne(user.id(), eventId) > 0) {
            events.findById(eventId).ifPresent(e -> e.setSavesCount(Math.max(0, e.getSavesCount() - 1)));
        }
    }

    /* ------------------------------------------------------------------ scriere */

    /** POST /events: Primăria → organizator „Primăria Arad”, Adminul → platforma. */
    @Transactional
    public EventView create(EventCreate req, CurrentUser user) {
        boolean admin = user.isAdmin();
        Event e = createForOrganizer(req, admin ? OrganizerType.ADMIN : OrganizerType.CITY, admin ? PLATFORM_NAME : CITY_NAME, null, user.id());
        return new EventView(e, null, false, isHot(e), imageCredit(e));
    }

    /** Pentru T09: firma își publică evenimentul (`organizerType = BUSINESS`). Statusul îl decide apelantul prin `req.status()`. */
    @Transactional
    public Event createForOrganizer(EventCreate req, OrganizerType type, String organizerName, Long businessId, Long createdBy) {
        Event e = new Event();
        e.setOrganizerType(type);
        e.setOrganizerName(organizerName);
        e.setOrganizerBusinessId(businessId);
        e.setCreatedBy(createdBy);
        e.setDemo(true); // tot ce se creează în aplicație în timpul demo-ului e conținut demonstrativ
        fill(e, req);
        Event saved = events.save(e);
        log.info("Event {} created by user {} ({})", saved.getId(), createdBy, type);
        return saved;
    }

    /** PUT /events/{id}: creatorul sau Adminul. */
    @Transactional
    public EventView update(long id, EventCreate req, CurrentUser user) {
        Event e = events.findById(id).orElseThrow(() -> NotFoundException.of("Event", id));
        requireOwner(e, user);
        fill(e, req);
        return new EventView(e, null, null, isHot(e), imageCredit(e));
    }

    /** Pentru T09: doar evenimentele firmei `businessId`. */
    @Transactional
    public Event updateForOrganizer(long id, EventCreate req, long businessId) {
        Event e = events.findById(id).orElseThrow(() -> NotFoundException.of("Event", id));
        if (!Long.valueOf(businessId).equals(e.getOrganizerBusinessId())) throw new ForbiddenException("Not your event");
        fill(e, req);
        return e;
    }

    /** DELETE /events/{id}: îl ascunde (biletele și salvările rămân valide în istoric). */
    @Transactional
    public void delete(long id, CurrentUser user) {
        Event e = events.findById(id).orElseThrow(() -> NotFoundException.of("Event", id));
        requireOwner(e, user);
        e.setStatus(ContentStatus.HIDDEN);
        log.info("Event {} hidden by user {}", id, user.id());
    }

    /** Pentru T09. */
    @Transactional
    public void deleteForOrganizer(long id, long businessId) {
        Event e = events.findById(id).orElseThrow(() -> NotFoundException.of("Event", id));
        if (!Long.valueOf(businessId).equals(e.getOrganizerBusinessId())) throw new ForbiddenException("Not your event");
        e.setStatus(ContentStatus.HIDDEN);
    }

    /* ------------------------------------------------------------------ reguli */

    public boolean isHot(Event e) {
        return e.getParticipantsCount() >= props.events().hotParticipants() || e.getSavesCount() >= props.events().hotSaves();
    }

    private void fill(Event e, EventCreate req) {
        if (req.endAt() != null && req.endAt().isBefore(req.startAt())) throw new BadRequestException("endAt must be after startAt");
        ContentStatus status = req.status() == null ? ContentStatus.PUBLISHED : req.status();
        if (status == ContentStatus.HIDDEN) throw new BadRequestException("status must be DRAFT or PUBLISHED");
        String oldImage = e.getImageUrl();
        EventMapper.apply(req, e);
        e.setStatus(status);
        setLocation(e, req, oldImage);
        switch (req.ticketType()) {
            case IN_APP -> {
                if (req.priceAdult() == null) throw new BadRequestException("priceAdult is required for IN_APP tickets");
                e.setPriceFrom(req.priceChild() == null ? req.priceAdult() : req.priceAdult().min(req.priceChild()));
            }
            case EXTERNAL -> {
                e.setPriceFrom(req.priceFrom());
                e.setPriceAdult(null);
                e.setPriceChild(null);
            }
            case FREE -> {
                e.setPriceFrom(null);
                e.setPriceAdult(null);
                e.setPriceChild(null);
            }
        }
        if (e.getCapacity() != null && e.getCapacity() < e.getParticipantsCount()) {
            throw new BadRequestException("capacity is lower than the tickets already issued (" + e.getParticipantsCount() + ")");
        }
    }

    /** Locul: din obiectivul `placeId` (nume, adresă, coordonate, poză dacă nu are) sau din câmpurile cererii. */
    private void setLocation(Event e, EventCreate req, String oldImage) {
        if (req.placeId() != null) {
            EventRepository.PlaceRef p = events.findPlace(req.placeId()).orElseThrow(() -> new BadRequestException("Unknown placeId " + req.placeId()));
            e.setPlaceId(req.placeId());
            e.setLocationName(req.locationName() != null && !req.locationName().isBlank() ? req.locationName().trim() : p.getName());
            e.setAddress(req.address() != null && !req.address().isBlank() ? req.address().trim() : p.getAddress());
            e.setLat(req.lat() != null ? req.lat() : p.getLat());
            e.setLng(req.lng() != null ? req.lng() : p.getLng());
            if (e.getImageUrl() == null) e.setImageUrl(oldImage != null ? oldImage : p.getImageUrl());
            return;
        }
        if (req.locationName() == null || req.locationName().isBlank() || req.lat() == null || req.lng() == null) {
            throw new BadRequestException("Give a placeId or locationName + lat + lng");
        }
        e.setPlaceId(null);
        e.setLocationName(req.locationName().trim());
        e.setAddress(req.address() == null || req.address().isBlank() ? null : req.address().trim());
        e.setLat(req.lat());
        e.setLng(req.lng());
        if (e.getImageUrl() == null) e.setImageUrl(oldImage);
    }

    private String imageCredit(Event e) {
        if (e.getPlaceId() == null || e.getImageUrl() == null) return null;
        return events.findPlace(e.getPlaceId())
                .filter(p -> e.getImageUrl().equals(p.getImageUrl()))
                .map(EventRepository.PlaceRef::getImageCredit).orElse(null);
    }

    private static void requireOwner(Event e, CurrentUser user) {
        if (!user.isAdmin() && !user.id().equals(e.getCreatedBy())) throw new ForbiddenException("Only the creator or an admin can change this event");
    }

    private static boolean visibleTo(Event e, Optional<CurrentUser> user) {
        if (e.getStatus() == ContentStatus.PUBLISHED) return true;
        return user.map(u -> u.isAdmin() || u.is(Role.CITY_HALL) || u.id().equals(e.getCreatedBy())).orElse(false);
    }

    private List<EventView> withSaved(List<EventView> rows, Optional<CurrentUser> user) {
        if (user.isEmpty() || rows.isEmpty()) return rows;
        Set<Long> ids = saved.findEventIdsIn(user.get().id(), rows.stream().map(v -> v.event().getId()).toList());
        return rows.stream().map(v -> new EventView(v.event(), v.distanceKm(), ids.contains(v.event().getId()), v.hot(), v.imageCredit())).toList();
    }

    private static Double distance(Event e, EventQuery.LatLng p) {
        return p == null ? null : Geo.round1(Geo.distanceKm(p.lat(), p.lng(), e.getLat(), e.getLng()));
    }

    private static boolean matches(Event e, String q) {
        String text = normalize(e.getTitle() + " " + e.getLocationName() + " " + e.getOrganizerName() + " " + e.getCategory());
        return Arrays.stream(q.split("\\s+")).allMatch(text::contains);
    }

    /** Minuscule fără diacritice: „Pădurea” → „padurea”, ca utilizatorul să poată căuta fără diacritice. */
    static String normalize(String s) {
        if (s == null || s.isBlank()) return null;
        return Normalizer.normalize(s.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private static LocalDate day(String s, String name) {
        try {
            return LocalDate.parse(s.trim());
        } catch (DateTimeParseException ex) {
            throw new BadRequestException(name + " must be a date YYYY-MM-DD");
        }
    }

    private static EventQuery.LatLng latLng(String near) {
        String[] parts = near.split(",");
        try {
            if (parts.length != 2) throw new NumberFormatException();
            double lat = Double.parseDouble(parts[0].trim()), lng = Double.parseDouble(parts[1].trim());
            if (Math.abs(lat) > 90 || Math.abs(lng) > 180) throw new NumberFormatException();
            return new EventQuery.LatLng(lat, lng);
        } catch (NumberFormatException ex) {
            throw new BadRequestException("near must be lat,lng");
        }
    }

    private static <E extends Enum<E>> Set<E> enums(String csv, Class<E> type, String name) {
        if (csv == null || csv.isBlank()) return EnumSet.noneOf(type);
        try {
            return Stream.of(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                    .map(s -> Enum.valueOf(type, s.toUpperCase(Locale.ROOT)))
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(type)));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unknown " + name + ": " + csv);
        }
    }

    private static Set<ContentStatus> statuses(String status, Optional<CurrentUser> user) {
        boolean staff = user.map(u -> u.isAdmin() || u.is(Role.CITY_HALL)).orElse(false);
        if (!staff || status == null || status.isBlank()) return EnumSet.of(ContentStatus.PUBLISHED);
        if ("ALL".equalsIgnoreCase(status)) return EnumSet.allOf(ContentStatus.class);
        return enums(status, ContentStatus.class, "status");
    }
}
