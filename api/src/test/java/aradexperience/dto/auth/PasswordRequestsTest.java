package aradexperience.dto.auth;

import aradexperience.dto.DtoJson;
import aradexperience.enums.Role;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import static org.assertj.core.api.Assertions.assertThat;

/** Cererile pentru contul personal cu email + parolă: înregistrare fără firmă, „am uitat parola” și resetarea cu cod. */
class PasswordRequestsTest {

    @Test
    void forgotPassword_roundTrip() throws Exception {
        String json = "dto/auth/forgot-password-request.json";
        ForgotPasswordRequest req = DtoJson.fromResource(json, ForgotPasswordRequest.class);
        assertThat(req.email()).isEqualTo("ana@exemplu.ro");
        JSONAssert.assertEquals(DtoJson.read(json), DtoJson.MAPPER.writeValueAsString(req), JSONCompareMode.STRICT);
    }

    @Test
    void resetPassword_roundTrip() throws Exception {
        String json = "dto/auth/reset-password-request.json";
        ResetPasswordRequest req = DtoJson.fromResource(json, ResetPasswordRequest.class);
        assertThat(req.code()).isEqualTo("482915");
        assertThat(req.password()).isEqualTo("parola-noua-2026");
        JSONAssert.assertEquals(DtoJson.read(json), DtoJson.MAPPER.writeValueAsString(req), JSONCompareMode.STRICT);
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
