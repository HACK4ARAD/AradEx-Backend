package aradexperience.controller.places;

import aradexperience.controller.ControllerTest;
import aradexperience.exceptions.NotFoundException;
import aradexperience.mock.PlaceMock;
import aradexperience.service.places.PlaceService;
import aradexperience.service.places.PlaceService.PlaceDetail;
import aradexperience.service.places.PlaceService.PlaceItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ControllerTest(PlacesController.class)
class PlacesControllerTest {

    @Autowired MockMvc mvc;
    @MockBean PlaceService service;

    @Test
    void list_isPublic_andMapsGuideBadge() throws Exception {
        when(service.list(any(), any(), any(), any())).thenReturn(List.of(new PlaceItem(PlaceMock.cetatea(), 3, 250, 1.2)));
        mvc.perform(get("/api/v1/places").param("tags", "istorie").param("near", "46.17,21.31"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].name").value("Cetatea Aradului"))
           .andExpect(jsonPath("$[0].tags[0]").value("istorie"))
           .andExpect(jsonPath("$[0].guidePointsCount").value(3))
           .andExpect(jsonPath("$[0].guideMinutes").value(5))
           .andExpect(jsonPath("$[0].distanceKm").value(1.2));
    }

    @Test
    void list_unknownType_is400() throws Exception {
        mvc.perform(get("/api/v1/places").param("type", "CASTLE")).andExpect(status().isBadRequest());
    }

    @Test
    void get_returnsGuidePoints_withoutDistance() throws Exception {
        when(service.get(eq(1L), isNull())).thenReturn(new PlaceDetail(PlaceMock.cetatea(),
                List.of(PlaceMock.point(11, 1, 1, 60), PlaceMock.point(12, 1, 2, 90)), null));
        mvc.perform(get("/api/v1/places/1"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.exteriorOnly").value(true))
           .andExpect(jsonPath("$.guidePoints.length()").value(2))
           .andExpect(jsonPath("$.guidePoints[1].position").value(2))
           .andExpect(jsonPath("$.distanceKm").doesNotExist());
    }

    @Test
    void get_unknown_is404_asProblemDetail() throws Exception {
        when(service.get(eq(99L), any())).thenThrow(NotFoundException.of("Place", 99));
        mvc.perform(get("/api/v1/places/99"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.detail").value("Place 99 not found"));
    }
}
