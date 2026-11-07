package aradexperience.config;

import aradexperience.controller.ControllerTest;
import aradexperience.controller.health.HealthController;
import aradexperience.enums.Role;
import aradexperience.mock.AuthMock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Matricea rute × roluri din docs/API.md, pe SecurityConfig-ul real. Controllerele feature-urilor nu există încă,
 * deci o cerere care TRECE de securitate ajunge la „Route not found” (404); una oprită dă 401 (fără token) sau 403 (rol greșit).
 */
@ControllerTest(HealthController.class)
class SecurityRulesTest {

    private static final int ALLOWED = 404;

    @Autowired MockMvc mvc;

    @ParameterizedTest(name = "{0} {1} fără token → public")
    @CsvSource({
            "GET, /api/v1/places", "GET, /api/v1/places/1", "GET, /api/v1/events", "GET, /api/v1/events/1/ics",
            "GET, /api/v1/offers/3", "GET, /api/v1/community/actions", "GET, /api/v1/community/leaderboard",
            "GET, /api/v1/businesses/1", "GET, /api/v1/itineraries/AB12CD", "GET, /api/v1/transit/stops/1/arrivals",
            "POST, /api/v1/ai/chat", "POST, /api/v1/ai/itinerary", "POST, /api/v1/itineraries", "POST, /api/v1/auth/volunteer",
    })
    void publicRoutes_withoutToken_passSecurity(String method, String path) throws Exception {
        mvc.perform(req(method, path)).andExpect(status().is(ALLOWED));
    }

    @ParameterizedTest(name = "{0} {1} fără token → 401")
    @CsvSource({
            "GET, /api/v1/me/points", "GET, /api/v1/me/tickets", "POST, /api/v1/events/1/save", "POST, /api/v1/events/1/tickets",
            "POST, /api/v1/events/1/reviews", "POST, /api/v1/offers/1/redeem", "POST, /api/v1/community/actions/1/register",
            "DELETE, /api/v1/community/actions/1/register", "GET, /api/v1/tickets/AX-T1-ABCDEF", "POST, /api/v1/ai/tts",
            "POST, /api/v1/events", "GET, /api/v1/business/me", "GET, /api/v1/city/actions", "GET, /api/v1/admin/stats",
            "GET, /api/v1/vol/actions", "POST, /api/v1/vol/scan", "GET, /api/v1/city/volunteers",
    })
    void protectedRoutes_withoutToken_are401(String method, String path) throws Exception {
        mvc.perform(req(method, path))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.status").value(401))
           .andExpect(jsonPath("$.instance").value(path));
    }

    @ParameterizedTest(name = "{2} → {0} {1} = {3}")
    @CsvSource({
            // Primărie
            "GET, /api/v1/city/actions, VISITOR, 403", "GET, /api/v1/city/actions, LOCAL, 403", "GET, /api/v1/city/actions, BUSINESS, 403",
            "GET, /api/v1/city/actions, CITY_HALL, 404", "POST, /api/v1/city/registrations/confirm, ADMIN, 404",
            // Firmă
            "GET, /api/v1/business/me, LOCAL, 403", "GET, /api/v1/business/me, CITY_HALL, 403",
            "GET, /api/v1/business/me, BUSINESS, 404", "POST, /api/v1/business/offers, ADMIN, 404",
            // Admin
            "GET, /api/v1/admin/stats, CITY_HALL, 403", "GET, /api/v1/admin/stats, BUSINESS, 403", "GET, /api/v1/admin/stats, ADMIN, 404",
            // Voluntar (T18)
            "GET, /api/v1/vol/actions, LOCAL, 403", "GET, /api/v1/vol/actions, VOLUNTEER, 404", "POST, /api/v1/vol/scan, VOLUNTEER, 404",
            "POST, /api/v1/vol/scan, CITY_HALL, 403", "GET, /api/v1/city/actions, VOLUNTEER, 403", "POST, /api/v1/city/volunteers, VOLUNTEER, 403",
            // Primăria administrează obiectivele, firmele partenere și voluntarii (API.md T05/T14/T18)
            "POST, /api/v1/city/places, CITY_HALL, 404", "POST, /api/v1/city/places, BUSINESS, 403", "POST, /api/v1/city/businesses/1/verify, CITY_HALL, 404",
            "POST, /api/v1/city/volunteers, CITY_HALL, 404", "PATCH, /api/v1/admin/users/5, CITY_HALL, 403", "PATCH, /api/v1/admin/users/5, ADMIN, 404",
            // Evenimente oficiale doar de la Primărie
            "POST, /api/v1/events, LOCAL, 403", "POST, /api/v1/events, BUSINESS, 403", "POST, /api/v1/events, CITY_HALL, 404",
            // Înscriere la acțiuni: localnic / vizitator
            "POST, /api/v1/community/actions/1/register, BUSINESS, 403", "POST, /api/v1/community/actions/1/register, CITY_HALL, 403",
            "POST, /api/v1/community/actions/1/register, LOCAL, 404", "POST, /api/v1/community/actions/1/register, VISITOR, 404",
            // orice utilizator logat
            "GET, /api/v1/me/tickets, VISITOR, 404", "POST, /api/v1/events/1/tickets, LOCAL, 404", "POST, /api/v1/ai/tts, VISITOR, 404",
    })
    void roleRules(String method, String path, Role role, int expected) throws Exception {
        mvc.perform(req(method, path).with(AuthMock.as(7L, role))).andExpect(status().is(expected));
    }

    private static MockHttpServletRequestBuilder req(String method, String path) {
        return request(HttpMethod.valueOf(method), path);
    }
}
