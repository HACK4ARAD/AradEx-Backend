package aradexperience.controller.auth;

import aradexperience.controller.ControllerTest;
import aradexperience.dto.auth.UpdateMeRequest;
import aradexperience.entity.users.User;
import aradexperience.enums.Role;
import aradexperience.exceptions.ConflictException;
import aradexperience.exceptions.TooManyRequestsException;
import aradexperience.service.auth.OtpService;
import aradexperience.exceptions.UnauthorizedException;
import aradexperience.mock.AuthMock;
import aradexperience.mock.JsonFixtures;
import aradexperience.mock.UserMock;
import aradexperience.service.auth.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ControllerTest(AuthController.class)
class AuthControllerTest {

    @Autowired MockMvc mvc;
    @MockBean AuthService service;

    @Test
    void register_returns201WithTokenAndUser() throws Exception {
        User u = UserMock.user(42L, "ana@exemplu.ro", Role.LOCAL);
        when(service.register(any())).thenReturn(new AuthService.Session("jwt", u, null));

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/auth/register.json")))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.token").value("jwt"))
           .andExpect(jsonPath("$.user.id").value(42))
           .andExpect(jsonPath("$.user.role").value("LOCAL"))
           .andExpect(jsonPath("$.user.business").doesNotExist());
    }

    @Test
    void register_invalidBody_is400WithErrors() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nu-e-email\",\"password\":\"123\",\"displayName\":\"A\",\"role\":\"LOCAL\"}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void register_duplicate_is409() throws Exception {
        when(service.register(any())).thenThrow(new ConflictException("Email already registered"));

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/auth/register.json")))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.detail").value("Email already registered"));
    }

    @Test
    void login_returns200() throws Exception {
        User owner = UserMock.businessOwner();
        when(service.login(any())).thenReturn(new AuthService.Session("jwt", owner, UserMock.business(owner.getId())));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/auth/login.json")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.token").value("jwt"))
           .andExpect(jsonPath("$.user.business.name").value("Cafeneaua din Pasaj"))
           .andExpect(jsonPath("$.user.business.verified").value(true));
    }

    @Test
    void login_wrongPassword_is401ProblemDetail() throws Exception {
        when(service.login(any())).thenThrow(new UnauthorizedException("Invalid email or password"));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/auth/login.json")))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.status").value(401))
           .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void me_withToken_returnsUser() throws Exception {
        when(service.me(any())).thenReturn(new AuthService.Profile(UserMock.local(), null));

        mvc.perform(get("/api/v1/me").with(AuthMock.as(2L, Role.LOCAL)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.email").value("local@demo.ro"))
           .andExpect(jsonPath("$.pointsBalance").value(270));
    }

    @Test
    void me_withoutToken_is401() throws Exception {
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void updateMe_returnsUpdatedUser() throws Exception {
        User u = UserMock.local();
        u.setLocale("en");
        when(service.updateMe(any(), eq(new UpdateMeRequest("Ana P.", "en")))).thenReturn(new AuthService.Profile(u, null));

        mvc.perform(patch("/api/v1/me").with(AuthMock.as(2L, Role.LOCAL))
                .contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/auth/update-me.json")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.locale").value("en"));
    }

    @Test
    void updateMe_badLocale_is400() throws Exception {
        mvc.perform(patch("/api/v1/me").with(AuthMock.as(2L, Role.LOCAL))
                .contentType(MediaType.APPLICATION_JSON).content("{\"locale\":\"de\"}"))
           .andExpect(status().isBadRequest());
    }

    // --- T21: telefon + OTP -----------------------------------------------------------------------------------------

    @Test
    void requestOtp_demoNumber_returns202WithDemoCode() throws Exception {
        when(service.requestOtp("0799 000 001")).thenReturn(new OtpService.RequestResult(60, "482913"));

        mvc.perform(post("/api/v1/auth/otp/request").contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/auth/otp-request.json")))
           .andExpect(status().isAccepted())
           .andExpect(jsonPath("$.retryAfterSeconds").value(60))
           .andExpect(jsonPath("$.demoCode").value("482913"));
    }

    @Test
    void requestOtp_tooSoon_is429WithRetryAfterSeconds() throws Exception {
        when(service.requestOtp(any())).thenThrow(new TooManyRequestsException("Wait before requesting a new code", 42));

        mvc.perform(post("/api/v1/auth/otp/request").contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/auth/otp-request.json")))
           .andExpect(status().isTooManyRequests())
           .andExpect(jsonPath("$.status").value(429))
           .andExpect(jsonPath("$.retryAfterSeconds").value(42));
    }

    @Test
    void verifyOtp_existingAccount_returnsTokenAndUserWithPhone() throws Exception {
        User u = UserMock.local();
        u.setPhone("+40799000002");
        when(service.verifyOtp("0799000001", "482913")).thenReturn(new AuthService.PhoneLogin(new AuthService.Session("jwt", u, null), null));

        mvc.perform(post("/api/v1/auth/otp/verify").contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/auth/otp-verify.json")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.token").value("jwt"))
           .andExpect(jsonPath("$.user.phone").value("+40799000002"))
           .andExpect(jsonPath("$.signupToken").doesNotExist());
    }

    @Test
    void verifyOtp_newNumber_returnsOnlySignupToken() throws Exception {
        when(service.verifyOtp(any(), any())).thenReturn(new AuthService.PhoneLogin(null, "signup"));

        mvc.perform(post("/api/v1/auth/otp/verify").contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/auth/otp-verify.json")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.signupToken").value("signup"))
           .andExpect(jsonPath("$.token").doesNotExist())
           .andExpect(jsonPath("$.user").doesNotExist());
    }

    @Test
    void verifyOtp_wrongCode_is401() throws Exception {
        when(service.verifyOtp(any(), any())).thenThrow(new UnauthorizedException("Invalid or expired code"));

        mvc.perform(post("/api/v1/auth/otp/verify").contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/auth/otp-verify.json")))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.detail").value("Invalid or expired code"));
    }

    @Test
    void registerWithPhone_returns201() throws Exception {
        User u = UserMock.user(99L, null, Role.LOCAL);
        u.setPhone("+40799000042");
        when(service.registerWithPhone(any())).thenReturn(new AuthService.Session("jwt", u, null));

        mvc.perform(post("/api/v1/auth/otp/register").contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/auth/otp-register.json")))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.token").value("jwt"))
           .andExpect(jsonPath("$.user.phone").value("+40799000042"))
           .andExpect(jsonPath("$.user.email").doesNotExist());
    }

    @Test
    void registerWithPhone_missingName_is400() throws Exception {
        mvc.perform(post("/api/v1/auth/otp/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"signupToken\":\"signup\",\"displayName\":\" \",\"role\":\"LOCAL\"}"))
           .andExpect(status().isBadRequest());
    }
}
