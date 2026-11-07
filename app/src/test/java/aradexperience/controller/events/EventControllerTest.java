package aradexperience.controller.events;

import aradexperience.controller.ControllerTest;
import aradexperience.enums.Role;
import aradexperience.exceptions.NotFoundException;
import aradexperience.mock.AuthMock;
import aradexperience.mock.EventMock;
import aradexperience.mock.JsonFixtures;
import aradexperience.service.events.EventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ControllerTest(EventController.class)
class EventControllerTest {

    @Autowired MockMvc mvc;
    @MockBean EventService service;

    @Test
    void list_isPublic_andPaged() throws Exception {
        when(service.list(any(), any())).thenReturn(new PageImpl<>(List.of(EventMock.view(EventMock.agenda().get(0)))));
        mvc.perform(get("/api/v1/events").param("from", "2026-11-07").param("near", "46.18,21.31"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.content[0].id").value(1))
           .andExpect(jsonPath("$.content[0].isHot").value(false))
           .andExpect(jsonPath("$.content[0].distanceKm").value(0.4))
           .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void get_missing_is404() throws Exception {
        when(service.get(eq(99L), any(), any())).thenThrow(NotFoundException.of("Event", 99L));
        mvc.perform(get("/api/v1/events/99")).andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void save_withoutToken_is401_withToken_is204() throws Exception {
        mvc.perform(post("/api/v1/events/1/save")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/events/1/save").with(AuthMock.as(7L, Role.LOCAL))).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/events/1/save").with(AuthMock.as(7L, Role.LOCAL))).andExpect(status().isNoContent());
        verify(service).save(eq(1L), any());
        verify(service).unsave(eq(1L), any());
    }

    @Test
    void create_onlyCityHall() throws Exception {
        when(service.create(any(), any())).thenReturn(EventMock.view(EventMock.agenda().get(1)));
        mvc.perform(post("/api/v1/events").with(AuthMock.as(7L, Role.LOCAL)).contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/events/create.json")))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/events").with(AuthMock.as(5L, Role.CITY_HALL)).contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/events/create.json")))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.id").value(2))
           .andExpect(jsonPath("$.spotsLeft").value(120));
    }

    @Test
    void create_invalidBody_is400() throws Exception {
        mvc.perform(post("/api/v1/events").with(AuthMock.as(5L, Role.CITY_HALL)).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\"}"))
           .andExpect(status().isBadRequest());
    }

    @Test
    void savedEvents_requiresLogin() throws Exception {
        mvc.perform(get("/api/v1/me/saved-events")).andExpect(status().isUnauthorized());
        when(service.savedBy(any(), any())).thenReturn(List.of(EventMock.view(EventMock.agenda().get(2))));
        mvc.perform(get("/api/v1/me/saved-events").with(AuthMock.as(7L, Role.LOCAL))).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(3));
    }
}
