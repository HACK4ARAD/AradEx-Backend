package aradexperience.mapper.business;

import aradexperience.dto.business.AnnouncementCreate;
import aradexperience.dto.business.AnnouncementDto;
import aradexperience.dto.business.BusinessDto;
import aradexperience.dto.business.BusinessUpdate;
import aradexperience.entity.business.Announcement;
import aradexperience.entity.business.Business;
import aradexperience.enums.ContentStatus;
import lombok.experimental.UtilityClass;

import java.time.OffsetDateTime;

/** Business / Announcement ↔ DTO-urile din `dto/business`. */
@UtilityClass
public class BusinessMapper {

    public static BusinessDto toDto(Business b) {
        return new BusinessDto(b.getId(), b.getName(), b.getCategory().name(), b.isVerified(), b.getImageUrl(), b.getDescription(),
                b.getAddress(), b.getLat(), b.getLng(), b.getPhone(), b.getWebsite(), b.getViewsCount());
    }

    /** Copiază profilul editabil; `verified`, `viewsCount` și proprietarul nu se schimbă de aici. */
    public static void apply(BusinessUpdate req, Business b) {
        b.setName(req.name().trim());
        b.setCategory(req.category());
        b.setDescription(blankToNull(req.description()));
        b.setAddress(req.address().trim());
        b.setLat(req.lat());
        b.setLng(req.lng());
        b.setPhone(blankToNull(req.phone()));
        b.setWebsite(blankToNull(req.website()));
        b.setImageUrl(blankToNull(req.imageUrl()));
    }

    public static AnnouncementDto toAnnouncementDto(Announcement a) {
        return new AnnouncementDto(a.getId(), a.getTitle(), a.getBody(), a.getImageUrl(), a.getStatus(), a.getCreatedAt());
    }

    public static Announcement toAnnouncement(AnnouncementCreate req, long businessId, ContentStatus status, OffsetDateTime now) {
        Announcement a = new Announcement();
        a.setBusinessId(businessId);
        a.setTitle(req.title().trim());
        a.setBody(req.body().trim());
        a.setImageUrl(blankToNull(req.imageUrl()));
        a.setStatus(status);
        a.setCreatedAt(now);
        return a;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
