package aradexperience.service.volunteer;

import aradexperience.dto.volunteer.VolunteerCreate;
import aradexperience.dto.volunteer.VolunteerUpdate;
import aradexperience.entity.community.CommunityAction;
import aradexperience.entity.users.User;
import aradexperience.entity.volunteer.Volunteer;
import aradexperience.enums.ActionStatus;
import aradexperience.enums.Role;
import aradexperience.enums.VolunteerStatus;
import aradexperience.exceptions.BadRequestException;
import aradexperience.exceptions.NotFoundException;
import aradexperience.repository.community.CommunityActionRepository;
import aradexperience.repository.users.UserRepository;
import aradexperience.repository.volunteer.VolunteerRepository;
import aradexperience.repository.volunteer.VolunteerScanRepository;
import aradexperience.security.CurrentUser;
import aradexperience.util.PhoneNumbers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Voluntarii în panoul Primăriei (T18, `/city/volunteers/**`): conturile (doar Primăria le creează), alocarea pe acțiuni,
 * suspendarea. Primăria nu e în chaturile voluntarilor (decizia din 7 nov). Codul de 6 cifre e unic și „trimis prin SMS” (simulat: îl arată panoul).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CityVolunteerService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final VolunteerRepository volunteers;
    private final VolunteerScanRepository scans;
    private final UserRepository users;
    private final CommunityActionRepository actions;
    Clock clock = Clock.systemUTC();

    /** Un voluntar cu tot ce arată panoul: numele, coordonatorul, scanările, dacă a expirat. */
    public record VolunteerView(Volunteer volunteer, User user, User coordinator, long scans, boolean expired) {}

    public List<VolunteerView> list() {
        return views(volunteers.findAllByOrderByCreatedAtDesc());
    }

    @Transactional
    public VolunteerView create(VolunteerCreate req, CurrentUser by) {
        String phone = PhoneNumbers.normalize(req.phone());
        User coordinator = coordinator(req.coordinatorId() == null ? by.id() : req.coordinatorId());
        Set<Long> actionIds = allocatable(req.actionIds(), Set.of());

        User u = new User();
        u.setDisplayName(req.name().trim());
        u.setRole(Role.VOLUNTEER);
        u.setLocale("ro");
        u = users.save(u);

        Volunteer v = new Volunteer();
        v.setUserId(u.getId());
        v.setPhone(phone);
        v.setEmail(req.email() == null || req.email().isBlank() ? null : req.email().trim().toLowerCase(Locale.ROOT));
        v.setLoginCode(newCode());
        v.setOneTime(req.oneTime());
        v.setCoordinatorId(coordinator.getId());
        v.setCreatedBy(by.id());
        v.setCreatedAt(now());
        v.setActionIds(actionIds);
        volunteers.save(v);
        log.info("Volunteer {} created by user {} ({} actions); access code sent by SMS (simulated)", u.getId(), by.id(), actionIds.size());
        return views(List.of(v)).get(0);
    }

    @Transactional
    public VolunteerView update(long id, VolunteerUpdate req) {
        Volunteer v = volunteers.findForUpdate(id).orElseThrow(() -> NotFoundException.of("Volunteer", id));
        if (req.actionIds() != null) v.setActionIds(allocatable(req.actionIds(), v.getActionIds()));
        if (req.coordinatorId() != null) v.setCoordinatorId(coordinator(req.coordinatorId()).getId());
        if (req.status() != null && req.status() != v.getStatus()) {
            v.setStatus(req.status());
            log.info("Volunteer {} {}", id, req.status() == VolunteerStatus.SUSPENDED ? "suspended" : "reactivated");
        }
        return views(List.of(v)).get(0);
    }

    /** Conturile Primăriei, în ordinea numelui (lista „Coordonator” din formular). */
    public List<User> coordinators() {
        return volunteers.coordinators();
    }

    // -------------------------------------------------------------------------------------------------------------

    List<VolunteerView> views(List<Volunteer> list) {
        if (list.isEmpty()) return List.of();
        Map<Long, User> people = byId(users.findAllById(list.stream().flatMap(v -> Stream.of(v.getUserId(), v.getCoordinatorId())).collect(Collectors.toSet())));
        Map<Long, Long> scanCounts = scans.scansBy(list.stream().map(Volunteer::getUserId).toList()).stream()
                .collect(Collectors.toMap(VolunteerScanRepository.ScanCount::getUserId, VolunteerScanRepository.ScanCount::getScans));
        Map<Long, CommunityAction> acts = byIdActions(actions.findAllById(list.stream().flatMap(v -> v.getActionIds().stream()).collect(Collectors.toSet())));
        OffsetDateTime now = now();
        return list.stream().map(v -> {
            List<CommunityAction> allocated = v.getActionIds().stream().map(acts::get).filter(Objects::nonNull).toList();
            return new VolunteerView(v, people.get(v.getUserId()), people.get(v.getCoordinatorId()),
                    scanCounts.getOrDefault(v.getUserId(), 0L), VolunteerRules.expired(v, allocated, now));
        }).toList();
    }

    /**
     * Acțiunile la care se poate aloca un voluntar: existente și publicate. Cele deja alocate rămân valabile și după ce s-au
     * încheiat (DONE), ca istoricul scanărilor să nu se piardă la o editare.
     */
    private Set<Long> allocatable(Collection<Long> requested, Set<Long> current) {
        if (requested == null || requested.isEmpty()) return new LinkedHashSet<>();
        Set<Long> ids = new LinkedHashSet<>(requested);
        Map<Long, CommunityAction> found = byIdActions(actions.findAllById(ids));
        for (Long id : ids) {
            CommunityAction a = found.get(id);
            if (a == null) throw new BadRequestException("Unknown action " + id);
            boolean keep = current.contains(id) && VolunteerRules.visible(a);
            if (!keep && a.getStatus() != ActionStatus.PUBLISHED) throw new BadRequestException("Action " + id + " is not published");
        }
        return ids;
    }

    private User coordinator(long id) {
        User c = users.findById(id).orElseThrow(() -> new BadRequestException("Unknown coordinator " + id));
        if (c.getRole() != Role.CITY_HALL && c.getRole() != Role.ADMIN) throw new BadRequestException("The coordinator must be a City Hall account");
        return c;
    }

    /** 6 cifre, fără zero la început (se citește ușor din SMS), unic între voluntari. */
    private String newCode() {
        for (int i = 0; i < 50; i++) {
            String code = String.valueOf(100_000 + RANDOM.nextInt(900_000));
            if (!volunteers.existsByLoginCode(code)) return code;
        }
        throw new IllegalStateException("Could not generate a unique volunteer code");
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private static Map<Long, User> byId(List<User> list) {
        return list.stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private static Map<Long, CommunityAction> byIdActions(List<CommunityAction> list) {
        return list.stream().collect(Collectors.toMap(CommunityAction::getId, Function.identity()));
    }
}
