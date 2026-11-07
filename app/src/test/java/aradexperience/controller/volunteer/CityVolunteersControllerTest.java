package aradexperience.controller.volunteer;

import aradexperience.controller.ControllerTest;
import aradexperience.enums.Role;
import aradexperience.exceptions.BadRequestException;
import aradexperience.mock.AuthMock;
import aradexperience.mock.JsonFixtures;
import aradexperience.mock.VolunteerMock;
import aradexperience.service.volunteer.CityVolunteerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ControllerTest(CityVolunteersController.class)
class CityVolunteersControllerTest {

    @Autowired MockMvc mvc;
    @MockBean CityVolunteerService service;

    private CityVolunteerService.VolunteerView view() {
        return new CityVolunteerService.VolunteerView(VolunteerMock.volunteer(1L, 2L), VolunteerMock.volunteerUser(), VolunteerMock.coordinator(),
                2, false);
    }

    @Test
    void create_asCityHall_is201_withTheCode() throws Exception {
        when(service.create(any(), any())).thenReturn(view());
        mvc.perform(post("/api/v1/city/volunteers").with(AuthMock.as(4L, Role.CITY_HALL))
                .contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/volunteer/create.json")))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.loginCode").value("482913"))
           .andExpect(jsonPath("$.coordinatorName").value("Ioana Marin"))
           .andExpect(jsonPath("$.actionIds.length()").value(2))
           .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void create_invalid400_withFieldErrors_andBusinessErrors400() throws Exception {
        mvc.perform(post("/api/v1/city/volunteers").with(AuthMock.as(5L, Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/volunteer/create-invalid.json")))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors.length()").value(2));
        when(service.create(any(), any())).thenThrow(new BadRequestException("Action 4 is not published"));
        mvc.perform(post("/api/v1/city/volunteers").with(AuthMock.as(4L, Role.CITY_HALL))
                .contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/volunteer/create.json")))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.detail").value("Action 4 is not published"));
    }

    @Test
    void cityRoutes_closedToVolunteersAndLocals() throws Exception {
        mvc.perform(get("/api/v1/city/volunteers")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/city/volunteers").with(AuthMock.as(VolunteerMock.VOL_ID, Role.VOLUNTEER))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/city/volunteers").with(AuthMock.as(2L, Role.LOCAL))).andExpect(status().isForbidden());
        when(service.list()).thenReturn(List.of(view()));
        mvc.perform(get("/api/v1/city/volunteers").with(AuthMock.as(4L, Role.CITY_HALL)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].scans").value(2));
    }

    @Test
    void suspend_andCoordinators_noCityMessages() throws Exception {
        when(service.update(anyLong(), any())).thenReturn(view());
        mvc.perform(patch("/api/v1/city/volunteers/203").with(AuthMock.as(4L, Role.CITY_HALL))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SUSPENDED\"}"))
           .andExpect(status().isOk());
        // Primăria nu mai are mesaje cu voluntarii (chatul e doar între voluntarii unei acțiuni)
        mvc.perform(get("/api/v1/city/volunteers/203/messages").with(AuthMock.as(4L, Role.CITY_HALL))).andExpect(status().isNotFound());
        when(service.coordinators()).thenReturn(List.of(VolunteerMock.coordinator()));
        mvc.perform(get("/api/v1/city/coordinators").with(AuthMock.as(4L, Role.CITY_HALL)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].name").value("Ioana Marin"));
    }
}
