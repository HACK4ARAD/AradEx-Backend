package aradexperience.dto.auth;

import aradexperience.dto.DtoJson;
import aradexperience.enums.Role;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import static org.assertj.core.api.Assertions.assertThat;

class AuthResponseTest {

    private static final String JSON = "dto/auth/auth-response.json";

    @Test
    void deserialize_readsTokenAndUser() {
        AuthResponse res = DtoJson.fromResource(JSON, AuthResponse.class);

        assertThat(res.token()).startsWith("eyJ");
        assertThat(res.user().role()).isEqualTo(Role.LOCAL);
        assertThat(res.user().phone()).isEqualTo("+40799000002");
        assertThat(res.user().pointsBalance()).isEqualTo(270);
        assertThat(res.user().business()).isNull();
    }

    @Test
    void serialize_roundTrip_matchesJson() throws Exception {
        AuthResponse res = DtoJson.fromResource(JSON, AuthResponse.class);
        JSONAssert.assertEquals(DtoJson.read(JSON), DtoJson.MAPPER.writeValueAsString(res), JSONCompareMode.STRICT);
    }
}
