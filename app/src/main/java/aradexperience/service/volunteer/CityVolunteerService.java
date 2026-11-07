package aradexperience.service.volunteer;

import aradexperience.dto.volunteer.MessageRequest;
import aradexperience.dto.volunteer.VolunteerCreate;
import aradexperience.dto.volunteer.VolunteerUpdate;
import aradexperience.entity.community.CommunityAction;
import aradexperience.entity.users.User;
import aradexperience.entity.volunteer.Volunteer;
import aradexperience.entity.volunteer.VolunteerMessage;
import aradexperience.enums.ActionStatus;
import aradexperience.enums.Role;
import aradexperience.enums.VolunteerStatus;
import aradexperience.exceptions.BadRequestException;
import aradexperience.exceptions.NotFoundException;
import aradexperience.repository.community.CommunityActionRepository;
import aradexperience.repository.users.UserRepository;
import aradexperience.repository.volunteer.VolunteerMessageRepository;
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
 * suspendarea, conversațiile. Codul de 6 cifre e unic și „trimis prin SMS” (simulat: îl arată panoul).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CityVolunteerService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final VolunteerRepository volunteers;
    private final VolunteerMessageRepository messages;
    private final VolunteerScanRepository scans;
    private final UserRepository users;
    private final CommunityActionRepository actions;
    Clock clock = Clock.systemUTC();

    /** Un voluntar cu tot ce arată panoul: numele, coordonatorul, scanările, mesajele necitite, ultimul mesaj, dacă a expirat. */
    public record VolunteerView(Volunteer volunteer, User user, User coordinator, long scans, int unread, MessageView lastMessage,
                                boolean expired) {}

    /** Un mesaj + numele autorului. */
    public record MessageView(VolunteerMessage message, String authorName) {}

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

    /** Conversația cu un voluntar; deschiderea ei marchează mesajele lui ca citite de Primărie. */
    @Transactional
    public List<MessageView> messages(long id) {
        Volunteer v = volunteers.findById(id).orElseThrow(() -> NotFoundException.of("Volunteer", id));
        messages.markRead(v.getUserId(), false, now());
        return withAuthors(messages.findByVolunteerUserIdOrderByCreatedAtAscIdAsc(v.getUserId()));
    }

    @Transactional
    public MessageView send(long id, MessageRequest req, CurrentUser by) {
        Volunteer v = volunteers.findById(id).orElseThrow(() -> NotFoundException.of("Volunteer", id));
        VolunteerMessage m = new VolunteerMessage();
        m.setVolunteerUserId(v.getUserId());
        m.setFromCity(true);
        m.setAuthorId(by.id());
        m.setText(req.text().trim());
        m.setCreatedAt(now());
        m = messages.save(m);
        messages.markRead(v.getUserId(), false, now());   // cine răspunde a citit conversația
        log.info("Message {} to volunteer {} from user {}", m.getId(), id, by.id());
        return new MessageView(m, users.findById(by.id()).map(User::getDisplayName).orElse(null));
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
        Map<Long, Long> unread = messages.unreadByCity().stream()
                .collect(Collectors.toMap(VolunteerMessageRepository.UnreadRow::getVolunteerId, VolunteerMessageRepository.UnreadRow::getUnread));
        Map<Long, VolunteerMessage> last = messages.lastPerVolunteer().stream()
                .collect(Collectors.toMap(VolunteerMessage::getVolunteerUserId, Function.identity()));
        Map<Long, User> authors = byId(users.findAllById(last.values().stream().map(VolunteerMessage::getAuthorId).filter(Objects::nonNull).collect(Collectors.toSet())));
        Map<Long, CommunityAction> acts = byIdActions(actions.findAllById(list.stream().flatMap(v -> v.getActionIds().stream()).collect(Collectors.toSet())));
        OffsetDateTime now = now();
        return list.stream().map(v -> {
            VolunteerMessage m = last.get(v.getUserId());
            List<CommunityAction> allocated = v.getActionIds().stream().map(acts::get).filter(Objects::nonNull).toList();
            return new VolunteerView(v, people.get(v.getUserId()), people.get(v.getCoordinatorId()),
                    scanCounts.getOrDefault(v.getUserId(), 0L), unread.getOrDefault(v.getUserId(), 0L).intValue(),
                    m == null ? null : new MessageView(m, authors.containsKey(m.getAuthorId()) ? authors.get(m.getAuthorId()).getDisplayName() : null),
                    VolunteerRules.expired(v, allocated, now));
        }).toList();
    }

    List<MessageView> withAuthors(List<VolunteerMessage> list) {
        Map<Long, User> authors = byId(users.findAllById(list.stream().map(VolunteerMessage::getAuthorId).collect(Collectors.toSet())));
        return list.stream().map(m -> new MessageView(m, authors.containsKey(m.getAuthorId()) ? authors.get(m.getAuthorId()).getDisplayName() : null)).toList();
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
