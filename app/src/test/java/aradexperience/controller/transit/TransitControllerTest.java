package aradexperience.controller.transit;

import aradexperience.controller.ControllerTest;
import aradexperience.dto.transit.StopArrivalsDto;
import aradexperience.dto.transit.TransitArrivalDto;
import aradexperience.dto.transit.TransitStopDto;
import aradexperience.dto.transit.TransitVehiclesDto;
import aradexperience.enums.TransitMode;
import aradexperience.exceptions.BadRequestException;
import aradexperience.exceptions.NotFoundException;
import aradexperience.mock.TransitMock;
import aradexperience.service.transit.TransitService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ControllerTest(TransitController.class)
class TransitControllerTest {

    static final TransitStopDto PODGORIA = new TransitStopDto(120, "Podgoria", "120", 46.18338, 21.32509, List.of(11), List.of("1"));

    @Autowired MockMvc mvc;
    @MockBean TransitService service;

    @Test
    void stops_isPublic() throws Exception {
        when(service.stops()).thenReturn(List.of(PODGORIA));
        mvc.perform(get("/api/v1/transit/stops"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].name").value("Podgoria"))
           .andExpect(jsonPath("$[0].routeIds[0]").value(11));
    }

    @Test
    void vehicles_sourceDown_is200WithLiveFalse() throws Exception {
        when(service.vehicles()).thenReturn(new TransitVehiclesDto(false, null, "Date: CTP Arad / Telelink", List.of()));
        mvc.perform(get("/api/v1/transit/vehicles"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.live").value(false))
           .andExpect(jsonPath("$.attribution").value("Date: CTP Arad / Telelink"))
           .andExpect(jsonPath("$.vehicles.length()").value(0))
           .andExpect(jsonPath("$.updatedAt").doesNotExist());
    }

    @Test
    void nearest_returnsStopDistanceAndArrivals() throws Exception {
        TransitArrivalDto a = new TransitArrivalDto(11, "1", TransitMode.TRAM, "#b80000", "Piata Romana", 120,
                TransitMock.NOW.plusSeconds(240).atOffset(ZoneOffset.UTC), 4, 0, true);
        when(service.nearest(46.1835, 21.3252)).thenReturn(new StopArrivalsDto(PODGORIA, 0.1, true,
                TransitMock.NOW.atOffset(ZoneOffset.UTC), "Date: CTP Arad / Telelink", List.of(a)));
        mvc.perform(get("/api/v1/transit/nearest").param("lat", "46.1835").param("lng", "21.3252"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.stop.id").value(120))
           .andExpect(jsonPath("$.distanceKm").value(0.1))
           .andExpect(jsonPath("$.arrivals[0].routeShortName").value("1"))
           .andExpect(jsonPath("$.arrivals[0].minutes").value(4))
           .andExpect(jsonPath("$.arrivals[0].at").value("2026-11-07T15:16:10Z"));
    }

    @Test
    void nearest_withoutCoordinates_is400() throws Exception {
        when(service.nearest(isNull(), isNull())).thenThrow(new BadRequestException("lat and lng are required (WGS84 degrees)"));
        mvc.perform(get("/api/v1/transit/nearest"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.detail").value("lat and lng are required (WGS84 degrees)"));
    }

    @Test
    void arrivals_unknownStop_is404() throws Exception {
        when(service.arrivals(5)).thenThrow(NotFoundException.of("Stop", 5));
        mvc.perform(get("/api/v1/transit/stops/5/arrivals"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.detail").value("Stop 5 not found"));
    }

    @Test
    void arrivals_nonNumericId_is400() throws Exception {
        mvc.perform(get("/api/v1/transit/stops/abc/arrivals")).andExpect(status().isBadRequest());
    }
}
