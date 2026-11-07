package aradexperience.controller.itineraries;

import aradexperience.controller.ControllerTest;
import aradexperience.enums.Role;
import aradexperience.exceptions.NotFoundException;
import aradexperience.mock.AuthMock;
import aradexperience.mock.ItineraryMock;
import aradexperience.mock.JsonFixtures;
import aradexperience.service.itineraries.ItinerariesService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ControllerTest(ItinerariesController.class)
class ItinerariesControllerTest {

    @Autowired MockMvc mvc;
    @MockBean ItinerariesService service;

    @Test
    void save_withoutAccount_is201_withIdAndShareCode() throws Exception {
        when(service.save(any(), eq(Optional.empty()), anyString())).thenReturn(ItineraryMock.dto(12L, "AX-I-7K3Q9Z"));

        mvc.perform(post("/api/v1/itineraries").contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/itineraries/save.json")))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.id").value(12))
           .andExpect(jsonPath("$.shareCode").value("AX-I-7K3Q9Z"))
           .andExpect(jsonPath("$.stops.length()").value(2))
           .andExpect(jsonPath("$.stops[0].arriveAt").value("2026-11-14T10:00:00+02:00"));
    }

    @Test
    void save_loggedIn_passesTheUser() throws Exception {
        when(service.save(any(), eq(Optional.of(7L)), anyString())).thenReturn(ItineraryMock.dto(12L, "AX-I-7K3Q9Z"));

        mvc.perform(post("/api/v1/itineraries").with(AuthMock.as(7, Role.VISITOR))
                        .contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/itineraries/save.json")))
           .andExpect(status().isCreated());
        verify(service).save(any(), eq(Optional.of(7L)), anyString());
    }

    @Test
    void save_withoutStops_is400() throws Exception {
        String body = JsonFixtures.read("requests/itineraries/save.json").replaceAll("(?s)\"stops\": \\[.*]", "\"stops\": []");
        mvc.perform(post("/api/v1/itineraries").contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isBadRequest());
    }

    @Test
    void get_isPublic_byShareCode_and404WhenMissing() throws Exception {
        when(service.get("AX-I-7K3Q9Z", Optional.empty())).thenReturn(ItineraryMock.dto(12L, "AX-I-7K3Q9Z"));
        when(service.get("99", Optional.empty())).thenThrow(new NotFoundException("Itinerary not found"));

        mvc.perform(get("/api/v1/itineraries/AX-I-7K3Q9Z"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.title").value("Jumătate de zi prin centrul istoric"))
           .andExpect(jsonPath("$.stops[1].name").value("Cetatea Aradului"));
        mvc.perform(get("/api/v1/itineraries/99")).andExpect(status().isNotFound());
    }

    @Test
    void mine_needsLogin() throws Exception {
        when(service.mine(7L)).thenReturn(List.of(ItineraryMock.dto(13L, "AX-I-BBBBBB"), ItineraryMock.dto(12L, "AX-I-AAAAAA")));

        mvc.perform(get("/api/v1/me/itineraries")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me/itineraries").with(AuthMock.as(7, Role.LOCAL)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].id").value(13))
           .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void get_byNumericId_passesTheLoggedInUser() throws Exception {
        when(service.get("12", Optional.of(7L))).thenReturn(ItineraryMock.dto(12L, "AX-I-7K3Q9Z"));

        mvc.perform(get("/api/v1/itineraries/12").with(AuthMock.as(7, Role.VISITOR))).andExpect(status().isOk());
        verify(service).get("12", Optional.of(7L));
    }

    @Test
    void saveToMine_needsLogin_andPassesTheUser() throws Exception {
        when(service.saveToMine("AX-I-7K3Q9Z", 7L)).thenReturn(ItineraryMock.dto(14L, "AX-I-CCCCCC"));

        mvc.perform(post("/api/v1/itineraries/AX-I-7K3Q9Z/save")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/itineraries/AX-I-7K3Q9Z/save").with(AuthMock.as(7, Role.VISITOR)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.id").value(14))
           .andExpect(jsonPath("$.shareCode").value("AX-I-CCCCCC"));
    }
}
