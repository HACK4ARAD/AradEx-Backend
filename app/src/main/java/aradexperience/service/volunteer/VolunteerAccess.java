package aradexperience.service.volunteer;

import aradexperience.entity.community.CommunityAction;
import aradexperience.entity.users.User;
import aradexperience.entity.volunteer.Volunteer;
import aradexperience.enums.VolunteerStatus;
import aradexperience.exceptions.ForbiddenException;
import aradexperience.repository.community.CommunityActionRepository;
import aradexperience.repository.users.UserRepository;
import aradexperience.repository.volunteer.VolunteerRepository;
import aradexperience.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Cine poate folosi aplicația de voluntar (scanare, chat): contul de voluntar al tokenului, activ (nu suspendat de Primărie, nu
 * expirat, nu dezactivat de Admin — T14). Altfel 403, și cu un token încă valid. Plus acțiunile lui alocate, vizibile.
 */
@Component
@RequiredArgsConstructor
public class VolunteerAccess {

    /** Același mesaj ca la conturile dezactivate de Admin (T14, `AuthService`). */
    static final String ACCOUNT_DISABLED = "Account disabled";

    private final VolunteerRepository volunteers;
    private final UserRepository users;
    private final CommunityActionRepository actions;
    Clock clock = Clock.systemUTC();

    public Volunteer active(CurrentUser current) {
        Volunteer v = volunteers.findById(current.id()).orElseThrow(() -> new ForbiddenException("Not a volunteer account"));
        if (users.findById(current.id()).map(User::isDisabled).orElse(true)) throw new ForbiddenException(ACCOUNT_DISABLED);
        if (v.getStatus() == VolunteerStatus.SUSPENDED) throw new ForbiddenException("This volunteer account is suspended");
        if (expired(v)) throw new ForbiddenException("This volunteer account has expired");
        return v;
    }

    boolean expired(Volunteer v) {
        return VolunteerRules.expired(v, actions.findAllById(v.getActionIds()), now());
    }

    /** Acțiunile alocate care se văd (publicate sau încheiate), în ordinea începerii. */
    List<CommunityAction> allocated(Volunteer v) {
        return actions.findAllById(v.getActionIds()).stream().filter(VolunteerRules::visible)
                .sorted(Comparator.comparing(CommunityAction::getStartAt)).toList();
    }

    OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}
