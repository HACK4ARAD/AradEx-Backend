package aradexperience.controller.health;

import aradexperience.controller.ControllerTest;
import aradexperience.enums.Role;
import aradexperience.mock.AuthMock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Șablonul de test de controller: rapid, fără DB, cu regulile reale de securitate. Pentru controllere cu service: `@MockBean XService service`. */
@ControllerTest(HealthController.class)
class HealthControllerTest {

    @Autowired MockMvc mvc;

    @Test
    void health_isPublic() throws Exception {
        mvc.perform(get("/api/v1/health"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void protectedRoute_withoutAuth_is401_asProblemJson() throws Exception {
        mvc.perform(get("/api/v1/me"))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void adminRoute_withWrongRole_is403() throws Exception {
        mvc.perform(get("/api/v1/admin/stats").with(AuthMock.as(1L, Role.LOCAL)))
           .andExpect(status().isForbidden());
    }
}
