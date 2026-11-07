package aradexperience.dto.auth;

import aradexperience.dto.DtoJson;
import aradexperience.enums.Role;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import static org.assertj.core.api.Assertions.assertThat;

/** T21: DTO-urile login-ului cu telefon + OTP (docs/API.md §Auth). */
class OtpDtosTest {

    @Test
    void otpRequest_readsPhone() {
        assertThat(DtoJson.fromResource("dto/auth/otp-request.json", OtpRequest.class).phone()).isEqualTo("0799 000 001");
    }

    @Test
    void otpRequestResponse_readsRetryAndDemoCode() {
        OtpRequestResponse r = DtoJson.fromResource("dto/auth/otp-request-response.json", OtpRequestResponse.class);
        assertThat(r.retryAfterSeconds()).isEqualTo(60);
        assertThat(r.demoCode()).isEqualTo("482913");
    }

    @Test
    void otpVerifyResponse_newNumber_hasOnlySignupToken() {
        OtpVerifyResponse r = DtoJson.fromResource("dto/auth/otp-verify-response-new.json", OtpVerifyResponse.class);
        assertThat(r.token()).isNull();
        assertThat(r.user()).isNull();
        assertThat(r.signupToken()).startsWith("eyJ");
    }

    @Test
    void otpRegisterRequest_readsAllFields() {
        OtpRegisterRequest r = DtoJson.fromResource("dto/auth/otp-register-request.json", OtpRegisterRequest.class);
        assertThat(r.displayName()).isEqualTo("Ana Pop");
        assertThat(r.role()).isEqualTo(Role.LOCAL);
    }

    @ParameterizedTest
    @CsvSource({
            "dto/auth/otp-request.json, aradexperience.dto.auth.OtpRequest",
            "dto/auth/otp-request-response.json, aradexperience.dto.auth.OtpRequestResponse",
            "dto/auth/otp-verify-request.json, aradexperience.dto.auth.OtpVerifyRequest",
            "dto/auth/otp-verify-response-new.json, aradexperience.dto.auth.OtpVerifyResponse",
            "dto/auth/otp-register-request.json, aradexperience.dto.auth.OtpRegisterRequest",
    })
    void roundTrip_matchesJson(String json, Class<?> type) throws Exception {
        Object dto = DtoJson.fromResource(json, type);
        JSONAssert.assertEquals(DtoJson.read(json), DtoJson.MAPPER.writeValueAsString(dto), JSONCompareMode.STRICT);
    }
}
