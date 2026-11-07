package aradexperience.dto.places;

import aradexperience.dto.DtoJson;
import aradexperience.enums.PlaceType;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import static org.assertj.core.api.Assertions.assertThat;

/** JSON-ul din resources = docs/API.md §Locuri & ghid; DTO-urile îl citesc și îl scriu identic (null-urile lipsesc, ca în Spring). */
class PlaceDtoTest {

    private static final String SUMMARY = "dto/places/place-summary.json";
    private static final String DETAIL = "dto/places/place-dto.json";

    @Test
    void summary_deserialize_readsAllFields() {
        PlaceSummary s = DtoJson.fromResource(SUMMARY, PlaceSummary.class);
        assertThat(s.type()).isEqualTo(PlaceType.ATTRACTION);
        assertThat(s.tags()).containsExactly("istorie", "familie");
        assertThat(s.distanceKm()).isEqualTo(1.8);
        assertThat(s.guidePointsCount()).isEqualTo(3);
        assertThat(s.guideMinutes()).isEqualTo(5);
        assertThat(s.exteriorOnly()).isTrue();
    }

    @Test
    void summary_roundTrip_matchesJson() throws Exception {
        PlaceSummary s = DtoJson.fromResource(SUMMARY, PlaceSummary.class);
        JSONAssert.assertEquals(DtoJson.read(SUMMARY), DtoJson.MAPPER.writeValueAsString(s), JSONCompareMode.STRICT);
    }

    @Test
    void detail_deserialize_readsGuidePointsInOrder() {
        PlaceDto d = DtoJson.fromResource(DETAIL, PlaceDto.class);
        assertThat(d.exteriorOnly()).isTrue();
        assertThat(d.distanceKm()).isNull();
        assertThat(d.guidePoints()).extracting(GuidePointDto::position).containsExactly(1, 2);
        assertThat(d.guidePoints().get(1).lat()).isNull();
    }

    @Test
    void detail_roundTrip_matchesJson() throws Exception {
        PlaceDto d = DtoJson.fromResource(DETAIL, PlaceDto.class);
        var mapper = DtoJson.MAPPER.copy().setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
        JSONAssert.assertEquals(DtoJson.read(DETAIL), mapper.writeValueAsString(d), JSONCompareMode.STRICT);
    }
}
