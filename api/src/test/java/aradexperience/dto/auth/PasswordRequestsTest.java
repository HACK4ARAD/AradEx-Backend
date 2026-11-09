package aradexperience.dto.auth;

import aradexperience.dto.DtoJson;
import aradexperience.enums.Role;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import static org.assertj.core.api.Assertions.assertThat;

/** Contul personal cu email + parolă: înregistrare fără firmă, „Am uitat parola” cu codul de recuperare, schimbarea parolei. */
class PasswordRequestsTest {

    @Test
    void recoverPassword_roundTrip() throws Exception {
        String json = "dto/auth/recover-password-request.json";
        RecoverPasswordRequest req = DtoJson.fromResource(json, RecoverPasswordRequest.class);
        assertThat(req.recoveryCode()).isEqualTo("K7QM-4XPT-9DWA");
        JSONAssert.assertEquals(DtoJson.read(json), DtoJson.MAPPER.writeValueAsString(req), JSONCompareMode.STRICT);
    }

    @Test
    void changePassword_requestAndResponse_roundTrip() throws Exception {
        String req = "dto/auth/change-password-request.json";
        String res = "dto/auth/change-password-response.json";
        assertThat(DtoJson.fromResource(req, ChangePasswordRequest.class).newPassword()).isEqualTo("parola-noua-2026");
        JSONAssert.assertEquals(DtoJson.read(req), DtoJson.MAPPER.writeValueAsString(DtoJson.fromResource(req, ChangePasswordRequest.class)), JSONCompareMode.STRICT);
        JSONAssert.assertEquals(DtoJson.read(res), DtoJson.MAPPER.writeValueAsString(DtoJson.fromResource(res, ChangePasswordResponse.class)), JSONCompareMode.STRICT);
    }

    @Test
    void registerPersonal_hasNoBusiness() throws Exception {
        String json = "dto/auth/register-request-personal.json";
        RegisterRequest req = DtoJson.fromResource(json, RegisterRequest.class);
        assertThat(req.role()).isEqualTo(Role.LOCAL);
        assertThat(req.business()).isNull();
        JSONAssert.assertEquals(DtoJson.read(json), DtoJson.MAPPER.writeValueAsString(req), JSONCompareMode.LENIENT);
    }
}
