package aradexperience.controller.volunteer;

import aradexperience.controller.ControllerTest;
import aradexperience.entity.community.CommunityRegistration;
import aradexperience.enums.RegistrationStatus;
import aradexperience.enums.Role;
import aradexperience.enums.VolunteerActionState;
import aradexperience.exceptions.ForbiddenException;
import aradexperience.exceptions.UnauthorizedException;
import aradexperience.mock.AuthMock;
import aradexperience.mock.CommunityMock;
import aradexperience.mock.JsonFixtures;
import aradexperience.mock.VolunteerMock;
import aradexperience.service.community.CityCommunityService;
import aradexperience.service.community.CommunityService;
import aradexperience.service.volunteer.CityVolunteerService;
import aradexperience.service.volunteer.VolunteerService;
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

@ControllerTest(VolunteerController.class)
class VolunteerControllerTest {

    @Autowired MockMvc mvc;
    @MockBean VolunteerService service;

    @Test
    void login_isPublic_andReturnsAToken() throws Exception {
        when(service.login(eq("482913"), any())).thenReturn(new VolunteerService.Session("jwt", VolunteerMock.volunteerUser()));
        mvc.perform(post("/api/v1/auth/volunteer").contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/volunteer/login.json")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.token").value("jwt"))
           .andExpect(jsonPath("$.user.role").value("VOLUNTEER"))
           .andExpect(jsonPath("$.user.displayName").value("Andrei Popa"));
    }

    @Test
    void login_wrongCode401_badFormat400() throws Exception {
        when(service.login(eq("482913"), any())).thenThrow(new UnauthorizedException("Invalid volunteer code"));
        mvc.perform(post("/api/v1/auth/volunteer").contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/volunteer/login.json")))
           .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/volunteer").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"12ab\"}"))
           .andExpect(status().isBadRequest());
    }

    @Test
    void actions_onlyForVolunteers() throws Exception {
        var a = VolunteerMock.actionAt(1L, CommunityMock.NOW.plusMinutes(20));
        when(service.actions(any())).thenReturn(List.of(new VolunteerService.ActionView(a, VolunteerActionState.OPEN, 2)));
        mvc.perform(get("/api/v1/vol/actions")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/vol/actions").with(AuthMock.as(2L, Role.LOCAL))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/vol/actions").with(AuthMock.as(VolunteerMock.VOL_ID, Role.VOLUNTEER)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].state").value("OPEN"))
           .andExpect(jsonPath("$[0].confirmedByMe").value(2))
           .andExpect(jsonPath("$[0].opensAt").exists());
    }

    @Test
    void scan_returnsTheConfirmation_or403WhenLocked() throws Exception {
        var a = CommunityMock.action(1L, 40, 18);
        CommunityRegistration r = CommunityMock.registration(5L, 1L, 2L, RegistrationStatus.CONFIRMED);
        var view = new CityCommunityService.ConfirmView(new CommunityService.RegistrationView(r, a, VolunteerMock.user(2L, "Ana Popescu", Role.LOCAL), "Andrei Popa"), false, 50);
        when(service.scan(any(), any())).thenReturn(view);
        mvc.perform(post("/api/v1/vol/scan").with(AuthMock.as(VolunteerMock.VOL_ID, Role.VOLUNTEER))
                .contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/volunteer/scan.json")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.pointsAwarded").value(50))
           .andExpect(jsonPath("$.confirmedBy").value("Andrei Popa"))
           .andExpect(jsonPath("$.user.displayName").value("Ana Popescu"));

        when(service.scan(any(), any())).thenThrow(new ForbiddenException("Scanning opens 30 minutes before the start"));
        mvc.perform(post("/api/v1/vol/scan").with(AuthMock.as(VolunteerMock.VOL_ID, Role.VOLUNTEER))
                .contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/volunteer/scan.json")))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.detail").value("Scanning opens 30 minutes before the start"));
        mvc.perform(post("/api/v1/vol/scan").with(AuthMock.as(VolunteerMock.VOL_ID, Role.VOLUNTEER))
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"AX-T1-K4N7PA\"}"))
           .andExpect(status().isBadRequest());
    }

    @Test
    void messages_sendIs201() throws Exception {
        when(service.send(any(), any())).thenReturn(new CityVolunteerService.MessageView(VolunteerMock.message(3, false, "Am ajuns la foișor."), "Andrei Popa"));
        mvc.perform(post("/api/v1/vol/messages").with(AuthMock.as(VolunteerMock.VOL_ID, Role.VOLUNTEER))
                .contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/volunteer/message.json")))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.fromCity").value(false))
           .andExpect(jsonPath("$.authorName").value("Andrei Popa"));
    }
}
