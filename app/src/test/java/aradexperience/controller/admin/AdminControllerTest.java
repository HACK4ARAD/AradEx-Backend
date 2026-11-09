package aradexperience.controller.admin;

import aradexperience.controller.ControllerTest;
import aradexperience.dto.admin.AdminUserUpdate;
import aradexperience.enums.Role;
import aradexperience.exceptions.BadRequestException;
import aradexperience.mock.AdminMock;
import aradexperience.mock.AuthMock;
import aradexperience.mock.BusinessMock;
import aradexperience.mock.JsonFixtures;
import aradexperience.mock.OfferMock;
import aradexperience.service.admin.AdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ControllerTest(AdminController.class)
class AdminControllerTest {

    @Autowired MockMvc mvc;
    @MockBean AdminService service;
    @MockBean aradexperience.service.auth.PasswordService passwords;

    @Test
    void stats_asAdmin_returnsCounts() throws Exception {
        Map<Role, Long> byRole = new EnumMap<>(Role.class);
        byRole.put(Role.LOCAL, 19L);
        when(service.stats()).thenReturn(new AdminService.Stats(byRole, 35, 2, 4, 3, 9990, 3));

        mvc.perform(get("/api/v1/admin/stats").with(AuthMock.as(AdminMock.ADMIN_ID, Role.ADMIN)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.usersByRole.LOCAL").value(19))
           .andExpect(jsonPath("$.pointsAwarded").value(9990));
    }

    @Test
    void admin_asVisitorOrCityHall_is403_withoutToken_is401() throws Exception {
        mvc.perform(get("/api/v1/admin/stats").with(AuthMock.as(1L, Role.VISITOR))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/users").with(AuthMock.as(AdminMock.CITY_ID, Role.CITY_HALL))).andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/admin/events/1/status").with(AuthMock.as(2L, Role.LOCAL))
                .contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/admin/status-hidden.json")))
           .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/stats")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void content_offers_mapsOrganizerAndCounts() throws Exception {
        when(service.content(AdminService.Kind.OFFERS, "PUBLISHED", "caf", false, 0, 50))
                .thenReturn(new AdminService.Content(new PageImpl<>(List.of(OfferMock.coffee())), Map.of(1L, BusinessMock.pasaj())));

        mvc.perform(get("/api/v1/admin/offers?status=PUBLISHED&q=caf").with(AuthMock.as(AdminMock.ADMIN_ID, Role.ADMIN)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.content[0].organizer").value("Cafeneaua din Pasaj"))
           .andExpect(jsonPath("$.content[0].organizerVerified").value(true))
           .andExpect(jsonPath("$.content[0].status").value("PUBLISHED"))
           .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void setStatus_event_returnsRow() throws Exception {
        var e = BusinessMock.event(24L, 1L);
        e.setStatus(aradexperience.enums.ContentStatus.HIDDEN);
        when(service.setStatus(eq(AdminService.Kind.EVENTS), eq(24L), eq("HIDDEN"), any()))
                .thenReturn(new AdminService.Content(new PageImpl<>(List.of(e)), Map.of(1L, BusinessMock.pasaj())));

        mvc.perform(patch("/api/v1/admin/events/24/status").with(AuthMock.as(AdminMock.ADMIN_ID, Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/admin/status-hidden.json")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.id").value(24))
           .andExpect(jsonPath("$.status").value("HIDDEN"));
    }

    @Test
    void setStatus_blankStatus_is400_unknownKind_is404() throws Exception {
        mvc.perform(patch("/api/v1/admin/offers/1/status").with(AuthMock.as(AdminMock.ADMIN_ID, Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"\"}"))
           .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/places").with(AuthMock.as(AdminMock.ADMIN_ID, Role.ADMIN)))
           .andExpect(status().isNotFound());
    }

    @Test
    void users_and_updateUser() throws Exception {
        var u = AdminMock.local(111L);
        when(service.users(Role.LOCAL, null, 0, 20)).thenReturn(new PageImpl<>(List.of(new AdminService.UserRow(u, null, 2))));
        mvc.perform(get("/api/v1/admin/users?role=LOCAL").with(AuthMock.as(AdminMock.ADMIN_ID, Role.ADMIN)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.content[0].phone").value("+40799000002"))
           .andExpect(jsonPath("$.content[0].ticketsCount").value(2))
           .andExpect(jsonPath("$.content[0].disabled").value(false));

        u.setRole(Role.CITY_HALL);
        when(service.updateUser(eq(111L), any(AdminUserUpdate.class), any())).thenReturn(new AdminService.UserRow(u, null, 0));
        mvc.perform(patch("/api/v1/admin/users/111").with(AuthMock.as(AdminMock.ADMIN_ID, Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/admin/user-update.json")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.role").value("CITY_HALL"));
        verify(service).updateUser(eq(111L), eq(new AdminUserUpdate(Role.CITY_HALL, 25, "Bonus voluntariat", null)), any());
    }

    @Test
    void updateUser_serviceRejects_is400ProblemDetail() throws Exception {
        when(service.updateUser(eq(5L), any(), any())).thenThrow(new BadRequestException("You cannot disable your own account"));
        mvc.perform(patch("/api/v1/admin/users/5").with(AuthMock.as(AdminMock.ADMIN_ID, Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content("{\"disabled\":true}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.detail").value("You cannot disable your own account"));
    }

    @Test
    void temporaryPassword_asAdmin_returnsItOnce_otherRoles_403() throws Exception {
        when(passwords.temporaryPassword(eq(7L), any())).thenReturn("K7QM-4XPT-9DWA");

        mvc.perform(post("/api/v1/admin/users/7/temporary-password").with(AuthMock.as(AdminMock.ADMIN_ID, Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temporaryPassword").value("K7QM-4XPT-9DWA"));
        mvc.perform(post("/api/v1/admin/users/7/temporary-password").with(AuthMock.as(4L, Role.CITY_HALL)))
                .andExpect(status().isForbidden());
    }

    @Test
    void temporaryPassword_forAnotherAdmin_is403() throws Exception {
        when(passwords.temporaryPassword(eq(9L), any())).thenThrow(new aradexperience.exceptions.ForbiddenException("Admin accounts change their own password"));

        mvc.perform(post("/api/v1/admin/users/9/temporary-password").with(AuthMock.as(AdminMock.ADMIN_ID, Role.ADMIN)))
                .andExpect(status().isForbidden());
    }
}
