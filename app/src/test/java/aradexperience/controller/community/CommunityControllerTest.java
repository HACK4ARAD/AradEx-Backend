package aradexperience.controller.community;

import aradexperience.controller.ControllerTest;
import aradexperience.entity.community.CommunityAction;
import aradexperience.enums.RegistrationStatus;
import aradexperience.enums.Role;
import aradexperience.exceptions.ConflictException;
import aradexperience.mock.AuthMock;
import aradexperience.mock.CommunityMock;
import aradexperience.service.community.CommunityService;
import aradexperience.service.points.PointsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ControllerTest(CommunityController.class)
class CommunityControllerTest {

    @Autowired MockMvc mvc;
    @MockBean CommunityService service;
    @MockBean PointsService points;

    @Test
    void actions_arePublic() throws Exception {
        CommunityAction a = CommunityMock.action(1L, 40, 18);
        when(service.list(eq(true), any())).thenReturn(List.of(new CommunityService.ActionView(a, List.of("Radu", "Elena"), null)));

        mvc.perform(get("/api/v1/community/actions"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].title").value("Plantăm copaci pe Faleza Mureșului"))
           .andExpect(jsonPath("$[0].category").value("ENVIRONMENT"))
           .andExpect(jsonPath("$[0].goingPreview[1]").value("Elena"))
           .andExpect(jsonPath("$[0].myRegistration").doesNotExist());
    }

    @Test
    void register_asLocal_returns201WithCode() throws Exception {
        CommunityAction a = CommunityMock.action(1L, 40, 19);
        when(service.register(eq(1L), any())).thenReturn(new CommunityService.RegistrationView(
                CommunityMock.registration(5L, 1L, 2L, RegistrationStatus.REGISTERED), a, null, null));

        mvc.perform(post("/api/v1/community/actions/1/register").with(AuthMock.as(2L, Role.LOCAL)))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.code").value("AX-C1-K4N7PA"))
           .andExpect(jsonPath("$.status").value("REGISTERED"))
           .andExpect(jsonPath("$.pointsReward").value(50));
    }

    @Test
    void register_full_is409ProblemDetail() throws Exception {
        when(service.register(eq(1L), any())).thenThrow(new ConflictException("This action is full"));

        mvc.perform(post("/api/v1/community/actions/1/register").with(AuthMock.as(1L, Role.VISITOR)))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.detail").value("This action is full"));
    }

    @Test
    void register_withoutToken_is401_asBusiness_is403() throws Exception {
        mvc.perform(post("/api/v1/community/actions/1/register")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/community/actions/1/register").with(AuthMock.as(3L, Role.BUSINESS))).andExpect(status().isForbidden());
    }

    @Test
    void cancel_returns204() throws Exception {
        mvc.perform(delete("/api/v1/community/actions/1/register").with(AuthMock.as(2L, Role.LOCAL))).andExpect(status().isNoContent());
        verify(service).cancel(eq(1L), any());
    }

    @Test
    void leaderboard_isPublic_badPeriodIs400() throws Exception {
        when(points.board(PointsService.Period.YEAR, null)).thenReturn(new PointsService.Board(PointsService.Period.YEAR, List.of(), null));
        mvc.perform(get("/api/v1/community/leaderboard?period=year"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.period").value("year"))
           .andExpect(jsonPath("$.me").doesNotExist());

        mvc.perform(get("/api/v1/community/leaderboard?period=week")).andExpect(status().isBadRequest());
    }

    @Test
    void myRegistrations_requiresLogin() throws Exception {
        mvc.perform(get("/api/v1/me/community/registrations")).andExpect(status().isUnauthorized());
    }
}
