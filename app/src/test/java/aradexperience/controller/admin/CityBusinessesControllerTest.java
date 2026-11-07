package aradexperience.controller.admin;

import aradexperience.controller.ControllerTest;
import aradexperience.dto.admin.CityBusinessCreate;
import aradexperience.enums.Role;
import aradexperience.exceptions.ConflictException;
import aradexperience.mock.AdminMock;
import aradexperience.mock.AuthMock;
import aradexperience.mock.JsonFixtures;
import aradexperience.service.admin.PartnerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ControllerTest(CityBusinessesController.class)
class CityBusinessesControllerTest {

    @Autowired MockMvc mvc;
    @MockBean PartnerService service;

    private static PartnerService.Row bakeryRow() {
        return new PartnerService.Row(AdminMock.bakery(), AdminMock.bakeryOwner(), 0, 1, 0);
    }

    @Test
    void list_toVerify_asCityHall() throws Exception {
        when(service.list(false)).thenReturn(List.of(bakeryRow()));

        mvc.perform(get("/api/v1/city/businesses?verified=false").with(AuthMock.as(AdminMock.CITY_ID, Role.CITY_HALL)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].name").value("Brutăria Veche"))
           .andExpect(jsonPath("$[0].verified").value(false))
           .andExpect(jsonPath("$[0].email").value("brutarie@exemplu.ro"))
           .andExpect(jsonPath("$[0].draftOffers").value(1));
    }

    @Test
    void partners_asBusinessOrVisitor_is403() throws Exception {
        mvc.perform(get("/api/v1/city/businesses").with(AuthMock.as(3L, Role.BUSINESS))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/city/businesses/2/verify").with(AuthMock.as(1L, Role.VISITOR))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void verify_asAdmin_returnsVerifiedRow() throws Exception {
        PartnerService.Row row = bakeryRow();
        row.business().setVerified(true);
        when(service.verify(eq(2L), any())).thenReturn(row);

        mvc.perform(post("/api/v1/city/businesses/2/verify").with(AuthMock.as(AdminMock.ADMIN_ID, Role.ADMIN)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.verified").value(true));
    }

    @Test
    void reject_withReason_or_withoutBody_is204() throws Exception {
        mvc.perform(post("/api/v1/city/businesses/2/reject").with(AuthMock.as(AdminMock.CITY_ID, Role.CITY_HALL))
                .contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/admin/business-reject.json")))
           .andExpect(status().isNoContent());
        verify(service).reject(eq(2L), eq("Date incomplete"), any());

        mvc.perform(post("/api/v1/city/businesses/2/reject").with(AuthMock.as(AdminMock.CITY_ID, Role.CITY_HALL)))
           .andExpect(status().isNoContent());
        verify(service).reject(eq(2L), isNull(), any());
    }

    @Test
    void create_returns201_withInvitePassword() throws Exception {
        var b = AdminMock.bakery();
        b.setVerified(true);
        when(service.create(any(CityBusinessCreate.class), any()))
                .thenReturn(new PartnerService.Created(new PartnerService.Row(b, AdminMock.bakeryOwner(), 0, 0, 0), "5PdWXBzJK37V"));

        mvc.perform(post("/api/v1/city/businesses").with(AuthMock.as(AdminMock.CITY_ID, Role.CITY_HALL))
                .contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/admin/business-create.json")))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.business.verified").value(true))
           .andExpect(jsonPath("$.invitePassword").value("5PdWXBzJK37V"));
    }

    @Test
    void create_invalid_is400_duplicate_is409() throws Exception {
        mvc.perform(post("/api/v1/city/businesses").with(AuthMock.as(AdminMock.CITY_ID, Role.CITY_HALL))
                .contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/admin/business-create-invalid.json")))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors").isArray());

        when(service.create(any(), any())).thenThrow(new ConflictException("Email already registered"));
        mvc.perform(post("/api/v1/city/businesses").with(AuthMock.as(AdminMock.CITY_ID, Role.CITY_HALL))
                .contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/admin/business-create.json")))
           .andExpect(status().isConflict());
    }
}
