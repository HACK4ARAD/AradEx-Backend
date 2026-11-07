package aradexperience.service.volunteer;

import aradexperience.dto.volunteer.VolunteerScanRequest;
import aradexperience.entity.community.CommunityAction;
import aradexperience.entity.community.CommunityRegistration;
import aradexperience.entity.users.User;
import aradexperience.entity.volunteer.Volunteer;
import aradexperience.enums.VolunteerActionState;
import aradexperience.enums.VolunteerStatus;
import aradexperience.exceptions.ConflictException;
import aradexperience.exceptions.ForbiddenException;
import aradexperience.exceptions.NotFoundException;
import aradexperience.exceptions.UnauthorizedException;
import aradexperience.repository.community.CommunityActionRepository;
import aradexperience.repository.community.CommunityRegistrationRepository;
import aradexperience.repository.users.UserRepository;
import aradexperience.repository.volunteer.VolunteerRepository;
import aradexperience.repository.volunteer.VolunteerScanRepository;
import aradexperience.security.CurrentUser;
import aradexperience.security.JwtService;
import aradexperience.service.community.CityCommunityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Voluntarul în aplicație (T18, `/vol/**` + `POST /auth/volunteer`): intră cu codul de 6 cifre, vede doar acțiunile lui (cu
 * lacăt până la 30 de minute înainte de start), confirmă participanții după codul din QR — aceeași confirmare idempotentă ca la
 * Primărie ({@link CityCommunityService#confirmByCode}), deci punctele se acordă o singură dată. Chatul e în {@link VolunteerChatService}.
 * Un cont suspendat sau expirat nu mai poate intra (401) și nu mai poate face nimic cu un token vechi (403); la fel un cont
 * dezactivat de Admin (T14, `users.disabled_at`, 403).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VolunteerService {

    static final int RECENT_SCANS = 10;

    private final VolunteerRepository volunteers;
    private final VolunteerScanRepository scans;
    private final UserRepository users;
    private final CommunityActionRepository actions;
    private final CommunityRegistrationRepository registrations;
    private final CityCommunityService cityCommunity;
    private final VolunteerAccess access;
    private final VolunteerChatService chat;
    private final LoginAttempts attempts;
    private final JwtService jwt;
    Clock clock = Clock.systemUTC();

    public record Session(String token, User user) {}

    /** Antetul „Acțiunile tale”. */
    public record Me(Volunteer volunteer, User user, User coordinator, int actions, long scanned, long toScan, int unread) {}

    /** O acțiune alocată + starea ei + câți a confirmat voluntarul acolo. */
    public record ActionView(CommunityAction action, VolunteerActionState state, long confirmedByMe) {}

    /** O confirmare recentă a voluntarului: înscrierea, acțiunea, participantul. */
    public record ScanView(CommunityRegistration registration, CommunityAction action, User participant, String confirmedBy) {}

    @Transactional
    public Session login(String rawCode, String clientKey) {
        attempts.check(clientKey);
        String code = rawCode == null ? "" : rawCode.trim();
        Volunteer v = volunteers.findByLoginCode(code).orElse(null);
        if (v == null) {
            attempts.failed(clientKey);
            log.info("Volunteer login: wrong code");
            throw new UnauthorizedException("Invalid volunteer code");
        }
        attempts.succeeded(clientKey);
        if (v.getStatus() == VolunteerStatus.SUSPENDED) throw new UnauthorizedException("This volunteer account is suspended");
        if (access.expired(v)) throw new UnauthorizedException("This volunteer account has expired");
        User u = users.findById(v.getUserId()).orElseThrow(() -> new UnauthorizedException("Invalid volunteer code"));
        if (u.isDisabled()) throw new ForbiddenException(VolunteerAccess.ACCOUNT_DISABLED);    // T14: contul dezactivat de Admin
        v.setLastLoginAt(now());
        log.info("Volunteer {} logged in", u.getId());
        return new Session(jwt.issue(u.getId(), null, u.getRole()), u);
    }

    public Me me(CurrentUser current) {
        Volunteer v = active(current);
        List<CommunityAction> allocated = allocated(v);
        OffsetDateTime now = now();
        long toScan = allocated.stream().filter(a -> VolunteerRules.state(a, now) != VolunteerActionState.ENDED)
                .mapToLong(a -> Math.max(0, a.getRegisteredCount() - a.getConfirmedCount())).sum();
        long scanned = scans.scansBy(List.of(v.getUserId())).stream().mapToLong(VolunteerScanRepository.ScanCount::getScans).sum();
        return new Me(v, users.findById(v.getUserId()).orElseThrow(), users.findById(v.getCoordinatorId()).orElse(null),
                allocated.size(), scanned, toScan, chat.unreadTotal(v));
    }

    public List<ActionView> actions(CurrentUser current) {
        Volunteer v = active(current);
        List<CommunityAction> allocated = allocated(v);
        Map<Long, Long> mine = allocated.isEmpty() ? Map.of() : scans.scansByActions(v.getUserId(), allocated.stream().map(CommunityAction::getId).toList())
                .stream().collect(Collectors.toMap(VolunteerScanRepository.ActionScanCount::getActionId, VolunteerScanRepository.ActionScanCount::getScans));
        OffsetDateTime now = now();
        return allocated.stream().map(a -> new ActionView(a, VolunteerRules.state(a, now), mine.getOrDefault(a.getId(), 0L))).toList();
    }

    /**
     * Ordinea verificărilor: cod necunoscut (404) → acțiune nealocată (403) → altă acțiune decât cea aleasă (409) → acțiunea
     * încă închisă sau încheiată (403) → confirmarea T11 (idempotentă; 409 dacă participantul și-a anulat înscrierea).
     */
    @Transactional
    public CityCommunityService.ConfirmView scan(VolunteerScanRequest req, CurrentUser current) {
        Volunteer v = active(current);
        String code = req.code().trim().toUpperCase(Locale.ROOT);
        long actionId = registrations.actionIdByCode(code).orElseThrow(() -> new NotFoundException("Unknown code " + code));
        if (!v.getActionIds().contains(actionId)) throw new ForbiddenException("This code is for an action you are not assigned to");
        CommunityAction a = actions.findById(actionId).orElseThrow(() -> NotFoundException.of("Action", actionId));
        if (req.actionId() != null && req.actionId() != actionId) throw new ConflictException("This code is for another action: " + a.getTitle());
        switch (VolunteerRules.state(a, now())) {
            case LOCKED -> throw new ForbiddenException("Scanning opens 30 minutes before the start");
            case ENDED -> throw new ForbiddenException("This action has ended");
            case OPEN -> { /* se poate scana */ }
        }
        CityCommunityService.ConfirmView res = cityCommunity.confirmByCode(code, current);
        log.info("Volunteer {} scanned registration {} ({})", v.getUserId(), res.registration().registration().getId(),
                res.alreadyConfirmed() ? "already confirmed" : "+" + res.pointsAwarded());
        return res;
    }

    public List<ScanView> recentScans(CurrentUser current) {
        Volunteer v = active(current);
        List<CommunityRegistration> regs = scans.recentBy(v.getUserId(), PageRequest.of(0, RECENT_SCANS));
        Map<Long, CommunityAction> acts = actions.findAllById(regs.stream().map(CommunityRegistration::getActionId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(CommunityAction::getId, Function.identity()));
        Map<Long, User> people = users.findAllById(Stream.concat(regs.stream().map(CommunityRegistration::getUserId), Stream.of(v.getUserId()))
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        String me = people.containsKey(v.getUserId()) ? people.get(v.getUserId()).getDisplayName() : null;
        return regs.stream().filter(r -> acts.containsKey(r.getActionId()))
                .map(r -> new ScanView(r, acts.get(r.getActionId()), people.get(r.getUserId()), me)).toList();
    }

    // -------------------------------------------------------------------------------------------------------------

    private Volunteer active(CurrentUser current) {
        return access.active(current);
    }

    private List<CommunityAction> allocated(Volunteer v) {
        return access.allocated(v);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}
