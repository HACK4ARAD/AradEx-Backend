package aradexperience.dto.google;

import aradexperience.dto.DtoJson;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleDtoTest {

    private static final String INFO = "dto/google/google-info-dto.json";
    private static final String LINK = "dto/google/google-link-dto.json";

    @Test
    void deserialize_readsAllFields() {
        GoogleInfoDto info = DtoJson.fromResource(INFO, GoogleInfoDto.class);
        assertThat(info.rating()).isEqualTo(4.6);
        assertThat(info.userRatingCount()).isEqualTo(1240);
        assertThat(info.reviews()).hasSize(2);
        assertThat(info.reviews().get(0).author()).isEqualTo("Ana P.");
        assertThat(info.reviews().get(0).rating()).isEqualTo(5);
        assertThat(info.reviews().get(0).relativeTime()).isEqualTo("acum o lună");
        assertThat(info.reviews().get(1).authorUrl()).isNull();
        assertThat(info.attribution()).isEqualTo(GoogleInfoDto.ATTRIBUTION);

        GoogleLinkDto link = DtoJson.fromResource(LINK, GoogleLinkDto.class);
        assertThat(link.googlePlaceId()).isEqualTo("ChIJ7VFfQACZRUcRXFuEhkS_zss");
        assertThat(link.configured()).isTrue();
        assertThat(link.info().userRatingCount()).isEqualTo(32);
    }

    @Test
    void serialize_roundTrip_matchesJson() throws Exception {
        JSONAssert.assertEquals(DtoJson.read(INFO), DtoJson.APP.writeValueAsString(DtoJson.fromResource(INFO, GoogleInfoDto.class)), JSONCompareMode.STRICT);
        JSONAssert.assertEquals(DtoJson.read(LINK), DtoJson.APP.writeValueAsString(DtoJson.fromResource(LINK, GoogleLinkDto.class)), JSONCompareMode.STRICT);
    }
}
