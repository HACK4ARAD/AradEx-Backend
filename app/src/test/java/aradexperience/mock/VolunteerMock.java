package aradexperience.mock;

import aradexperience.entity.community.CommunityAction;
import aradexperience.entity.users.User;
import aradexperience.entity.volunteer.Volunteer;
import aradexperience.entity.volunteer.ActionChatMessage;
import aradexperience.enums.Role;
import aradexperience.enums.VolunteerStatus;
import aradexperience.security.CurrentUser;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;

/** Date de test pentru T18 (ca în seed-ul V15_1): Andrei Popa (203, cod 482913), coordonatoarea Ioana Marin (201). */
public final class VolunteerMock {

    public static final long VOL_ID = 203L;
    public static final long COORD_ID = 201L;
    public static final String CODE = "482913";

    private VolunteerMock() {}

    public static Volunteer volunteer(long... actionIds) {
        Volunteer v = new Volunteer();
        v.setUserId(VOL_ID);
        v.setPhone("+40740000001");
        v.setLoginCode(CODE);
        v.setCoordinatorId(COORD_ID);
        v.setCreatedBy(4L);
        v.setCreatedAt(CommunityMock.NOW.minusDays(2));
        v.setStatus(VolunteerStatus.ACTIVE);
        v.setActionIds(new LinkedHashSet<>(java.util.Arrays.stream(actionIds).boxed().toList()));
        return v;
    }

    public static User user(long id, String name, Role role) {
        User u = new User();
        u.setId(id);
        u.setDisplayName(name);
        u.setRole(role);
        return u;
    }

    public static User volunteerUser() {
        return user(VOL_ID, "Andrei Popa", Role.VOLUNTEER);
    }

    public static User coordinator() {
        return user(COORD_ID, "Ioana Marin", Role.CITY_HALL);
    }

    public static CurrentUser current() {
        return new CurrentUser(VOL_ID, null, Role.VOLUNTEER);
    }

    /** O acțiune care începe la `start` și ține 3 ore. */
    public static CommunityAction actionAt(long id, OffsetDateTime start) {
        CommunityAction a = CommunityMock.action(id, 40, 18);
        a.setStartAt(start);
        a.setEndAt(start.plusHours(3));
        return a;
    }

    public static final long OTHER_VOL_ID = 204L;

    /** Un mesaj din chatul acțiunii `actionId`, scris de `authorId`. */
    public static ActionChatMessage chat(long id, long actionId, long authorId, String text, String imageUrl) {
        ActionChatMessage m = new ActionChatMessage();
        m.setId(id);
        m.setActionId(actionId);
        m.setAuthorId(authorId);
        m.setText(text);
        m.setImageUrl(imageUrl);
        m.setCreatedAt(CommunityMock.NOW.minusMinutes(30 - id));
        return m;
    }

    public static User otherVolunteerUser() {
        return user(OTHER_VOL_ID, "Maria Ionescu", Role.VOLUNTEER);
    }

    public static List<User> people() {
        return List.of(volunteerUser(), coordinator());
    }
}
