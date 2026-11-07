package aradexperience.controller.google;

import aradexperience.controller.ControllerTest;
import aradexperience.dto.google.GooglePlaceIdRequest;
import aradexperience.enums.Role;
import aradexperience.exceptions.ApiException;
import aradexperience.exceptions.NotFoundException;
import aradexperience.mock.AuthMock;
import aradexperience.mock.GoogleMock;
import aradexperience.mock.JsonFixtures;
import aradexperience.service.google.GoogleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ControllerTest(GoogleController.class)
class GoogleControllerTest {

    @Autowired MockMvc mvc;
    @MockBean GoogleService service;

    @Test
    void placeGoogle_isPublic() throws Exception {
        when(service.placeInfo(1L, null)).thenReturn(GoogleMock.info());
        mvc.perform(get("/api/v1/places/1/google"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.rating").value(4.6))
           .andExpect(jsonPath("$.userRatingCount").value(1240))
           .andExpect(jsonPath("$.reviews[0].author").value("Ana P."))
           .andExpect(jsonPath("$.reviews[0].authorPhotoUrl").doesNotExist())
           .andExpect(jsonPath("$.attribution").value("Powered by Google"));
    }

    @Test
    void businessGoogle_passesTheLanguage_notLinkedIs404() throws Exception {
        when(service.businessInfo(1L, "en")).thenThrow(new NotFoundException("Business 1 is not linked to Google"));
        mvc.perform(get("/api/v1/businesses/1/google").param("lang", "en"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.detail").value("Business 1 is not linked to Google"));
    }

    @Test
    void googleDown_is503ProblemDetail() throws Exception {
        when(service.placeInfo(eq(1L), isNull())).thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Google is not responding right now, try again later"));
        mvc.perform(get("/api/v1/places/1/google"))
           .andExpect(status().isServiceUnavailable())
           .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void cityLink_cityHallCanSet_othersGet403_guest401() throws Exception {
        when(service.setPlaceLink(eq(1L), any(GooglePlaceIdRequest.class))).thenReturn(GoogleMock.link());
        mvc.perform(put("/api/v1/city/places/1/google").with(AuthMock.as(4L, Role.CITY_HALL))
                .contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/google/link.json")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.googlePlaceId").value(GoogleMock.CETATE_GOOGLE_ID))
           .andExpect(jsonPath("$.configured").value(true));
        mvc.perform(put("/api/v1/city/places/1/google").with(AuthMock.as(3L, Role.BUSINESS))
                .contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/google/link.json")))
           .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/city/places/1/google")).andExpect(status().isUnauthorized());
    }

    @Test
    void cityLink_invalidId_is400() throws Exception {
        mvc.perform(put("/api/v1/city/places/1/google").with(AuthMock.as(4L, Role.CITY_HALL))
                .contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/google/link-invalid.json")))
           .andExpect(status().isBadRequest());
    }

    @Test
    void myLink_businessOnly() throws Exception {
        when(service.myLink(any())).thenReturn(GoogleMock.link());
        mvc.perform(get("/api/v1/business/me/google").with(AuthMock.as(3L, Role.BUSINESS)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.info.reviews[0].text").value("Superb"));
        mvc.perform(get("/api/v1/business/me/google").with(AuthMock.as(2L, Role.LOCAL)))
           .andExpect(status().isForbidden());
    }

    @Test
    void setMyLink_savesForTheCurrentBusiness() throws Exception {
        when(service.setMyLink(any(), any(GooglePlaceIdRequest.class))).thenReturn(GoogleMock.link());
        mvc.perform(put("/api/v1/business/me/google").with(AuthMock.as(3L, Role.BUSINESS))
                .contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/google/link.json")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.googlePlaceId").value(GoogleMock.CETATE_GOOGLE_ID));
    }
}
