package aradexperience.controller.ai;

import aradexperience.controller.ControllerTest;
import aradexperience.dto.ai.ChatResponse;
import aradexperience.exceptions.TooManyRequestsException;
import aradexperience.mock.JsonFixtures;
import aradexperience.mock.ItineraryMock;
import aradexperience.service.ai.ChatService;
import aradexperience.service.ai.ItineraryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ControllerTest(AiController.class)
class AiControllerTest {

    @Autowired MockMvc mvc;
    @MockBean ChatService service;
    @MockBean ItineraryService itineraries;

    @Test
    void chat_isPublic_andReturnsReplyEventsAndChips() throws Exception {
        when(service.chat(any(), eq("9.9.9.9"))).thenReturn(new ChatResponse("Pentru ce zi cauți?", List.of(), List.of("Sâmbătă", "Duminică"), false));

        mvc.perform(post("/api/v1/ai/chat").header("X-Forwarded-For", "1.1.1.1, 9.9.9.9")
                        .contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/ai/chat.json")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.reply").value("Pentru ce zi cauți?"))
           .andExpect(jsonPath("$.quickReplies.length()").value(2))
           .andExpect(jsonPath("$.offline").value(false));
    }

    @Test
    void chat_emptyMessages_is400() throws Exception {
        mvc.perform(post("/api/v1/ai/chat").contentType(MediaType.APPLICATION_JSON).content("{\"messages\":[],\"locale\":\"ro\"}"))
           .andExpect(status().isBadRequest());
    }

    @Test
    void chat_overTheLimit_is429() throws Exception {
        when(service.chat(any(), any())).thenThrow(new TooManyRequestsException("Too many chat messages", 30));

        mvc.perform(post("/api/v1/ai/chat").contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/ai/chat.json")))
           .andExpect(status().isTooManyRequests());
    }

    @Test
    void itinerary_isPublic_andReturnsTheUnsavedPlan() throws Exception {
        when(itineraries.plan(any(), eq("9.9.9.9"))).thenReturn(ItineraryMock.dto(null, null));

        mvc.perform(post("/api/v1/ai/itinerary").header("X-Forwarded-For", "1.1.1.1, 9.9.9.9")
                        .contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/itineraries/plan.json")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.id").doesNotExist())
           .andExpect(jsonPath("$.shareCode").doesNotExist())
           .andExpect(jsonPath("$.ask").value("Jumătate de zi, pe jos, istorie"))
           .andExpect(jsonPath("$.stops[0].kind").value("PLACE"))
           .andExpect(jsonPath("$.offline").value(true));
    }

    @Test
    void itinerary_withoutDateOrWithBadTransport_is400() throws Exception {
        mvc.perform(post("/api/v1/ai/itinerary").contentType(MediaType.APPLICATION_JSON).content("{\"transport\":\"WALK\",\"hours\":4}"))
           .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/ai/itinerary").contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"2026-11-14\",\"transport\":\"BIKE\"}"))
           .andExpect(status().isBadRequest());
    }
}
