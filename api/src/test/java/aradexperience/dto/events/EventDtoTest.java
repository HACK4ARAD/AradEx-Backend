package aradexperience.dto.events;

import aradexperience.dto.DtoJson;
import aradexperience.enums.ContentStatus;
import aradexperience.enums.OrganizerType;
import aradexperience.enums.TicketType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** EventDto / EventCreate (introduse de T09 pentru `/business/events`). Round-trip-ul e în BusinessDtosTest. */
class EventDtoTest {

    private static final ObjectMapper MAPPER = DtoJson.MAPPER.copy().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void eventDto_readsSummaryAndDetailFields() throws Exception {
        EventDto dto = MAPPER.readValue(DtoJson.read("dto/events/event-dto.json"), EventDto.class);
        assertThat(dto.organizerType()).isEqualTo(OrganizerType.BUSINESS);
        assertThat(dto.ticketType()).isEqualTo(TicketType.IN_APP);
        assertThat(dto.isHot()).isFalse();
        assertThat(dto.spotsLeft()).isEqualTo(10);
        assertThat(dto.status()).isEqualTo(ContentStatus.PUBLISHED);
    }

    @Test
    void eventCreate_locationIsOptional() throws Exception {
        EventCreate req = MAPPER.readValue(DtoJson.read("dto/events/event-create.json"), EventCreate.class);
        assertThat(req.lat()).isNull();
        assertThat(req.capacity()).isEqualTo(10);
    }
}
