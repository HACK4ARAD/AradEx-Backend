package aradexperience.dto.tickets;

import aradexperience.dto.DtoJson;
import aradexperience.enums.TicketStatus;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import static org.assertj.core.api.Assertions.assertThat;

class TicketDtoTest {

    private static final String JSON = "dto/tickets/ticket-dto.json";

    @Test
    void deserialize_readsAllFields() {
        TicketDto dto = DtoJson.fromResource(JSON, TicketDto.class);
        assertThat(dto.code()).isEqualTo("AX-T22-K8D2QF");
        assertThat(dto.total()).isEqualTo(65.0);
        assertThat(dto.status()).isEqualTo(TicketStatus.CONFIRMED);
        assertThat(dto.usedAt()).isNull();
    }

    @Test
    void serialize_roundTrip_matchesJson() throws Exception {
        TicketDto dto = DtoJson.fromResource(JSON, TicketDto.class);
        JSONAssert.assertEquals(DtoJson.read(JSON), DtoJson.APP.writeValueAsString(dto), JSONCompareMode.STRICT);
    }
}
