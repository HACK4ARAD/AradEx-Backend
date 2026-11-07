package aradexperience.service.community;

import aradexperience.dto.community.ActionCreate;
import aradexperience.dto.community.CityDashboardDto;
import aradexperience.entity.community.CommunityAction;
import aradexperience.entity.community.CommunityRegistration;
import aradexperience.entity.points.PointsTransaction;
import aradexperience.entity.users.User;
import aradexperience.enums.ActionStatus;
import aradexperience.enums.PointsType;
import aradexperience.enums.RegistrationStatus;
import aradexperience.exceptions.BadRequestException;
import aradexperience.exceptions.ConflictException;
import aradexperience.exceptions.NotFoundException;
import aradexperience.repository.community.CommunityActionRepository;
import aradexperience.repository.community.CommunityRegistrationRepository;
import aradexperience.repository.community.CommunityStatsRepository;
import aradexperience.repository.points.PointsTransactionRepository;
import aradexperience.security.CurrentUser;
import aradexperience.service.points.PointsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static aradexperience.mapper.community.CommunityMapper.apply;
import static aradexperience.mapper.community.CommunityMapper.toEntity;

/**
 * Comunitate, panoul Primăriei (T11, `/city/**`): acțiuni (creare / editare / ciorne), înscrișii, confirmare (QR sau din listă)
 * și respingere, panoul cu KPI. Confirmarea acordă `pointsReward` O SINGURĂ DATĂ (idempotent, înscrierea e blocată pe durata
 * tranzacției) și doar prin {@link PointsService}.
 * T18: `/vol/scan` refolosește {@link #confirmByCode} (după ce verifică că acțiunea e alocată voluntarului).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CityCommunityService {

    static final int RECENT = 8;
    static final int DASHBOARD_ACTIONS = 5;

    private final CommunityActionRepository actions;
    private final CommunityRegistrationRepository registrations;
    private final CommunityStatsRepository stats;
    private final PointsTransactionRepository transactions;
    private final PointsService points;
    private final CommunityService community;

    /** Rezultatul unei confirmări: înscrierea + dacă era deja confirmată + câte puncte s-au acordat acum. */
    public record ConfirmView(CommunityService.RegistrationView registration, boolean alreadyConfirmed, int pointsAwarded) {}

    public List<CommunityService.ActionView> actions() {
        return community.views(actions.findAllByOrderByStartAtDesc(), null);
    }

    @Transactional
    public CommunityService.ActionView create(ActionCreate req, CurrentUser user) {
        validateTimes(req);
        if (req.status() != null && req.status() != ActionStatus.PUBLISHED && req.status() != ActionStatus.DRAFT) {
            throw new BadRequestException("A new action can only be PUBLISHED or DRAFT");
        }
        CommunityAction a = actions.save(toEntity(req, user.id()));
        log.info("Action {} created by user {} ({})", a.getId(), user.id(), a.getStatus());
        return new CommunityService.ActionView(a, List.of(), null);
    }

    /** `status` lipsă = neschimbat; capacitatea nu scade sub numărul de înscriși (409). */
    @Transactional
    public CommunityService.ActionView update(long id, ActionCreate req) {
        validateTimes(req);
        CommunityAction a = actions.findForUpdate(id).orElseThrow(() -> NotFoundException.of("Action", id));
        if (req.capacity() < a.getRegisteredCount()) {
            throw new ConflictException("Capacity cannot be lower than the " + a.getRegisteredCount() + " people already registered");
        }
        // o acțiune cu înscriși nu se mai ascunde ca ciornă: codurile lor n-ar mai putea fi confirmate (se anulează explicit, CANCELLED)
        if (req.status() == ActionStatus.DRAFT && a.getStatus() != ActionStatus.DRAFT && a.getRegisteredCount() > 0) {
            throw new ConflictException("An action with " + a.getRegisteredCount() + " people registered cannot go back to draft");
        }
        apply(req, a);
        if (req.status() != null) a.setStatus(req.status());
        log.info("Action {} updated ({})", id, a.getStatus());
        return community.views(List.of(a), null).get(0);
    }

    public List<CommunityService.RegistrationView> registrations(long actionId, RegistrationStatus status) {
        CommunityAction a = actions.findById(actionId).orElseThrow(() -> NotFoundException.of("Action", actionId));
        List<CommunityRegistration> regs = status == null
                ? registrations.findByActionIdOrderByRegisteredAtAsc(actionId)
                : registrations.findByActionIdAndStatusOrderByRegisteredAtAsc(actionId, status);
        return community.withUsers(regs, Map.of(a.getId(), a));
    }

    /** Confirmare după codul din QR (Primăria; T18: voluntarul). 404 cod necunoscut; idempotent. */
    @Transactional
    public ConfirmView confirmByCode(String code, CurrentUser by) {
        String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        long actionId = registrations.actionIdByCode(normalized).orElseThrow(() -> new NotFoundException("Unknown code " + normalized));
        CommunityAction a = lockAction(actionId);
        CommunityRegistration r = registrations.findForUpdateByCode(normalized)
                .orElseThrow(() -> new NotFoundException("Unknown code " + normalized));
        return confirm(r, a, by);
    }

    @Transactional
    public ConfirmView confirmById(long registrationId, CurrentUser by) {
        CommunityAction a = lockAction(registrations.actionIdById(registrationId).orElseThrow(() -> NotFoundException.of("Registration", registrationId)));
        CommunityRegistration r = registrations.findForUpdate(registrationId).orElseThrow(() -> NotFoundException.of("Registration", registrationId));
        return confirm(r, a, by);
    }

    /** NO_SHOW (nu s-a prezentat); idempotent; 409 dacă e deja confirmată (punctele s-au acordat) sau anulată. */
    @Transactional
    public CommunityService.RegistrationView reject(long registrationId, CurrentUser by) {
        CommunityAction a = lockAction(registrations.actionIdById(registrationId).orElseThrow(() -> NotFoundException.of("Registration", registrationId)));
        CommunityRegistration r = registrations.findForUpdate(registrationId).orElseThrow(() -> NotFoundException.of("Registration", registrationId));
        switch (r.getStatus()) {
            case CONFIRMED -> throw new ConflictException("Already confirmed; the points were awarded");
            case CANCELLED -> throw new ConflictException("The registration was cancelled by the participant");
            case NO_SHOW -> { /* deja respinsă */ }
            case REGISTERED -> {
                r.setStatus(RegistrationStatus.NO_SHOW);
                r.setConfirmedAt(community.now());     // momentul deciziei
                r.setConfirmedBy(by.id());
                a.setRegisteredCount(Math.max(0, a.getRegisteredCount() - 1));   // locul se eliberează
                log.info("Registration {} rejected by user {}", r.getId(), by.id());
            }
        }
        return community.withUsers(List.of(r), Map.of(a.getId(), a)).get(0);
    }

    public CityDashboardDto dashboard() {
        OffsetDateTime now = community.now();
        List<CommunityAction> upcoming = actions.findByStatusAndEndAtAfterOrderByStartAtAsc(ActionStatus.PUBLISHED, community.todayStart())
                .stream().limit(DASHBOARD_ACTIONS).toList();
        Map<Long, Long> pending = upcoming.isEmpty() ? Map.of() : registrations.pending(upcoming.stream().map(CommunityAction::getId).toList())
                .stream().collect(Collectors.toMap(CommunityRegistrationRepository.PendingRow::getActionId, CommunityRegistrationRepository.PendingRow::getPending));

        List<CommunityRegistration> recent = registrations.recent(PageRequest.of(0, RECENT));
        Map<Long, CommunityAction> recentActions = actions.findAllById(recent.stream().map(CommunityRegistration::getActionId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(CommunityAction::getId, Function.identity()));
        Map<Long, User> people = community.usersById(recent.stream().flatMap(r -> Stream.of(r.getUserId(), r.getConfirmedBy())).filter(Objects::nonNull).toList());

        OffsetDateTime monthStart = now.atZoneSameInstant(PointsService.ZONE).withDayOfMonth(1).toLocalDate().atStartOfDay(PointsService.ZONE).toOffsetDateTime();
        return new CityDashboardDto(
                stats.upcomingEvents(now),
                stats.upcomingRegistrations(community.todayStart()),
                transactions.awardedBetween(monthStart, monthStart.atZoneSameInstant(PointsService.ZONE).plusMonths(1).toOffsetDateTime()),
                stats.pendingValidations(community.todayStart()),
                stats.volunteers(),
                upcoming.stream().map(a -> new CityDashboardDto.ActionProgress(a.getId(), a.getTitle(), a.getStartAt(), a.getRegisteredCount(),
                        a.getCapacity(), pending.getOrDefault(a.getId(), 0L), a.getImageUrl())).toList(),
                recent.stream().filter(r -> recentActions.containsKey(r.getActionId())).map(r -> {
                    CommunityAction a = recentActions.get(r.getActionId());
                    User who = people.get(r.getUserId());
                    User by = r.getConfirmedBy() == null ? null : people.get(r.getConfirmedBy());
                    return new CityDashboardDto.Activity(r.getStatus(), who == null ? "—" : who.getDisplayName(), a.getTitle(),
                            r.getStatus() == RegistrationStatus.CONFIRMED ? a.getPointsReward() : 0,
                            by == null ? null : by.getDisplayName(), r.getConfirmedAt() != null ? r.getConfirmedAt() : r.getRegisteredAt());
                }).toList());
    }

    // -------------------------------------------------------------------------------------------------------------

    private CommunityAction lockAction(long actionId) {
        return actions.findForUpdate(actionId).orElseThrow(() -> NotFoundException.of("Action", actionId));
    }

    private ConfirmView confirm(CommunityRegistration r, CommunityAction a, CurrentUser by) {
        if (r.getStatus() == RegistrationStatus.CONFIRMED) {
            return new ConfirmView(community.withUsers(List.of(r), Map.of(a.getId(), a)).get(0), true, 0);
        }
        if (r.getStatus() == RegistrationStatus.CANCELLED) throw new ConflictException("The registration was cancelled by the participant");
        if (a.getStatus() != ActionStatus.PUBLISHED && a.getStatus() != ActionStatus.DONE) {
            throw new ConflictException("This action is not published");
        }
        if (r.getStatus() == RegistrationStatus.NO_SHOW) a.setRegisteredCount(a.getRegisteredCount() + 1);   // respins din greșeală, dar e aici
        r.setStatus(RegistrationStatus.CONFIRMED);
        r.setConfirmedAt(community.now());
        r.setConfirmedBy(by.id());
        a.setConfirmedCount(a.getConfirmedCount() + 1);
        points.award(r.getUserId(), a.getPointsReward(), PointsType.EARN_ACTION, PointsTransaction.REF_ACTION, a.getId(), a.getTitle());
        log.info("Registration {} confirmed by user {} (+{})", r.getId(), by.id(), a.getPointsReward());
        return new ConfirmView(community.withUsers(List.of(r), Map.of(a.getId(), a)).get(0), false, a.getPointsReward());
    }

    private static void validateTimes(ActionCreate req) {
        if (!req.endAt().isAfter(req.startAt())) throw new BadRequestException("endAt must be after startAt");
    }
}
