package aradexperience.dto.auth;

import aradexperience.dto.DtoJson;
import aradexperience.enums.Role;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterRequestTest {

    private static final String JSON = "dto/auth/register-request.json";

    @Test
    void deserialize_readsNestedBusiness() {
        RegisterRequest req = DtoJson.fromResource(JSON, RegisterRequest.class);

        assertThat(req.role()).isEqualTo(Role.BUSINESS);
        assertThat(req.business().name()).isEqualTo("Cafeneaua din Pasaj");
        assertThat(req.business().lat()).isEqualTo(46.1738);
    }

    @Test
    void serialize_roundTrip_matchesJson() throws Exception {
        RegisterRequest req = DtoJson.fromResource(JSON, RegisterRequest.class);
        JSONAssert.assertEquals(DtoJson.read(JSON), DtoJson.MAPPER.writeValueAsString(req), JSONCompareMode.STRICT);
    }
}
