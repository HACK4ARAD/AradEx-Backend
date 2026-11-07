package aradexperience.mapper.admin;

import aradexperience.dto.admin.AdminContentDto;
import aradexperience.dto.admin.AdminStatsDto;
import aradexperience.dto.admin.AdminUserDto;
import aradexperience.dto.admin.CityBusinessDto;
import aradexperience.entity.business.Business;
import aradexperience.entity.community.CommunityAction;
import aradexperience.entity.events.Event;
import aradexperience.entity.offers.Offer;
import aradexperience.entity.users.User;
import aradexperience.service.admin.AdminService;
import aradexperience.service.admin.PartnerService;
import lombok.experimental.UtilityClass;

import java.util.Map;

import static aradexperience.mapper.users.UserMapper.toBusinessSummary;

/** T14: rândurile din panourile de administrare (statistici, conținut, utilizatori, firme partenere) → DTO-urile din `dto/admin`. */
@UtilityClass
public class AdminMapper {

    public static AdminStatsDto toDto(AdminService.Stats s) {
        return new AdminStatsDto(s.usersByRole(), s.events(), s.ticketsSold(), s.offersRedeemed(), s.codesUsed(), s.pointsAwarded(), s.actions());
    }

    /** Un element din „Conținut & moderare”; `businesses` = firmele organizatoare, după id. */
    public static AdminContentDto toContent(Object item, Map<Long, Business> businesses) {
        return switch (item) {
            case Event e -> {
                Business b = e.getOrganizerBusinessId() == null ? null : businesses.get(e.getOrganizerBusinessId());
                String organizer = e.getOrganizerName() != null ? e.getOrganizerName() : b != null ? b.getName() : null;
                yield new AdminContentDto(e.getId(), e.getTitle(), e.getCategory().name(), organizer, e.getOrganizerBusinessId(),
                        b == null ? null : b.isVerified(), e.getImageUrl(), e.getStartAt(), e.getEndAt(), null, null,
                        e.getParticipantsCount(), e.getCapacity(), null, null, e.getStatus().name());
            }
            case Offer o -> {
                Business b = businesses.get(o.getBusinessId());
                yield new AdminContentDto(o.getId(), o.getTitle(), o.getCategory().name(), b == null ? null : b.getName(), o.getBusinessId(),
                        b == null ? null : b.isVerified(), o.getImageUrl(), null, null, o.getValidFrom(), o.getValidUntil(),
                        null, null, o.getUsedCount(), o.getViewsCount(), o.getStatus().name());
            }
            case CommunityAction a -> new AdminContentDto(a.getId(), a.getTitle(), a.getCategory().name(), a.getOrganizer(), null, null,
                    a.getImageUrl(), a.getStartAt(), a.getEndAt(), null, null, a.getRegisteredCount(), a.getCapacity(), null, null,
                    a.getStatus().name());
            default -> throw new IllegalArgumentException("Unsupported content: " + item.getClass());
        };
    }

    public static AdminUserDto toDto(AdminService.UserRow r) {
        User u = r.user();
        return new AdminUserDto(u.getId(), u.getEmail(), u.getPhone(), u.getDisplayName(), u.getRole(), u.getLocale(), u.getPointsBalance(),
                r.business() == null ? null : toBusinessSummary(r.business()), u.isDisabled(), u.getCreatedAt(), r.tickets());
    }

    public static CityBusinessDto toDto(PartnerService.Row r) {
        Business b = r.business();
        User owner = r.owner();
        return new CityBusinessDto(b.getId(), b.getName(), b.getCategory().name(), b.isVerified(), b.getImageUrl(), b.getAddress(),
                b.getPhone(), owner == null ? null : owner.getDisplayName(), owner == null ? null : owner.getEmail(),
                r.activeOffers(), r.draftOffers(), r.codesUsed(), b.getCreatedAt(), b.getRejectedAt(), b.getRejectReason());
    }
}
