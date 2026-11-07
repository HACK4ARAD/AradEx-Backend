package aradexperience.dto.auth;

import aradexperience.dto.DtoJson;
import aradexperience.enums.Role;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import static org.assertj.core.api.Assertions.assertThat;

/** Șablonul testului de serializare al unui DTO: JSON-ul din resources e „adevărul” (= docs/API.md), DTO-ul trebuie să-l citească și să-l scrie identic. */
class UserDtoTest {

    private static final String JSON = "dto/auth/user-dto.json";

    @Test
    void deserialize_readsAllFields() {
        UserDto dto = DtoJson.fromResource(JSON, UserDto.class);

        assertThat(dto.id()).isEqualTo(3L);
        assertThat(dto.email()).isEqualTo("firma@demo.ro");
        assertThat(dto.phone()).isNull();
        assertThat(dto.role()).isEqualTo(Role.BUSINESS);
        assertThat(dto.locale()).isEqualTo("ro");
        assertThat(dto.pointsBalance()).isZero();
        assertThat(dto.business()).isNotNull();
        assertThat(dto.business().verified()).isTrue();
    }

    @Test
    void serialize_roundTrip_matchesJson() throws Exception {
        UserDto dto = DtoJson.fromResource(JSON, UserDto.class);
        JSONAssert.assertEquals(DtoJson.read(JSON), DtoJson.MAPPER.writeValueAsString(dto), JSONCompareMode.STRICT);
    }
}
