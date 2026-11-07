package aradexperience.service.volunteer;

import aradexperience.entity.community.CommunityAction;
import aradexperience.enums.ActionStatus;
import aradexperience.mock.VolunteerMock;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static aradexperience.enums.VolunteerActionState.ENDED;
import static aradexperience.enums.VolunteerActionState.LOCKED;
import static aradexperience.enums.VolunteerActionState.OPEN;
import static org.assertj.core.api.Assertions.assertThat;

class VolunteerRulesTest {

    static final OffsetDateTime START = OffsetDateTime.parse("2026-11-07T10:00:00+02:00");
    final CommunityAction a = VolunteerMock.actionAt(1L, START);   // 10:00–13:00

    @Test
    void locked_until30MinutesBefore_openUntilMidnight_thenEnded() {
        assertThat(VolunteerRules.state(a, START.minusDays(1))).isEqualTo(LOCKED);
        assertThat(VolunteerRules.state(a, START.minusMinutes(31))).isEqualTo(LOCKED);
        assertThat(VolunteerRules.state(a, START.minusMinutes(30))).isEqualTo(OPEN);
        assertThat(VolunteerRules.state(a, START.plusHours(5))).isEqualTo(OPEN);                       // 15:00, întârziații
        assertThat(VolunteerRules.state(a, OffsetDateTime.parse("2026-11-07T23:59:00+02:00"))).isEqualTo(OPEN);
        assertThat(VolunteerRules.state(a, OffsetDateTime.parse("2026-11-08T00:00:00+02:00"))).isEqualTo(ENDED);
        assertThat(VolunteerRules.opensAt(a)).isEqualTo(START.minusMinutes(30));
    }

    @Test
    void doneActionIsEnded_draftIsNotVisible() {
        a.setStatus(ActionStatus.DONE);
        assertThat(VolunteerRules.state(a, START)).isEqualTo(ENDED);
        assertThat(VolunteerRules.visible(a)).isTrue();
        a.setStatus(ActionStatus.DRAFT);
        assertThat(VolunteerRules.visible(a)).isFalse();
    }

    @Test
    void oneTimeAccount_expiresAfterTheLastAllocatedAction() {
        var v = VolunteerMock.volunteer(1L, 2L);
        CommunityAction later = VolunteerMock.actionAt(2L, START.plusDays(3));
        OffsetDateTime nextWeek = START.plusDays(7);
        assertThat(VolunteerRules.expired(v, List.of(a, later), nextWeek)).isFalse();      // nu e de unică folosință
        v.setOneTime(true);
        assertThat(VolunteerRules.expired(v, List.of(a, later), START.plusDays(1))).isFalse();
        assertThat(VolunteerRules.expired(v, List.of(a, later), nextWeek)).isTrue();
        assertThat(VolunteerRules.expired(v, List.of(), nextWeek)).isFalse();              // fără acțiuni: Primăria îi poate aloca una
    }
}
