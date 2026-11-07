package aradexperience.mapper.volunteer;

import aradexperience.dto.community.RegistrationDto;
import aradexperience.dto.volunteer.CoordinatorDto;
import aradexperience.dto.volunteer.VolunteerActionDto;
import aradexperience.dto.volunteer.VolunteerDto;
import aradexperience.dto.volunteer.VolunteerMeDto;
import aradexperience.dto.volunteer.VolunteerMessageDto;
import aradexperience.entity.community.CommunityAction;
import aradexperience.entity.users.User;
import aradexperience.entity.volunteer.Volunteer;
import aradexperience.mapper.community.CommunityMapper;
import aradexperience.service.volunteer.CityVolunteerService;
import aradexperience.service.volunteer.VolunteerRules;
import aradexperience.service.volunteer.VolunteerService;
import lombok.experimental.UtilityClass;

import java.util.List;

/** Voluntari (T18): view-urile serviciilor → DTO-urile din docs/API.md. */
@UtilityClass
public class VolunteerMapper {

    public static VolunteerDto toDto(CityVolunteerService.VolunteerView view) {
        Volunteer v = view.volunteer();
        User c = view.coordinator();
        return new VolunteerDto(v.getUserId(), view.user() == null ? null : view.user().getDisplayName(), v.getPhone(), v.getEmail(),
                v.getLoginCode(), v.isOneTime(), v.getStatus(), view.expired(), v.getCoordinatorId(), c == null ? null : c.getDisplayName(),
                List.copyOf(v.getActionIds()), view.scans(), view.unread(), view.lastMessage() == null ? null : toMessage(view.lastMessage()),
                v.getCreatedAt(), v.getLastLoginAt());
    }

    public static VolunteerMessageDto toMessage(CityVolunteerService.MessageView m) {
        var e = m.message();
        return new VolunteerMessageDto(e.getId(), e.isFromCity(), m.authorName(), e.getText(), e.getCreatedAt(), e.getReadAt());
    }

    public static CoordinatorDto toCoordinator(User u) {
        return new CoordinatorDto(u.getId(), u.getDisplayName());
    }

    public static VolunteerMeDto toMe(VolunteerService.Me me) {
        return new VolunteerMeDto(me.user().getId(), me.user().getDisplayName(), me.coordinator() == null ? null : me.coordinator().getDisplayName(),
                me.volunteer().isOneTime(), me.actions(), me.scanned(), me.toScan(), me.unread());
    }

    public static VolunteerActionDto toAction(VolunteerService.ActionView view) {
        CommunityAction a = view.action();
        return new VolunteerActionDto(a.getId(), a.getTitle(), a.getCategory(), a.getStartAt(), a.getEndAt(), a.getLocationName(), a.getAddress(),
                a.getLat(), a.getLng(), a.getPointsReward(), a.getImageUrl(), a.getRegisteredCount(), a.getConfirmedCount(), view.confirmedByMe(),
                view.state(), VolunteerRules.opensAt(a));
    }

    public static RegistrationDto toScan(VolunteerService.ScanView s) {
        return CommunityMapper.toRegistration(s.registration(), s.action(), s.participant(), s.confirmedBy());
    }
}
