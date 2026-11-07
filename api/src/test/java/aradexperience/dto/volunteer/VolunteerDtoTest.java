package aradexperience.dto.volunteer;

import aradexperience.dto.DtoJson;
import aradexperience.enums.ActionCategory;
import aradexperience.enums.VolunteerActionState;
import aradexperience.enums.VolunteerStatus;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import static org.assertj.core.api.Assertions.assertThat;

/** T18: JSON-urile din resources = docs/API.md §Voluntari; DTO-urile le citesc și le scriu identic. */
class VolunteerDtoTest {

    private static final String VOLUNTEER = "dto/volunteer/volunteer-dto.json";
    private static final String ACTION = "dto/volunteer/volunteer-action-dto.json";
    private static final String ME = "dto/volunteer/volunteer-me-dto.json";
    private static final String CHAT = "dto/volunteer/chat-dto.json";
    private static final String SUMMARY = "dto/volunteer/chat-summary-dto.json";

    @Test
    void volunteerDto_readsAllFields() {
        VolunteerDto dto = DtoJson.fromResource(VOLUNTEER, VolunteerDto.class);
        assertThat(dto.loginCode()).isEqualTo("482913");
        assertThat(dto.status()).isEqualTo(VolunteerStatus.ACTIVE);
        assertThat(dto.actionIds()).containsExactly(1L, 2L);
        assertThat(dto.coordinatorName()).isEqualTo("Ioana Marin");
        assertThat(dto.scans()).isEqualTo(2);
    }

    @Test
    void chatDtos_textPhotoOrBoth_andMine() {
        ChatDto chat = DtoJson.fromResource(CHAT, ChatDto.class);
        assertThat(chat.members()).extracting(ChatMemberDto::name).containsExactly("Andrei Popa", "Maria Ionescu");
        assertThat(chat.messages()).extracting(ChatMessageDto::mine).containsExactly(false, false, true);
        assertThat(chat.messages().get(1).imageUrl()).contains("/media/chat/");
        assertThat(chat.messages().get(2).text()).isNull();
        ChatSummaryDto s = DtoJson.fromResource(SUMMARY, ChatSummaryDto.class);
        assertThat(s.members()).isEqualTo(2);
        assertThat(s.unread()).isEqualTo(2);
    }

    @Test
    void volunteerActionDto_andMe_readAllFields() {
        VolunteerActionDto a = DtoJson.fromResource(ACTION, VolunteerActionDto.class);
        assertThat(a.category()).isEqualTo(ActionCategory.ENVIRONMENT);
        assertThat(a.state()).isEqualTo(VolunteerActionState.OPEN);
        assertThat(a.opensAt()).isEqualTo(a.startAt().minusMinutes(30));
        assertThat(a.confirmedByMe()).isEqualTo(2);
        VolunteerMeDto me = DtoJson.fromResource(ME, VolunteerMeDto.class);
        assertThat(me.toScan()).isEqualTo(29);
    }

    @Test
    void roundTrip_matchesJson() throws Exception {
        for (var c : new Object[][] { {VOLUNTEER, VolunteerDto.class}, {ACTION, VolunteerActionDto.class}, {ME, VolunteerMeDto.class},
                {CHAT, ChatDto.class}, {SUMMARY, ChatSummaryDto.class} }) {
            String path = (String) c[0];
            Object dto = DtoJson.fromResource(path, (Class<?>) c[1]);
            JSONAssert.assertEquals(DtoJson.read(path), DtoJson.APP.writeValueAsString(dto), JSONCompareMode.STRICT);
        }
    }
}
