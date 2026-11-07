package aradexperience.dto.media;

import aradexperience.dto.DtoJson;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import static org.assertj.core.api.Assertions.assertThat;

class MediaDtoTest {

    private static final String JSON = "dto/media/media-dto.json";
    private static final String IMPORT_JSON = "dto/media/media-import-dto.json";

    @Test
    void deserialize_readsAllFields() {
        MediaDto dto = DtoJson.fromResource(JSON, MediaDto.class);
        assertThat(dto.url()).endsWith("/api/v1/media/uploads/3f9a1c0b7d2e4a51.jpg");
        assertThat(dto.key()).isEqualTo("uploads/3f9a1c0b7d2e4a51.jpg");
        assertThat(dto.contentType()).isEqualTo("image/jpeg");
        assertThat(dto.size()).isEqualTo(248113L);

        MediaImportDto imp = DtoJson.fromResource(IMPORT_JSON, MediaImportDto.class);
        assertThat(imp.imported()).isEqualTo(10);
        assertThat(imp.failed()).isEqualTo(1);
        assertThat(imp.remaining()).isEqualTo(213);
    }

    @Test
    void serialize_roundTrip_matchesJson() throws Exception {
        JSONAssert.assertEquals(DtoJson.read(JSON), DtoJson.MAPPER.writeValueAsString(DtoJson.fromResource(JSON, MediaDto.class)), JSONCompareMode.STRICT);
        JSONAssert.assertEquals(DtoJson.read(IMPORT_JSON),
                DtoJson.MAPPER.writeValueAsString(DtoJson.fromResource(IMPORT_JSON, MediaImportDto.class)), JSONCompareMode.STRICT);
    }
}
