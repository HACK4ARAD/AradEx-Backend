package aradexperience.dto.community;

import aradexperience.dto.DtoJson;
import aradexperience.dto.points.PointsDto;
import aradexperience.enums.ActionCategory;
import aradexperience.enums.ActionStatus;
import aradexperience.enums.PointsType;
import aradexperience.enums.RegistrationStatus;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import static org.assertj.core.api.Assertions.assertThat;

/** Serializarea DTO-urilor T11: JSON-ul din resources = docs/API.md §Comunitate & puncte. */
class CommunityDtoTest {

    @Test
    void actionDto_readsAllFields_andRoundTrips() throws Exception {
        ActionDto dto = DtoJson.fromResource("dto/community/action-dto.json", ActionDto.class);

        assertThat(dto.category()).isEqualTo(ActionCategory.ENVIRONMENT);
        assertThat(dto.status()).isEqualTo(ActionStatus.PUBLISHED);
        assertThat(dto.goingPreview()).containsExactly("Radu", "Elena", "Mihai");
        assertThat(dto.myRegistration().code()).isEqualTo("AX-C1-K4N7PA");
        assertThat(dto.myRegistration().status()).isEqualTo(RegistrationStatus.REGISTERED);
        assertThat(dto.myRegistration().user()).isNull();
        roundTrip("dto/community/action-dto.json", dto);
    }

    @Test
    void confirmResult_readsAllFields_andRoundTrips() throws Exception {
        ConfirmResultDto dto = DtoJson.fromResource("dto/community/confirm-result.json", ConfirmResultDto.class);

        assertThat(dto.status()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(dto.user().displayName()).isEqualTo("Ana Popescu");
        assertThat(dto.alreadyConfirmed()).isFalse();
        assertThat(dto.pointsAwarded()).isEqualTo(50);
        roundTrip("dto/community/confirm-result.json", dto);
    }

    @Test
    void leaderboard_readsAllFields_andRoundTrips() throws Exception {
        LeaderboardDto dto = DtoJson.fromResource("dto/community/leaderboard.json", LeaderboardDto.class);

        assertThat(dto.period()).isEqualTo("month");
        assertThat(dto.entries()).hasSize(2);
        assertThat(dto.entries().get(0).actionsCount()).isEqualTo(2);
        assertThat(dto.me().rank()).isEqualTo(16);
        roundTrip("dto/community/leaderboard.json", dto);
    }

    @Test
    void pointsDto_readsAllFields_andRoundTrips() throws Exception {
        PointsDto dto = DtoJson.fromResource("dto/points/points-dto.json", PointsDto.class);

        assertThat(dto.balance()).isEqualTo(270);
        assertThat(dto.streakMonths()).isEqualTo(2);
        assertThat(dto.transactions()).extracting(PointsDto.Transaction::type).containsExactly(PointsType.EARN_ACTION, PointsType.SPEND_OFFER);
        roundTrip("dto/points/points-dto.json", dto);
    }

    private static void roundTrip(String path, Object dto) throws Exception {
        // ca în aplicație: câmpurile null nu se scriu (non_null), datele ca ISO-8601 cu offset
        String json = MAPPER.writeValueAsString(MAPPER.readValue(DtoJson.read(path), dto.getClass()));
        JSONAssert.assertEquals(DtoJson.read(path), json, JSONCompareMode.STRICT);
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = DtoJson.MAPPER.copy()
            .setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(com.fasterxml.jackson.databind.DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
}
