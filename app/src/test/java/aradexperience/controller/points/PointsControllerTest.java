package aradexperience.controller.points;

import aradexperience.controller.ControllerTest;
import aradexperience.enums.Role;
import aradexperience.mock.AuthMock;
import aradexperience.service.points.PointsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ControllerTest(PointsController.class)
class PointsControllerTest {

    @Autowired MockMvc mvc;
    @MockBean PointsService service;

    @Test
    void myPoints_returnsSummary() throws Exception {
        when(service.summary(any())).thenReturn(new PointsService.Summary(270, 50, 16, 2, List.of()));

        mvc.perform(get("/api/v1/me/points").with(AuthMock.as(2L, Role.LOCAL)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.balance").value(270))
           .andExpect(jsonPath("$.rank").value(16))
           .andExpect(jsonPath("$.streakMonths").value(2));
    }

    @Test
    void myPoints_withoutToken_is401() throws Exception {
        mvc.perform(get("/api/v1/me/points")).andExpect(status().isUnauthorized());
    }
}
