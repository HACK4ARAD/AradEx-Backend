package aradexperience.api;

import aradexperience.dto.ai.ChatRequest;
import aradexperience.dto.ai.ChatResponse;
import aradexperience.dto.itineraries.ItineraryDto;
import aradexperience.dto.itineraries.ItineraryRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * docs/API.md §AI (T12, T13): chatul cu Claude peste evenimentele din DB și planificatorul de itinerariu.
 * Implementat de `controller/ai/AiController`.
 */
@Tag(name = "AI")
@RequestMapping("/api/v1")
public interface AiApi {

    @Operation(summary = "T12: răspunde la o întrebare cu evenimente reale din următoarele 14 zile; offline=true dacă Claude nu răspunde; 429 peste 20/min de la aceeași adresă")
    @PostMapping("/ai/chat")
    ChatResponse chat(@Valid @RequestBody ChatRequest request);

    @Operation(summary = "T13: generează un itinerariu (nesalvat) din locurile, evenimentele zilei și ofertele din DB; offline=true dacă "
            + "Claude nu răspunde (planificatorul de rezervă); 429 peste 20/min de la aceeași adresă")
    @PostMapping("/ai/itinerary")
    ItineraryDto itinerary(@Valid @RequestBody ItineraryRequest request);
}
