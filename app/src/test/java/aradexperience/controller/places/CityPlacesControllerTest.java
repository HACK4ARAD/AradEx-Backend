package aradexperience.controller.places;

import aradexperience.controller.ControllerTest;
import aradexperience.enums.Role;
import aradexperience.exceptions.ConflictException;
import aradexperience.mock.AuthMock;
import aradexperience.mock.JsonFixtures;
import aradexperience.mock.PlaceMock;
import aradexperience.service.places.CityPlaceService;
import aradexperience.service.places.PlaceService.PlaceDetail;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ControllerTest(CityPlacesController.class)
class CityPlacesControllerTest {

    @Autowired MockMvc mvc;
    @MockBean CityPlaceService service;

    @Test
    void create_asCityHall_is201() throws Exception {
        when(service.create(any())).thenReturn(new PlaceDetail(PlaceMock.palatulCultural(), List.of(), null));
        mvc.perform(post("/api/v1/city/places").with(AuthMock.as(4L, Role.CITY_HALL))
                .contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/places/place.json")))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.id").value(2))
           .andExpect(jsonPath("$.guidePoints.length()").value(0));
    }

    @Test
    void create_invalidBody_is400_withFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/city/places").with(AuthMock.as(5L, Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/places/place-invalid.json")))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void create_withoutToken_is401_wrongRole_is403() throws Exception {
        String body = JsonFixtures.read("requests/places/place.json");
        mvc.perform(post("/api/v1/city/places").contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/city/places").with(AuthMock.as(2L, Role.LOCAL)).contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isForbidden());
    }

    @Test
    void update_slugConflict_is409() throws Exception {
        when(service.update(eq(1L), any())).thenThrow(new ConflictException("Slug 'x' is already used"));
        mvc.perform(put("/api/v1/city/places/1").with(AuthMock.as(4L, Role.CITY_HALL))
                .contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/places/place.json")))
           .andExpect(status().isConflict());
    }

    @Test
    void delete_is204() throws Exception {
        mvc.perform(delete("/api/v1/city/places/1").with(AuthMock.as(4L, Role.CITY_HALL))).andExpect(status().isNoContent());
        verify(service).delete(1L);
    }

    @Test
    void guidePoints_addIs201_updateIs200_deleteIs204() throws Exception {
        String body = JsonFixtures.read("requests/places/guide-point.json");
        when(service.addGuidePoint(eq(11L), any())).thenReturn(PlaceMock.point(40, 11, 4, 20));
        when(service.updateGuidePoint(eq(11L), eq(40L), any())).thenReturn(PlaceMock.point(40, 11, 1, 20));

        mvc.perform(post("/api/v1/city/places/11/guide-points").with(AuthMock.as(4L, Role.CITY_HALL))
                .contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isCreated()).andExpect(jsonPath("$.position").value(4));
        mvc.perform(put("/api/v1/city/places/11/guide-points/40").with(AuthMock.as(4L, Role.CITY_HALL))
                .contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isOk()).andExpect(jsonPath("$.position").value(1));
        mvc.perform(delete("/api/v1/city/places/11/guide-points/40").with(AuthMock.as(4L, Role.CITY_HALL)))
           .andExpect(status().isNoContent());
        verify(service).deleteGuidePoint(11L, 40L);
    }

    @Test
    void deleteGuidePoint_asVisitor_is403() throws Exception {
        doThrow(new IllegalStateException("should not be called")).when(service).deleteGuidePoint(any(Long.class), any(Long.class));
        mvc.perform(delete("/api/v1/city/places/11/guide-points/40").with(AuthMock.as(1L, Role.VISITOR))).andExpect(status().isForbidden());
    }
}
