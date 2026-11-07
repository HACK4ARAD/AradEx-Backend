package aradexperience.controller.ai;

import aradexperience.api.AiApi;
import aradexperience.dto.ai.ChatRequest;
import aradexperience.dto.ai.ChatResponse;
import aradexperience.dto.itineraries.ItineraryDto;
import aradexperience.dto.itineraries.ItineraryRequest;
import aradexperience.service.ai.ChatService;
import aradexperience.service.ai.ItineraryService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import aradexperience.util.ClientIp;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AiController implements AiApi {

    private final ChatService chat;
    private final ItineraryService itineraries;
    private final HttpServletRequest request;

    @Override
    public ChatResponse chat(ChatRequest body) {
        return chat.chat(body, clientKey());
    }

    @Override
    public ItineraryDto itinerary(ItineraryRequest body) {
        return itineraries.plan(body, clientKey());
    }

    /** Adresa clientului pentru limita de mesaje (aceeași regulă ca la login: util/ClientIp). */
    private String clientKey() {
        return ClientIp.of(request);
    }
}
