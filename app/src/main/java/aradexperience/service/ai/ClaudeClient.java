package aradexperience.service.ai;

import aradexperience.dto.ai.ChatMessageDto;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.TextBlock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * T12 · apelul Claude (Anthropic API, SDK-ul oficial Java): istoricul conversației + promptul de sistem, cu răspunsul forțat
 * la JSON-ul `{reply, suggestedEventIds, quickReplies}` (structured outputs). Timeout 20 s, o reîncercare, `maxTokens` explicit.
 * Orice eroare (cheie lipsă sau greșită, timeout, limită) → `Optional.empty()`: serviciul trece pe răspunsurile offline.
 */
@Slf4j
@Component
public class ClaudeClient {

    static final Duration TIMEOUT = Duration.ofSeconds(20);
    static final int MAX_RETRIES = 1;
    static final long MAX_TOKENS = 1024;

    /** Schema răspunsului: textul, id-urile evenimentelor din catalog, chips de răspuns rapid. */
    static final JsonOutputFormat.Schema SCHEMA = JsonOutputFormat.Schema.builder()
            .putAdditionalProperty("type", JsonValue.from("object"))
            .putAdditionalProperty("properties", JsonValue.from(Map.of(
                    "reply", Map.of("type", "string"),
                    "suggestedEventIds", Map.of("type", "array", "items", Map.of("type", "integer")),
                    "quickReplies", Map.of("type", "array", "items", Map.of("type", "string")))))
            .putAdditionalProperty("required", JsonValue.from(List.of("reply", "suggestedEventIds", "quickReplies")))
            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
            .build();

    private final AnthropicClient client;
    private final String model;

    public ClaudeClient(@Value("${app.anthropic.api-key:}") String apiKey, @Value("${app.anthropic.model:claude-haiku-5-5}") String model) {
        this.model = model;
        this.client = apiKey == null || apiKey.isBlank() ? null
                : AnthropicOkHttpClient.builder().apiKey(apiKey.trim()).timeout(TIMEOUT).maxRetries(MAX_RETRIES).build();
        if (client == null) log.warn("ANTHROPIC_API_KEY is not set: the chat answers offline");
    }

    public boolean configured() {
        return client != null;
    }

    /** Textul JSON al răspunsului, sau gol dacă Claude nu e disponibil. `history` începe cu un mesaj al omului. */
    public Optional<String> complete(String system, List<ChatMessageDto> history) {
        if (client == null || history.isEmpty()) return Optional.empty();
        try {
            MessageCreateParams.Builder b = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(MAX_TOKENS)
                    .system(system)
                    .outputConfig(OutputConfig.builder().format(JsonOutputFormat.builder().schema(SCHEMA).build()).build());
            for (ChatMessageDto m : history) {
                if ("assistant".equals(m.role())) b.addAssistantMessage(m.content());
                else b.addUserMessage(m.content());
            }
            Message msg = client.messages().create(b.build());
            String text = msg.content().stream().flatMap(c -> c.text().stream()).map(TextBlock::text).collect(Collectors.joining());
            return text.isBlank() ? Optional.empty() : Optional.of(text);
        } catch (RuntimeException e) {
            log.warn("Claude call failed ({}): answering offline", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
