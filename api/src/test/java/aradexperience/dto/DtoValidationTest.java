package aradexperience.dto;

import aradexperience.dto.auth.LoginRequest;
import aradexperience.dto.auth.RegisterRequest;
import aradexperience.dto.auth.UpdateMeRequest;
import aradexperience.dto.business.AnnouncementCreate;
import aradexperience.dto.business.BusinessUpdate;
import aradexperience.dto.events.EventCreate;
import aradexperience.enums.BusinessCategory;
import aradexperience.enums.EventCategory;
import aradexperience.enums.Role;
import aradexperience.enums.TicketType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Testele „mici” ale contractului: regulile de validare de pe DTO-urile de cerere, fără Spring. */
class DtoValidationTest {

    private static final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void registerRequest_valid_hasNoViolations() {
        var req = new RegisterRequest("ana@exemplu.ro", "demo1234", "Ana", Role.VISITOR, null);
        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    void registerRequest_shortPasswordAndBadEmail_isRejected() {
        var req = new RegisterRequest("nu-e-email", "1234", "Ana", Role.LOCAL, null);
        Set<ConstraintViolation<RegisterRequest>> v = validator.validate(req);
        assertThat(v).extracting(c -> c.getPropertyPath().toString()).containsExactlyInAnyOrder("email", "password");
    }

    @Test
    void registerRequest_businessIsValidatedWhenPresent() {
        var req = new RegisterRequest("firma@exemplu.ro", "demo1234", "Firma", Role.BUSINESS,
                new RegisterRequest.BusinessCreate("", "CAFE", "Str. X 1", null, null));
        assertThat(validator.validate(req)).extracting(c -> c.getPropertyPath().toString()).containsExactly("business.name");
    }

    @Test
    void registerRequest_emailLongerThanColumn_andCoordinatesOutOfRange_areRejected() {
        String longEmail = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + ".ro";   // @Email valid, > 190
        var req = new RegisterRequest(longEmail, "demo1234", "Firma", Role.BUSINESS,
                new RegisterRequest.BusinessCreate("Firma", "CAFE", "Str. X 1", 999.0, -500.0));
        assertThat(validator.validate(req)).extracting(c -> c.getPropertyPath().toString())
                .containsExactlyInAnyOrder("email", "business.lat", "business.lng");
    }

    @Test
    void loginRequest_blankPassword_isRejected() {
        assertThat(validator.validate(new LoginRequest("ana@exemplu.ro", ""))).hasSize(1);
    }

    @Test
    void updateMeRequest_allOptional_butLocaleMustBeRoOrEn() {
        assertThat(validator.validate(new UpdateMeRequest(null, null))).isEmpty();
        assertThat(validator.validate(new UpdateMeRequest("Ana", "en"))).isEmpty();
        assertThat(validator.validate(new UpdateMeRequest("", "de")))
                .extracting(c -> c.getPropertyPath().toString()).containsExactlyInAnyOrder("displayName", "locale");
    }

    @Test
    void role_onlyVisitorLocalBusinessCanSelfRegister() {
        assertThat(Role.values()).filteredOn(Role::selfRegistrable).containsExactly(Role.VISITOR, Role.LOCAL, Role.BUSINESS);
    }

    // --- T09: firme -------------------------------------------------------------------------------------------------

    private static EventCreate event(String title, BigDecimal priceAdult, String externalUrl, Integer capacity, Double lat) {
        return new EventCreate(title, "Descriere", EventCategory.FOOD, null, null, null, lat, lat == null ? null : 21.31,
                OffsetDateTime.parse("2026-11-29T14:00:00Z"), null, TicketType.IN_APP, priceAdult, null, externalUrl, capacity, null);
    }

    @Test
    void eventCreate_valid_hasNoViolations() {
        assertThat(validator.validate(event("Atelier latte art", new BigDecimal("40"), null, 10, null))).isEmpty();
    }

    @Test
    void eventCreate_blankTitle_negativePrice_badUrl_zeroCapacity_badLat_areRejected() {
        assertThat(validator.validate(event(" ", new BigDecimal("-1"), "ftp://x", 0, 120.0)))
                .extracting(c -> c.getPropertyPath().toString())
                .containsExactlyInAnyOrder("title", "priceAdult", "externalUrl", "capacity", "lat");
    }

    @Test
    void eventCreate_requiresCategoryStartAndTicketType() {
        var req = new EventCreate("T", "D", null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        assertThat(validator.validate(req)).extracting(c -> c.getPropertyPath().toString())
                .containsExactlyInAnyOrder("category", "startAt", "ticketType");
    }

    @Test
    void businessUpdate_requiresNameAddressAndCoordinates() {
        assertThat(validator.validate(new BusinessUpdate("Cafeneaua", BusinessCategory.CAFE, null, "Str. X 1", 46.17, 21.31, null, null, null)))
                .isEmpty();
        assertThat(validator.validate(new BusinessUpdate("", null, null, "", null, 200.0, null, "pasaj.ro", null)))
                .extracting(c -> c.getPropertyPath().toString())
                .containsExactlyInAnyOrder("name", "category", "address", "lat", "lng", "website");
    }

    @Test
    void announcementCreate_titleAndBodyRequired_titleMax120() {
        assertThat(validator.validate(new AnnouncementCreate("Program nou", "Deschis până la 23:00.", null))).isEmpty();
        assertThat(validator.validate(new AnnouncementCreate("x".repeat(121), " ", null)))
                .extracting(c -> c.getPropertyPath().toString()).containsExactlyInAnyOrder("title", "body");
    }
}
