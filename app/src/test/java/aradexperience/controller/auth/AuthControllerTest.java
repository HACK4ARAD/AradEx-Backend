package aradexperience.controller.auth;

import aradexperience.controller.ControllerTest;
import aradexperience.dto.auth.UpdateMeRequest;
import aradexperience.entity.users.User;
import aradexperience.enums.Role;
import aradexperience.exceptions.ConflictException;
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
}
