package aradexperience.api;

import aradexperience.dto.ai.ChatRequest;
import aradexperience.dto.ai.ChatResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/** docs/API.md §AI (T12): chatul cu Claude peste evenimentele din DB. Implementat de `controller/ai/AiController`. */
@Tag(name = "AI")
@RequestMapping("/api/v1")
public interface AiApi {

    @Operation(summary = "T12: răspunde la o întrebare cu evenimente reale din următoarele 14 zile; offline=true dacă Claude nu răspunde; 429 peste 20/min de la aceeași adresă")
    @PostMapping("/ai/chat")
    ChatResponse chat(@Valid @RequestBody ChatRequest request);
}
