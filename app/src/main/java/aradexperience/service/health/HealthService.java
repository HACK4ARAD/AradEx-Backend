package aradexperience.service.health;

import aradexperience.exceptions.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * /health verifică și baza de date. Load balancer-ul întreabă ruta la 15 s; un 503 face task-ul „unhealthy”
 * și ECS pornește altul. Așa se repară singur și după ce RDS rotește parola din Secrets Manager (la 7 zile):
 * task-ul nou citește parola curentă, pe când cel vechi rămâne cu cea de la pornire (28P01).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthService {

    private final JdbcTemplate jdbc;

    public void check() {
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
        } catch (DataAccessException e) {
            log.warn("Health: database unavailable ({})", e.getClass().getSimpleName());
            throw new ServiceUnavailableException("Database unavailable");
        }
    }
}
