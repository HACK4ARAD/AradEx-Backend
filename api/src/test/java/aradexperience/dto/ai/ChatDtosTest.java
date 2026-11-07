package aradexperience.dto.ai;

import aradexperience.dto.DtoJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import static org.assertj.core.api.Assertions.assertThat;

/** T12: DTO-urile chatului (docs/API.md §AI). */
class ChatDtosTest {

    @Test
    void chatRequest_readsHistoryAndLocale() {
        ChatRequest r = DtoJson.fromResource("dto/ai/chat-request.json", ChatRequest.class);
        assertThat(r.messages()).hasSize(3);
        assertThat(r.messages().get(1).role()).isEqualTo("assistant");
        assertThat(r.locale()).isEqualTo("ro");
    }

    @Test
    void chatResponse_readsEventsChipsAndOffline() {
        ChatResponse r = DtoJson.fromResource("dto/ai/chat-response.json", ChatResponse.class);
        assertThat(r.suggestedEventIds()).containsExactly(4L, 7L);
        assertThat(r.quickReplies()).hasSize(2);
        assertThat(r.offline()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "dto/ai/chat-request.json, aradexperience.dto.ai.ChatRequest",
            "dto/ai/chat-response.json, aradexperience.dto.ai.ChatResponse",
    })
    void roundTrip_matchesJson(String json, Class<?> type) throws Exception {
        Object dto = DtoJson.fromResource(json, type);
        JSONAssert.assertEquals(DtoJson.read(json), DtoJson.MAPPER.writeValueAsString(dto), JSONCompareMode.STRICT);
    }
}
