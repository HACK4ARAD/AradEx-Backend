package aradexperience.service.community;

import aradexperience.entity.community.CommunityAction;
import aradexperience.entity.community.CommunityRegistration;
import aradexperience.entity.users.User;
import aradexperience.enums.ActionStatus;
import aradexperience.enums.RegistrationStatus;
import aradexperience.enums.Role;
import aradexperience.exceptions.ConflictException;
import aradexperience.exceptions.NotFoundException;
import aradexperience.repository.community.CommunityActionRepository;
import aradexperience.repository.community.CommunityRegistrationRepository;
import aradexperience.repository.users.UserRepository;
import aradexperience.security.CurrentUser;
import aradexperience.service.points.PointsService;
import aradexperience.util.Codes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static aradexperience.mapper.community.CommunityMapper.firstName;

/**
 * Comunitate, partea publică și a localnicului (T11): lista și detaliul acțiunilor, înscriere (cod `AX-C{id}-XXXXXX`), anulare,
 * înscrierile mele. Reguli: o înscriere per acțiune, capacitate, doar acțiuni publicate care nu s-au încheiat; contoarele
 * acțiunii se schimbă cu rândul acțiunii blocat (`findForUpdate`), deci cereri simultane nu depășesc locurile.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityService {

    static final int PREVIEW = 3;

    private final CommunityActionRepository actions;
    private final CommunityRegistrationRepository registrations;
    private final UserRepository users;

    /** Ceasul aplicației; în teste se înlocuiește. */
    Clock clock = Clock.systemUTC();

    /** O acțiune + datele calculate pentru DTO. */
    public record ActionView(CommunityAction action, List<String> goingPreview, RegistrationView myRegistration) {}

    /** O înscriere + acțiunea ei; `user` doar pentru listele Primăriei; `confirmedBy` = numele celui care a confirmat / respins. */
    public record RegistrationView(CommunityRegistration registration, CommunityAction action, User user, String confirmedBy) {}

    public List<ActionView> list(boolean upcoming, Optional<CurrentUser> current) {
        // „care urmează” păstrează și acțiunile de azi deja încheiate: Primăria le validează toată ziua, iar localnicul își vede QR-ul
        List<CommunityAction> list = upcoming
                ? actions.findByStatusAndEndAtAfterOrderByStartAtAsc(ActionStatus.PUBLISHED, todayStart())
                : actions.findByStatusInOrderByStartAtDesc(List.of(ActionStatus.PUBLISHED, ActionStatus.DONE));
        return views(list, current.map(CurrentUser::id).orElse(null));
    }

    public ActionView get(long id, Optional<CurrentUser> current) {
        CommunityAction a = actions.findById(id).orElseThrow(() -> NotFoundException.of("Action", id));
        boolean staff = current.map(u -> u.is(Role.CITY_HALL) || u.isAdmin()).orElse(false);
        // ciornele (și acțiunile anulate) nu există pentru public
        if (!staff && (a.getStatus() == ActionStatus.DRAFT || a.getStatus() == ActionStatus.CANCELLED)) throw NotFoundException.of("Action", id);
        return views(List.of(a), current.map(CurrentUser::id).orElse(null)).get(0);
    }

    @Transactional
    public RegistrationView register(long actionId, CurrentUser user) {
        CommunityAction a = actions.findForUpdate(actionId).orElseThrow(() -> NotFoundException.of("Action", actionId));
        if (a.getStatus() == ActionStatus.DRAFT) throw NotFoundException.of("Action", actionId);
        if (a.getStatus() != ActionStatus.PUBLISHED) throw new ConflictException("Registrations are closed for this action");
        if (!a.getEndAt().isAfter(now())) throw new ConflictException("This action has already ended");

        Optional<CommunityRegistration> existing = registrations.findByActionIdAndUserId(actionId, user.id());
        if (existing.isPresent()) {
            switch (existing.get().getStatus()) {
                case REGISTERED, CONFIRMED -> throw new ConflictException("You are already registered for this action");
                case NO_SHOW -> throw new ConflictException("Your registration for this action was rejected");
                case CANCELLED -> { /* se reînscrie: același rând și același cod */ }
            }
        }
        if (a.getRegisteredCount() >= a.getCapacity()) throw new ConflictException("This action is full");

        CommunityRegistration r = existing.orElseGet(CommunityRegistration::new);
        if (r.getId() == null) {
            r.setActionId(actionId);
            r.setUserId(user.id());
            r.setCode(Codes.generate("AX-C" + actionId));
        }
        r.setStatus(RegistrationStatus.REGISTERED);
        r.setRegisteredAt(now());
        r.setConfirmedAt(null);
        r.setConfirmedBy(null);
        try {
            r = registrations.saveAndFlush(r);
        } catch (DataIntegrityViolationException e) {
            // UNIQUE(action_id, user_id) sau, foarte rar, același cod generat de două ori → 409, nu 500
            throw new ConflictException("You are already registered for this action");
        }
        a.setRegisteredCount(a.getRegisteredCount() + 1);
        log.info("User {} registered for action {}", user.id(), actionId);
        return new RegistrationView(r, a, null, null);
    }

    /** Idempotent: o înscriere deja anulată → nimic. 404 dacă nu există, 409 dacă a fost deja confirmată sau respinsă. */
    @Transactional
    public void cancel(long actionId, CurrentUser user) {
        CommunityAction a = actions.findForUpdate(actionId).orElseThrow(() -> NotFoundException.of("Action", actionId));
        CommunityRegistration r = registrations.findByActionIdAndUserId(actionId, user.id())
                .orElseThrow(() -> new NotFoundException("You are not registered for this action"));
        switch (r.getStatus()) {
            case CANCELLED -> { return; }
            case CONFIRMED -> throw new ConflictException("Your participation was already confirmed");
            case NO_SHOW -> throw new ConflictException("Your registration for this action was rejected");
            case REGISTERED -> {
                r.setStatus(RegistrationStatus.CANCELLED);
                a.setRegisteredCount(Math.max(0, a.getRegisteredCount() - 1));
                log.info("User {} cancelled registration for action {}", user.id(), actionId);
            }
        }
    }

    /** Înscrierile mele active (fără cele anulate): întâi cele care urmează (cea mai apropiată prima), apoi cele trecute. */
    public List<RegistrationView> myRegistrations(CurrentUser user) {
        List<CommunityRegistration> regs = registrations.findByUserIdAndStatusNot(user.id(), RegistrationStatus.CANCELLED);
        Map<Long, CommunityAction> byId = actions.findAllById(regs.stream().map(CommunityRegistration::getActionId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(CommunityAction::getId, Function.identity()));
        OffsetDateTime now = now();
        Comparator<RegistrationView> byStart = Comparator.comparing(v -> v.action().getStartAt());
        Map<Boolean, List<RegistrationView>> past = regs.stream().filter(r -> byId.containsKey(r.getActionId()))
                .map(r -> new RegistrationView(r, byId.get(r.getActionId()), null, null))
                .collect(Collectors.partitioningBy(v -> v.action().getEndAt().isBefore(now)));
        List<RegistrationView> out = new ArrayList<>(past.get(false).stream().sorted(byStart).toList());   // care urmează: cea mai apropiată prima
        out.addAll(past.get(true).stream().sorted(byStart.reversed()).toList());                          // trecute: cea mai recentă prima
        return out;
    }

    // ------------------------------------------------------------- ajutoare, folosite și de CityCommunityService

    /** goingPreview (prenumele ultimilor 3 înscriși) + înscrierea mea pentru fiecare acțiune. */
    List<ActionView> views(List<CommunityAction> list, Long meId) {
        if (list.isEmpty()) return List.of();
        List<Long> ids = list.stream().map(CommunityAction::getId).toList();
        Map<Long, List<String>> going = new LinkedHashMap<>();
        registrations.going(ids).forEach(g -> {
            List<String> names = going.computeIfAbsent(g.getActionId(), k -> new ArrayList<>());
            if (names.size() < PREVIEW) names.add(firstName(g.getDisplayName()));
        });
        Map<Long, CommunityRegistration> mine = meId == null ? Map.of()
                : registrations.findByUserIdAndActionIdInAndStatusNot(meId, ids, RegistrationStatus.CANCELLED).stream()
                .collect(Collectors.toMap(CommunityRegistration::getActionId, Function.identity()));
        return list.stream().map(a -> new ActionView(a, going.getOrDefault(a.getId(), List.of()),
                mine.containsKey(a.getId()) ? new RegistrationView(mine.get(a.getId()), a, null, null) : null)).toList();
    }

    /** Utilizatorii după id (participanți + cine a confirmat), într-o singură cerere. */
    Map<Long, User> usersById(Collection<Long> ids) {
        Set<Long> clean = new HashSet<>(ids);
        clean.remove(null);
        if (clean.isEmpty()) return Map.of();
        return users.findAllById(clean).stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }

    /** RegistrationView cu participantul și numele celui care a confirmat. */
    List<RegistrationView> withUsers(List<CommunityRegistration> regs, Map<Long, CommunityAction> actionsById) {
        Map<Long, User> u = usersById(regs.stream().flatMap(r -> java.util.stream.Stream.of(r.getUserId(), r.getConfirmedBy())).filter(Objects::nonNull).toList());
        return regs.stream().map(r -> new RegistrationView(r, actionsById.get(r.getActionId()), u.get(r.getUserId()),
                Optional.ofNullable(r.getConfirmedBy()).map(u::get).map(User::getDisplayName).orElse(null))).toList();
    }

    OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    /** Miezul nopții de azi, ora Aradului. */
    OffsetDateTime todayStart() {
        return now().atZoneSameInstant(PointsService.ZONE).toLocalDate().atStartOfDay(PointsService.ZONE).toOffsetDateTime();
    }
}
