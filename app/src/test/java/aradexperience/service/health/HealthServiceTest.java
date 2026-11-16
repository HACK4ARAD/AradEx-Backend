package aradexperience.service.health;

import aradexperience.exceptions.ServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthServiceTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final HealthService service = new HealthService(jdbc);

    @Test
    void check_databaseAnswers_passes() {
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);

        assertThatCode(service::check).doesNotThrowAnyException();
    }

    @Test
    void check_databaseRejectsConnection_is503() {
        // ex. parola bazei rotită de RDS: Postgres răspunde 28P01 și pool-ul nu mai poate deschide conexiuni
        when(jdbc.queryForObject("SELECT 1", Integer.class))
            .thenThrow(new CannotGetJdbcConnectionException("FATAL: password authentication failed"));

        assertThatThrownBy(service::check)
            .isInstanceOf(ServiceUnavailableException.class)
            .hasMessage("Database unavailable");
    }
}
