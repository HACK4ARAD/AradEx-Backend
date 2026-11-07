package aradexperience.mapper.tickets;

import aradexperience.dto.tickets.TicketRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static aradexperience.mapper.tickets.TicketMapper.toEntity;
import static org.assertj.core.api.Assertions.assertThat;

class TicketMapperTest {

    @Test
    void toEntity_normalizesEmail() {
        assertThat(toEntity(new TicketRequest(1, 0, " Ana@Exemplu.RO "), 1L, 2L, BigDecimal.ZERO).getEmail()).isEqualTo("ana@exemplu.ro");
    }

    @Test
    void toEntity_withoutEmail_storesNull_T21PhoneAccounts() {
        assertThat(toEntity(new TicketRequest(1, 0, null), 1L, 2L, BigDecimal.ZERO).getEmail()).isNull();
        assertThat(toEntity(new TicketRequest(1, 0, "  "), 1L, 2L, BigDecimal.ZERO).getEmail()).isNull();
    }
}
