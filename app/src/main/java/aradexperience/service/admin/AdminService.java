package aradexperience.service.admin;

import aradexperience.dto.admin.AdminUserUpdate;
import aradexperience.entity.business.Business;
import aradexperience.entity.community.CommunityAction;
import aradexperience.entity.events.Event;
import aradexperience.entity.offers.Offer;
import aradexperience.entity.users.User;
import aradexperience.enums.ActionStatus;
import aradexperience.enums.ContentStatus;
import aradexperience.enums.Role;
import aradexperience.enums.TicketStatus;
import aradexperience.exceptions.BadRequestException;
import aradexperience.exceptions.NotFoundException;
import aradexperience.repository.admin.AdminContentRepository;
import aradexperience.repository.admin.AdminStatsRepository;
import aradexperience.repository.admin.AdminUserRepository;
import aradexperience.repository.community.CommunityActionRepository;
import aradexperience.repository.events.EventRepository;
import aradexperience.repository.offers.OfferRepository;
import aradexperience.repository.users.UserRepository;
import aradexperience.security.CurrentUser;
import aradexperience.service.points.PointsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * T14 · Administrare (`/admin/**`, doar ADMIN): statisticile platformei, „Conținut & moderare” (orice eveniment, ofertă sau acțiune,
 * indiferent de organizator) și utilizatorii (rol, ajustare de puncte prin PointsService, dezactivare).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

    static final int MAX_PAGE = 100;

    private final AdminStatsRepository stats;
    private final AdminContentRepository content;
    private final AdminUserRepository userSearch;
    private final EventRepository events;
    private final OfferRepository offers;
    private final CommunityActionRepository actions;
    private final UserRepository users;
    private final PointsService points;

    public enum Kind {
        EVENTS, OFFERS, ACTIONS;

        public static Kind parse(String raw) {
            try {
                return valueOf(raw.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new BadRequestException("Unknown content type: " + raw);
            }
        }
    }

    public record Stats(Map<Role, Long> usersByRole, long events, long ticketsSold, long offersRedeemed, long codesUsed,
                        long pointsAwarded, long actions) {}

    /** O pagină de conținut: elementele (Event | Offer | CommunityAction) și firmele lor (pentru nume și „neverificată”). */
    public record Content(Page<?> page, Map<Long, Business> businesses) {}

    public record UserRow(User user, Business business, long tickets) {}

    // ---------------------------------------------------------------- statistici

    public Stats stats() {
        Map<Role, Long> byRole = new EnumMap<>(Role.class);
        for (Role r : Role.values()) byRole.put(r, 0L);
        for (AdminStatsRepository.RoleCount rc : stats.usersByRole()) {
            Arrays.stream(Role.values()).filter(r -> r.name().equals(rc.getRole())).findFirst()
                  .ifPresent(r -> byRole.put(r, rc.getTotal()));
        }
        return new Stats(byRole, stats.publishedEvents(), stats.ticketsSold(), stats.offersRedeemed(), stats.codesUsed(),
                stats.pointsAwarded(), stats.publishedActions());
    }

    // ---------------------------------------------------------------- conținut & moderare

    public Content content(Kind kind, String status, String q, boolean all, int page, int size) {
        String like = like(q);
        OffsetDateTime now = OffsetDateTime.now();
        Page<?> result = switch (kind) {
            case EVENTS -> content.events(status == null ? List.of(ContentStatus.values()) : List.of(contentStatus(status)), like, all, now,
                    pageable(page, size, all ? Sort.by(Sort.Direction.DESC, "startAt") : Sort.by("startAt")));
            case OFFERS -> content.offers(status == null ? List.of(ContentStatus.values()) : List.of(contentStatus(status)), like,
                    pageable(page, size, Sort.by(Sort.Direction.DESC, "id")));
            case ACTIONS -> content.actions(status == null ? List.of(ActionStatus.values()) : List.of(actionStatus(status)), like, all, now,
                    pageable(page, size, all ? Sort.by(Sort.Direction.DESC, "startAt") : Sort.by("startAt")));
        };
        return new Content(result, businessesOf(result.getContent()));
    }

    /** Publică / ascunde orice element. Întoarce elementul și firma lui (sau null). */
    @Transactional
    public Content setStatus(Kind kind, long id, String status, CurrentUser by) {
        Object item = switch (kind) {
            case EVENTS -> {
                Event e = events.findById(id).orElseThrow(() -> NotFoundException.of("Event", id));
                e.setStatus(contentStatus(status));
                yield e;
            }
            case OFFERS -> {
                Offer o = offers.findById(id).orElseThrow(() -> NotFoundException.of("Offer", id));
                o.setStatus(contentStatus(status));
                yield o;
            }
            case ACTIONS -> {
                CommunityAction a = actions.findById(id).orElseThrow(() -> NotFoundException.of("Action", id));
                a.setStatus(actionStatus(status));
                yield a;
            }
        };
        log.info("Admin {} set {} {} to {}", by.id(), kind, id, status);
        return new Content(new PageImpl<>(List.of(item)), businessesOf(List.of(item)));
    }

    // ---------------------------------------------------------------- utilizatori

    public Page<UserRow> users(Role role, String q, int page, int size) {
        Collection<Role> roles = role == null ? List.of(Role.values()) : List.of(role);
        Page<User> found = userSearch.search(roles, like(q),
                pageable(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))));
        List<Long> ids = found.getContent().stream().map(User::getId).toList();
        Map<Long, Business> byOwner = ids.isEmpty() ? Map.of() : userSearch.businessesOf(ids).stream()
                .collect(Collectors.toMap(Business::getOwnerUserId, Function.identity()));
        Map<Long, Long> tickets = ids.isEmpty() ? Map.of() : userSearch.ticketCounts(ids, TicketStatus.CANCELLED).stream()
                .collect(Collectors.toMap(AdminUserRepository.TicketCount::getUserId, AdminUserRepository.TicketCount::getTotal));
        return found.map(u -> new UserRow(u, byOwner.get(u.getId()), tickets.getOrDefault(u.getId(), 0L)));
    }

    @Transactional
    public UserRow updateUser(long id, AdminUserUpdate req, CurrentUser by) {
        User u = users.findById(id).orElseThrow(() -> NotFoundException.of("User", id));
        boolean self = Objects.equals(u.getId(), by.id());

        if (req.role() != null && req.role() != u.getRole()) {
            if (self) throw new BadRequestException("You cannot change your own role");
            if (req.role() == Role.VOLUNTEER) throw new BadRequestException("Volunteer accounts are created by City Hall");
            if (req.role() == Role.BUSINESS || u.getRole() == Role.BUSINESS) {
                throw new BadRequestException("Business accounts are managed in Partner businesses");
            }
            log.info("Admin {} changed role of user {} from {} to {}", by.id(), id, u.getRole(), req.role());
            u.setRole(req.role());
        }
        if (req.disabled() != null) {
            if (req.disabled() && self) throw new BadRequestException("You cannot disable your own account");
            if (req.disabled() && !u.isDisabled()) u.setDisabledAt(OffsetDateTime.now());
            if (!req.disabled()) u.setDisabledAt(null);
            log.info("Admin {} {} user {}", by.id(), req.disabled() ? "disabled" : "enabled", id);
        }
        if (req.pointsAdjust() != null) {
            String note = req.note() == null || req.note().isBlank() ? "Admin adjustment" : req.note().trim();
            points.adjust(u.getId(), req.pointsAdjust(), note); // reîncarcă `u` (același context) cu soldul nou
        }
        Business b = u.getRole() == Role.BUSINESS ? userSearch.businessesOf(List.of(u.getId())).stream().findFirst().orElse(null) : null;
        long tickets = userSearch.ticketCounts(List.of(u.getId()), TicketStatus.CANCELLED).stream()
                .mapToLong(AdminUserRepository.TicketCount::getTotal).sum();
        return new UserRow(u, b, tickets);
    }

    // ---------------------------------------------------------------- ajutătoare

    private Map<Long, Business> businessesOf(List<?> items) {
        List<Long> ids = items.stream().map(i -> switch (i) {
            case Event e -> e.getOrganizerBusinessId();
            case Offer o -> o.getBusinessId();
            default -> null;
        }).filter(Objects::nonNull).distinct().toList();
        return ids.isEmpty() ? Map.of() : content.businesses(ids).stream().collect(Collectors.toMap(Business::getId, Function.identity()));
    }

    static ContentStatus contentStatus(String raw) {
        try {
            return ContentStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Status must be one of DRAFT, PUBLISHED, HIDDEN");
        }
    }

    static ActionStatus actionStatus(String raw) {
        try {
            return ActionStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Status must be one of DRAFT, PUBLISHED, CANCELLED, DONE");
        }
    }

    static String like(String q) {
        String t = q == null ? "" : q.trim().toLowerCase(Locale.ROOT).replace("\\", "").replace("%", "").replace("_", "");
        return "%" + t + "%";
    }

    private static PageRequest pageable(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE), sort);
    }
}
