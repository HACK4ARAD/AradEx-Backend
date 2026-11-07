package aradexperience.controller.business;

import aradexperience.controller.ControllerTest;
import aradexperience.entity.events.Event;
import aradexperience.enums.ContentStatus;
import aradexperience.enums.Role;
import aradexperience.exceptions.ForbiddenException;
import aradexperience.exceptions.NotFoundException;
import aradexperience.mock.AuthMock;
import aradexperience.mock.BusinessMock;
import aradexperience.mock.JsonFixtures;
import aradexperience.service.business.BusinessService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static aradexperience.mock.BusinessMock.OWNER_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ControllerTest(BusinessController.class)
class BusinessControllerTest {

    @Autowired MockMvc mvc;
    @MockBean BusinessService service;

    @Test
    void publicPage_withoutToken_returnsProfileEventsAnnouncements() throws Exception {
        when(service.page(1L)).thenReturn(new BusinessService.Page(BusinessMock.pasaj(),
                List.of(BusinessMock.event(24L, 1L)), List.of(BusinessMock.announcement(1L, 1L))));

        mvc.perform(get("/api/v1/businesses/1"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.business.name").value("Cafeneaua din Pasaj"))
           .andExpect(jsonPath("$.business.viewsCount").value(1240))
           .andExpect(jsonPath("$.offers.length()").value(0))
           .andExpect(jsonPath("$.events[0].organizerName").value("Cafeneaua din Pasaj"))
           .andExpect(jsonPath("$.events[0].isHot").value(true))
           .andExpect(jsonPath("$.announcements[0].title").value("Program prelungit de Black Friday"));
    }

    @Test
    void publicPage_unknown_is404ProblemDetail() throws Exception {
        when(service.page(42L)).thenThrow(NotFoundException.of("Business", 42));

        mvc.perform(get("/api/v1/businesses/42"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.detail").value("Business 42 not found"));
    }

    @Test
    void me_asBusiness_returnsStats() throws Exception {
        when(service.dashboard(any())).thenReturn(new BusinessService.Dashboard(BusinessMock.pasaj(), 0, 0, 2));

        mvc.perform(get("/api/v1/business/me").with(AuthMock.as(OWNER_ID, Role.BUSINESS)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.business.verified").value(true))
           .andExpect(jsonPath("$.stats.views").value(1240))
           .andExpect(jsonPath("$.stats.upcomingEvents").value(2))
           .andExpect(jsonPath("$.stats.codesUsed").value(0));
    }

    @Test
    void me_asLocal_is403_withoutToken_is401() throws Exception {
        mvc.perform(get("/api/v1/business/me").with(AuthMock.as(2L, Role.LOCAL))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/business/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void updateMe_returnsProfile() throws Exception {
        when(service.updateMe(any(), any())).thenReturn(BusinessMock.pasaj());

        mvc.perform(put("/api/v1/business/me").with(AuthMock.as(OWNER_ID, Role.BUSINESS))
                .contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/business/update-me.json")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.address").value("Str. Vasile Alecsandri 1, Arad"));
    }

    @Test
    void createEvent_returns201WithEventDto() throws Exception {
        Event e = BusinessMock.event(31L, 1L);
        e.setTitle("Atelier latte art");
        e.setPriceFrom(new BigDecimal("40"));
        e.setStatus(ContentStatus.DRAFT);
        when(service.createEvent(any(), any())).thenReturn(e);

        mvc.perform(post("/api/v1/business/events").with(AuthMock.as(OWNER_ID, Role.BUSINESS))
                .contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/business/create-event.json")))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.id").value(31))
           .andExpect(jsonPath("$.status").value("DRAFT"))
           .andExpect(jsonPath("$.organizerBusinessId").value(1))
           .andExpect(jsonPath("$.spotsLeft").value(18))
           .andExpect(jsonPath("$.priceFrom").value(40));
    }

    @Test
    void createEvent_invalidBody_is400WithFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/business/events").with(AuthMock.as(OWNER_ID, Role.BUSINESS))
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\",\"description\":\"D\",\"category\":\"FOOD\",\"ticketType\":\"FREE\"}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void createEvent_asLocal_is403() throws Exception {
        mvc.perform(post("/api/v1/business/events").with(AuthMock.as(2L, Role.LOCAL))
                .contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/business/create-event.json")))
           .andExpect(status().isForbidden());
    }

    @Test
    void updateEvent_ofAnotherBusiness_is403ProblemDetail() throws Exception {
        when(service.updateEvent(any(), eq(24L), any())).thenThrow(new ForbiddenException("Event 24 belongs to another organizer"));

        mvc.perform(put("/api/v1/business/events/24").with(AuthMock.as(14L, Role.BUSINESS))
                .contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/business/create-event.json")))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.detail").value("Event 24 belongs to another organizer"));
    }

    @Test
    void deleteEvent_returns204_and403ForOthers() throws Exception {
        mvc.perform(delete("/api/v1/business/events/24").with(AuthMock.as(OWNER_ID, Role.BUSINESS))).andExpect(status().isNoContent());
        verify(service).deleteEvent(any(), eq(24L));

        doThrow(new ForbiddenException("Event 25 belongs to another organizer")).when(service).deleteEvent(any(), eq(25L));
        mvc.perform(delete("/api/v1/business/events/25").with(AuthMock.as(14L, Role.BUSINESS))).andExpect(status().isForbidden());
    }

    @Test
    void myEvents_listsEventDtos() throws Exception {
        when(service.myEvents(any())).thenReturn(List.of(BusinessMock.event(24L, 1L)));

        mvc.perform(get("/api/v1/business/events").with(AuthMock.as(OWNER_ID, Role.BUSINESS)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].id").value(24))
           .andExpect(jsonPath("$[0].status").value("PUBLISHED"));
    }

    @Test
    void createAnnouncement_returns201_andListWorks() throws Exception {
        when(service.createAnnouncement(any(), any())).thenReturn(BusinessMock.announcement(5L, 1L));
        when(service.myAnnouncements(any())).thenReturn(List.of(BusinessMock.announcement(5L, 1L)));

        mvc.perform(post("/api/v1/business/announcements").with(AuthMock.as(OWNER_ID, Role.BUSINESS))
                .contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/business/create-announcement.json")))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.id").value(5))
           .andExpect(jsonPath("$.status").value("PUBLISHED"));
        mvc.perform(get("/api/v1/business/announcements").with(AuthMock.as(OWNER_ID, Role.BUSINESS)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void createAnnouncement_blankBody_is400() throws Exception {
        mvc.perform(post("/api/v1/business/announcements").with(AuthMock.as(OWNER_ID, Role.BUSINESS))
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Program\",\"body\":\" \"}"))
           .andExpect(status().isBadRequest());
    }

    @Test
    void deleteAnnouncement_returns204() throws Exception {
        mvc.perform(delete("/api/v1/business/announcements/5").with(AuthMock.as(OWNER_ID, Role.BUSINESS))).andExpect(status().isNoContent());
    }

    @Test
    void activity_returnsItems() throws Exception {
        when(service.activity(any())).thenReturn(new aradexperience.dto.business.BusinessActivityDto(List.of()));

        mvc.perform(get("/api/v1/business/activity").with(AuthMock.as(OWNER_ID, Role.BUSINESS)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.items.length()").value(0));
    }
}
