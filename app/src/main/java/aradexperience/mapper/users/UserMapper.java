package aradexperience.mapper.users;

import aradexperience.dto.auth.BusinessSummary;
import aradexperience.dto.auth.RegisterRequest;
import aradexperience.dto.auth.UserDto;
import aradexperience.entity.business.Business;
import aradexperience.entity.users.User;
import aradexperience.enums.BusinessCategory;
import lombok.experimental.UtilityClass;

/**
 * User/Business ↔ DTO-urile din `dto/auth`. Metodele sunt declarate explicit `static` ca importul static să meargă
 * (javac nu vede metodele făcute statice de Lombok la `import static`).
 */
@UtilityClass
public class UserMapper {

    /** @param business firma utilizatorului (doar pentru rolul BUSINESS), altfel null */
    public static UserDto toDto(User u, Business business) {
        return new UserDto(u.getId(), u.getEmail(), u.getPhone(), u.getDisplayName(), u.getRole(), u.getLocale(), u.getPointsBalance(),
                business == null ? null : toBusinessSummary(business), u.isMustChangePassword(), u.getRecoveryCodeHash() != null);
    }

    public static BusinessSummary toBusinessSummary(Business b) {
        return new BusinessSummary(b.getId(), b.getName(), b.getCategory().name(), b.isVerified(), b.getImageUrl());
    }

    /** Firmă nouă din înregistrare: neverificată, conținutul ei rămâne ciornă până la verificare. */
    public static Business toBusiness(RegisterRequest.BusinessCreate req, BusinessCategory category, long ownerUserId) {
        Business b = new Business();
        b.setOwnerUserId(ownerUserId);
        b.setName(req.name().trim());
        b.setCategory(category);
        b.setAddress(req.address().trim());
        b.setLat(req.lat());
        b.setLng(req.lng());
        b.setVerified(false);
        return b;
    }
}
