package aradexperience.controller.offers;

import aradexperience.controller.ControllerTest;
import aradexperience.enums.ContentStatus;
import aradexperience.enums.OfferCategory;
import aradexperience.enums.Role;
import aradexperience.exceptions.ConflictException;
import aradexperience.exceptions.ForbiddenException;
import aradexperience.exceptions.NotFoundException;
import aradexperience.mock.AuthMock;
import aradexperience.mock.BusinessMock;
import aradexperience.mock.JsonFixtures;
import aradexperience.mock.OfferMock;
import aradexperience.service.offers.OfferRedemptionService;
import aradexperience.service.offers.OfferRedemptionService.RedemptionView;
import aradexperience.service.offers.OfferService;
import aradexperience.service.offers.OfferService.OfferView;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ControllerTest(OffersController.class)
class OffersControllerTest {

    @Autowired MockMvc mvc;
    @MockBean OfferService offers;
    @MockBean OfferRedemptionService redemptions;

    private static OfferView coffeeView() {
        return new OfferView(OfferMock.coffee(), BusinessMock.pasaj(), 0.3, null);
    }

    @Test
    void list_isPublic_andPassesFilters() throws Exception {
        when(offers.list(new OfferService.Filter(OfferCategory.CAFE, null, true, "46.17,21.31", null))).thenReturn(List.of(coffeeView()));

        mvc.perform(get("/api/v1/offers").param("category", "CAFE").param("points", "true").param("near", "46.17,21.31"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].businessName").value("Cafeneaua din Pasaj"))
           .andExpect(jsonPath("$[0].discountLabel").value("-20%"))
           .andExpect(jsonPath("$[0].category").value("CAFE"))
           .andExpect(jsonPath("$[0].distanceKm").value(0.3))
           .andExpect(jsonPath("$[0].pointsPrice").value(30));
    }

    @Test
    void list_badCategory_is400() throws Exception {
        mvc.perform(get("/api/v1/offers").param("category", "PIZZA")).andExpect(status().isBadRequest());
    }

    @Test
    void get_withToken_includesMyRedemption() throws Exception {
        when(offers.get(eq(1L), any())).thenReturn(new OfferView(OfferMock.coffee(), BusinessMock.pasaj(), null, OfferMock.issued(9L, "AX-O1-K8D2QF")));

        mvc.perform(get("/api/v1/offers/1").with(AuthMock.as(2L, Role.LOCAL)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.usedCount").value(87))
           .andExpect(jsonPath("$.myRedemption.code").value("AX-O1-K8D2QF"))
           .andExpect(jsonPath("$.myRedemption.offerTitle").value("20% reducere la orice cafea"));
    }

    @Test
    void get_withoutToken_passesNoUser_unknown_is404() throws Exception {
        when(offers.get(eq(1L), isNull())).thenReturn(coffeeView());
        mvc.perform(get("/api/v1/offers/1")).andExpect(status().isOk()).andExpect(jsonPath("$.myRedemption").doesNotExist());

        when(offers.get(eq(42L), isNull())).thenThrow(NotFoundException.of("Offer", 42));
        mvc.perform(get("/api/v1/offers/42")).andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("Offer 42 not found"));
    }

    @Test
    void redeem_requiresLogin_returns201_and409OnSecond() throws Exception {
        mvc.perform(post("/api/v1/offers/1/redeem")).andExpect(status().isUnauthorized());

        when(redemptions.redeem(eq(1L), any())).thenReturn(new RedemptionView(OfferMock.issued(9L, "AX-O1-K8D2QF"), OfferMock.coffee(), BusinessMock.pasaj()));
        mvc.perform(post("/api/v1/offers/1/redeem").with(AuthMock.as(2L, Role.LOCAL)))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.code").value("AX-O1-K8D2QF"))
           .andExpect(jsonPath("$.status").value("ISSUED"))
           .andExpect(jsonPath("$.businessName").value("Cafeneaua din Pasaj"));

        when(redemptions.redeem(eq(1L), any())).thenThrow(new ConflictException("You already have an active code for this offer"));
        mvc.perform(post("/api/v1/offers/1/redeem").with(AuthMock.as(2L, Role.LOCAL)))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.detail").value("You already have an active code for this offer"));
    }

    @Test
    void myRedemptions_listsCodes() throws Exception {
        when(redemptions.mine(any())).thenReturn(List.of(new RedemptionView(OfferMock.used(1L, "AX-O1-B9Q2LW"), OfferMock.coffee(), BusinessMock.pasaj())));

        mvc.perform(get("/api/v1/me/redemptions").with(AuthMock.as(2L, Role.LOCAL)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].status").value("USED"))
           .andExpect(jsonPath("$[0].usedAt").exists());
    }

    @Test
    void businessOffers_createUpdateDelete() throws Exception {
        when(offers.businessOffers(any())).thenReturn(List.of(coffeeView()));
        mvc.perform(get("/api/v1/business/offers").with(AuthMock.as(BusinessMock.OWNER_ID, Role.BUSINESS)))
           .andExpect(status().isOk()).andExpect(jsonPath("$[0].viewsCount").value(1240));

        var draft = OfferMock.coffee();
        draft.setStatus(ContentStatus.DRAFT);
        when(offers.create(any(), any())).thenReturn(new OfferView(draft, BusinessMock.pasaj(), null, null));
        mvc.perform(post("/api/v1/business/offers").with(AuthMock.as(BusinessMock.OWNER_ID, Role.BUSINESS))
                .contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/offers/create-offer.json")))
           .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT"));

        when(offers.update(any(), eq(1L), any())).thenThrow(new ForbiddenException("Offer 1 belongs to another business"));
        mvc.perform(put("/api/v1/business/offers/1").with(AuthMock.as(14L, Role.BUSINESS))
                .contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/offers/create-offer.json")))
           .andExpect(status().isForbidden());

        mvc.perform(delete("/api/v1/business/offers/1").with(AuthMock.as(BusinessMock.OWNER_ID, Role.BUSINESS))).andExpect(status().isNoContent());
        verify(offers).delete(any(), eq(1L));
    }

    @Test
    void createOffer_invalid_is400_asLocal_is403() throws Exception {
        mvc.perform(post("/api/v1/business/offers").with(AuthMock.as(BusinessMock.OWNER_ID, Role.BUSINESS))
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\",\"description\":\"D\",\"discountLabel\":\"-10%\",\"pointsPrice\":0}"))
           .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.length()").value(2));
        mvc.perform(post("/api/v1/business/offers").with(AuthMock.as(2L, Role.LOCAL))
                .contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/offers/create-offer.json")))
           .andExpect(status().isForbidden());
    }

    @Test
    void useCode_returnsUsed_409Twice_404Unknown() throws Exception {
        when(redemptions.use(any(), eq("AX-O1-K8D2QF")))
                .thenReturn(new RedemptionView(OfferMock.used(9L, "AX-O1-K8D2QF"), OfferMock.coffee(), BusinessMock.pasaj()))
                .thenThrow(new ConflictException("Code already used"));
        var req = post("/api/v1/business/redemptions/use").with(AuthMock.as(BusinessMock.OWNER_ID, Role.BUSINESS))
                .contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/offers/use-code.json"));
        mvc.perform(req).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("USED"));
        mvc.perform(req).andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("Code already used"));

        when(redemptions.use(any(), eq("AX-O1-ZZZZZZ"))).thenThrow(new NotFoundException("Unknown code"));
        mvc.perform(post("/api/v1/business/redemptions/use").with(AuthMock.as(BusinessMock.OWNER_ID, Role.BUSINESS))
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"AX-O1-ZZZZZZ\"}"))
           .andExpect(status().isNotFound());
    }

    @Test
    void businessRedemptions_filtersByStatus() throws Exception {
        when(redemptions.businessRedemptions(any(), eq(aradexperience.enums.RedemptionStatus.USED)))
                .thenReturn(List.of(new RedemptionView(OfferMock.used(1L, "AX-O1-B9Q2LW"), OfferMock.coffee(), BusinessMock.pasaj())));
        mvc.perform(get("/api/v1/business/redemptions").param("status", "USED").with(AuthMock.as(BusinessMock.OWNER_ID, Role.BUSINESS)))
           .andExpect(status().isOk()).andExpect(jsonPath("$[0].code").value("AX-O1-B9Q2LW"));
    }
}
