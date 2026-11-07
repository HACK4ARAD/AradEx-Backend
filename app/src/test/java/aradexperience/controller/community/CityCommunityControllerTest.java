package aradexperience.controller.community;

import aradexperience.controller.ControllerTest;
import aradexperience.entity.community.CommunityAction;
import aradexperience.enums.ActionStatus;
import aradexperience.enums.RegistrationStatus;
import aradexperience.enums.Role;
import aradexperience.exceptions.NotFoundException;
import aradexperience.mock.AuthMock;
import aradexperience.mock.CommunityMock;
import aradexperience.mock.JsonFixtures;
import aradexperience.mock.UserMock;
import aradexperience.service.community.CityCommunityService;
import aradexperience.service.community.CommunityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ControllerTest(CityCommunityController.class)
class CityCommunityControllerTest {

    @Autowired MockMvc mvc;
    @MockBean CityCommunityService service;

    @Test
    void confirmCode_returnsResultWithPoints() throws Exception {
        CommunityAction a = CommunityMock.action(1L, 40, 18);
        var r = CommunityMock.registration(5L, 1L, 2L, RegistrationStatus.CONFIRMED);
        r.setConfirmedAt(CommunityMock.NOW);
        when(service.confirmByCode(eq("AX-C1-K4N7PA"), any())).thenReturn(new CityCommunityService.ConfirmView(
                new CommunityService.RegistrationView(r, a, UserMock.local(), "Primăria Arad"), false, 50));

        mvc.perform(post("/api/v1/city/registrations/confirm").with(AuthMock.as(4L, Role.CITY_HALL))
                .contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/community/confirm-code.json")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.status").value("CONFIRMED"))
           .andExpect(jsonPath("$.alreadyConfirmed").value(false))
           .andExpect(jsonPath("$.pointsAwarded").value(50))
           .andExpect(jsonPath("$.user.displayName").value("Ana Popescu"))
           .andExpect(jsonPath("$.confirmedBy").value("Primăria Arad"));
    }

    @Test
    void confirmCode_invalidCode_is400_unknownIs404() throws Exception {
        mvc.perform(post("/api/v1/city/registrations/confirm").with(AuthMock.as(4L, Role.CITY_HALL))
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"nu-e-cod\"}"))
           .andExpect(status().isBadRequest());

        when(service.confirmByCode(any(), any())).thenThrow(new NotFoundException("Unknown code AX-C1-ZZZZZZ"));
        mvc.perform(post("/api/v1/city/registrations/confirm").with(AuthMock.as(4L, Role.CITY_HALL))
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"AX-C1-ZZZZZZ\"}"))
           .andExpect(status().isNotFound());
    }

    @Test
    void cityRoutes_forLocal_are403() throws Exception {
        mvc.perform(get("/api/v1/city/actions").with(AuthMock.as(2L, Role.LOCAL))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/city/registrations/5/confirm").with(AuthMock.as(2L, Role.LOCAL))).andExpect(status().isForbidden());
    }

    @Test
    void create_returns201() throws Exception {
        CommunityAction a = CommunityMock.action(ActionStatus.DRAFT);
        when(service.create(any(), any())).thenReturn(new CommunityService.ActionView(a, List.of(), null));

        mvc.perform(post("/api/v1/city/actions").with(AuthMock.as(4L, Role.CITY_HALL))
                .contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/community/create-action.json")))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.status").value("DRAFT"))
           .andExpect(jsonPath("$.organizer").value("Direcția Spații Verzi"));
    }

    @Test
    void create_invalidBody_is400WithErrors() throws Exception {
        mvc.perform(post("/api/v1/city/actions").with(AuthMock.as(5L, Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\",\"pointsReward\":5}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors").isArray());
    }
}
