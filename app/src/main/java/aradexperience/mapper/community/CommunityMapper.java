package aradexperience.mapper.community;

import aradexperience.dto.community.ActionCreate;
import aradexperience.dto.community.ActionDto;
import aradexperience.dto.community.ActionSummary;
import aradexperience.dto.community.ConfirmResultDto;
import aradexperience.dto.community.RegistrationDto;
import aradexperience.entity.community.CommunityAction;
import aradexperience.entity.community.CommunityRegistration;
import aradexperience.entity.users.User;
import aradexperience.enums.ActionStatus;
import aradexperience.service.community.CityCommunityService;
import aradexperience.service.community.CommunityService;
import aradexperience.util.Geo;
import lombok.experimental.UtilityClass;

import java.util.List;

/**
 * Acțiuni / înscrieri ↔ DTO-urile din `dto/community`. Câmpurile calculate (goingPreview, myRegistration, numele) vin ca parametri.
 * Metodele sunt declarate explicit `static` (import static).
 */
@UtilityClass
public class CommunityMapper {

    public static ActionSummary toSummary(CommunityAction a, List<String> goingPreview, RegistrationDto myRegistration) {
        return new ActionSummary(a.getId(), a.getTitle(), a.getCategory(), a.getOrganizer(), a.getStartAt(), a.getEndAt(),
                a.getLocationName(), a.getLat(), a.getLng(), a.getPointsReward(), a.getCapacity(), a.getRegisteredCount(),
                a.getImageUrl(), goingPreview, myRegistration);
    }

    public static ActionDto toDto(CommunityAction a, List<String> goingPreview, RegistrationDto myRegistration) {
        return new ActionDto(a.getId(), a.getTitle(), a.getCategory(), a.getOrganizer(), a.getStartAt(), a.getEndAt(),
                a.getLocationName(), a.getLat(), a.getLng(), a.getPointsReward(), a.getCapacity(), a.getRegisteredCount(),
                a.getImageUrl(), goingPreview, myRegistration,
                a.getDescription(), a.getAddress(), a.getStatus(), a.getConfirmedCount(), a.getImageCredit());
    }

    /**
     * @param user        participantul (doar în listele Primăriei), altfel null
     * @param confirmedBy numele celui care a confirmat / respins, sau null
     */
    public static RegistrationDto toRegistration(CommunityRegistration r, CommunityAction a, User user, String confirmedBy) {
        return new RegistrationDto(r.getId(), r.getActionId(), a.getTitle(), a.getStartAt(), a.getLocationName(), a.getPointsReward(),
                r.getCode(), r.getStatus(), r.getRegisteredAt(), r.getConfirmedAt(), confirmedBy,
                user == null ? null : new RegistrationDto.UserRef(user.getId(), user.getDisplayName()), a.getImageUrl());
    }

    public static ConfirmResultDto toConfirmResult(CommunityRegistration r, CommunityAction a, User user, String confirmedBy,
                                                   boolean alreadyConfirmed, int pointsAwarded) {
        return new ConfirmResultDto(r.getId(), r.getActionId(), a.getTitle(), a.getStartAt(), a.getLocationName(), a.getPointsReward(),
                r.getCode(), r.getStatus(), r.getRegisteredAt(), r.getConfirmedAt(), confirmedBy,
                user == null ? null : new RegistrationDto.UserRef(user.getId(), user.getDisplayName()), a.getImageUrl(), alreadyConfirmed, pointsAwarded);
    }

    // ---- din view-urile serviciilor (entitate + date calculate)

    public static ActionSummary toSummary(CommunityService.ActionView v) {
        return toSummary(v.action(), v.goingPreview(), v.myRegistration() == null ? null : toRegistration(v.myRegistration()));
    }

    public static ActionDto toDto(CommunityService.ActionView v) {
        return toDto(v.action(), v.goingPreview(), v.myRegistration() == null ? null : toRegistration(v.myRegistration()));
    }

    public static RegistrationDto toRegistration(CommunityService.RegistrationView v) {
        return toRegistration(v.registration(), v.action(), v.user(), v.confirmedBy());
    }

    public static ConfirmResultDto toConfirmResult(CityCommunityService.ConfirmView c) {
        var v = c.registration();
        return toConfirmResult(v.registration(), v.action(), v.user(), v.confirmedBy(), c.alreadyConfirmed(), c.pointsAwarded());
    }

    /** Acțiune nouă: contoarele pornesc de la 0, fără coordonate → centrul Aradului, fără stare → PUBLISHED. */
    public static CommunityAction toEntity(ActionCreate req, long createdBy) {
        CommunityAction a = new CommunityAction();
        a.setCreatedBy(createdBy);
        apply(req, a);
        a.setStatus(req.status() == null ? ActionStatus.PUBLISHED : req.status());
        return a;
    }

    /** Copiază câmpurile editabile (fără stare și contoare). */
    public static void apply(ActionCreate req, CommunityAction a) {
        a.setTitle(req.title().trim());
        a.setDescription(req.description().trim());
        a.setCategory(req.category());
        a.setOrganizer(blankToNull(req.organizer()));
        a.setStartAt(req.startAt());
        a.setEndAt(req.endAt());
        a.setLocationName(req.locationName().trim());
        a.setAddress(blankToNull(req.address()));
        a.setLat(req.lat() == null ? Geo.ARAD_LAT : req.lat());
        a.setLng(req.lng() == null ? Geo.ARAD_LNG : req.lng());
        a.setPointsReward(req.pointsReward());
        a.setCapacity(req.capacity());
        a.setImageUrl(blankToNull(req.imageUrl()));
        a.setImageCredit(blankToNull(req.imageCredit()));
    }

    /** „Radu M.” → „Radu” (pentru „Radu, Elena și încă 16 merg”). */
    public static String firstName(String displayName) {
        if (displayName == null || displayName.isBlank()) return "—";
        String s = displayName.trim();
        int sp = s.indexOf(' ');
        return sp > 0 ? s.substring(0, sp) : s;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
