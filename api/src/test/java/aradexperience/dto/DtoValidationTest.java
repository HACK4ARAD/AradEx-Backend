package aradexperience.dto;

import aradexperience.dto.auth.LoginRequest;
import aradexperience.dto.auth.RegisterRequest;
import aradexperience.enums.Role;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

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
    void loginRequest_blankPassword_isRejected() {
        assertThat(validator.validate(new LoginRequest("ana@exemplu.ro", ""))).hasSize(1);
    }
}
