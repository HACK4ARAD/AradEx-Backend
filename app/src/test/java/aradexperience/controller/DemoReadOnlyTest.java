package aradexperience.controller;

import aradexperience.config.DemoReadOnly;
import aradexperience.controller.auth.AuthController;
import aradexperience.enums.Role;
import aradexperience.mock.AuthMock;
import aradexperience.mock.UserMock;
import aradexperience.service.auth.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** „Doar demo” (11 nov): cu `app.demo-only=true` orice modificare e refuzată; citirile și login-ul merg. */
@ControllerTest(AuthController.class)
@TestPropertySource(properties = "app.demo-only=true")
class DemoReadOnlyTest {

    @Autowired MockMvc mvc;
    @MockBean AuthService service;

    @Test
    void writes_areRefusedWithTheDemoMessage_beforeReachingTheService() throws Exception {
        mvc.perform(patch("/api/v1/me").with(AuthMock.as(2L, Role.LOCAL)).contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Ana\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(DemoReadOnly.READ_ONLY));
        mvc.perform(post("/api/v1/me/password").with(AuthMock.as(2L, Role.LOCAL)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"demo1234\",\"newPassword\":\"parola-noua-1\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void reads_andTheLogin_stillWork() throws Exception {
        when(service.me(any())).thenReturn(new AuthService.Profile(UserMock.local(), null));
        when(service.login(any())).thenReturn(new AuthService.Session("jwt", UserMock.local(), null));

        mvc.perform(get("/api/v1/me").with(AuthMock.as(2L, Role.LOCAL))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"local@demo.ro\",\"password\":\"demo1234\"}"))
                .andExpect(status().isOk());
        verify(service).login(any());
    }
}
