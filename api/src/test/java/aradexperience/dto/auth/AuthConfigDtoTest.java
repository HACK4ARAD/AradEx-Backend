package aradexperience.dto.auth;

import aradexperience.dto.DtoJson;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import static org.assertj.core.api.Assertions.assertThat;

class AuthConfigDtoTest {

    @Test
    void roundTrip() throws Exception {
        String json = "dto/auth/auth-config.json";
        AuthConfigDto dto = DtoJson.fromResource(json, AuthConfigDto.class);
        assertThat(dto.demoOnly()).isTrue();
        JSONAssert.assertEquals(DtoJson.read(json), DtoJson.MAPPER.writeValueAsString(dto), JSONCompareMode.STRICT);
    }
}
