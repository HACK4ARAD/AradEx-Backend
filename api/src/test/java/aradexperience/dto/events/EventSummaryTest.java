package aradexperience.dto.events;

import aradexperience.dto.DtoJson;
import aradexperience.enums.EventCategory;
import aradexperience.enums.TicketType;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EventSummary = docs/API.md; `isHot` / `isDemo` trebuie să rămână cu prefixul `is` în JSON.
 */
class EventSummaryTest {

    private static final String JSON = "dto/events/event-summary.json";

    @Test
    void deserialize_readsAllFields() {
        EventSummary dto = DtoJson.fromResource(JSON, EventSummary.class);
        assertThat(dto.id()).isEqualTo(22L);
        assertThat(dto.category()).isEqualTo(EventCategory.FAMILY);
        assertThat(dto.ticketType()).isEqualTo(TicketType.IN_APP);
        assertThat(dto.isHot()).isTrue();
        assertThat(dto.isDemo()).isTrue();
        assertThat(dto.distanceKm()).isEqualTo(0.4);
        assertThat(dto.ratingAvg()).isNull();
    }

    @Test
    void serialize_roundTrip_matchesJson() throws Exception {
        EventSummary dto = DtoJson.fromResource(JSON, EventSummary.class);
        JSONAssert.assertEquals(DtoJson.read(JSON), DtoJson.APP.writeValueAsString(dto), JSONCompareMode.STRICT);
    }
}
