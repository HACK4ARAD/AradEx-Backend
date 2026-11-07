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

    /** Adresa clientului pentru limita de mesaje: în spatele ALB-ului, ultima valoare din X-Forwarded-For (o adaugă ALB-ul). */
    private String clientKey() {
        String fwd = request.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            String[] parts = fwd.split(",");
            return parts[parts.length - 1].trim();
        }
        return request.getRemoteAddr();
    }
}
