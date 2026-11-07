package aradexperience.dto.community;

import aradexperience.enums.ActionCategory;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Regulile jakarta de pe cererile T11 (într-un fișier al culoarului, ca să nu atingem DtoValidationTest în paralel cu alții). */
class CommunityValidationTest {

    private static final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private static final OffsetDateTime START = OffsetDateTime.parse("2026-11-21T09:00:00+02:00");

    static ActionCreate valid() {
        return new ActionCreate("Plantăm flori în Parcul Copiilor", "Ce facem, ce aduc oamenii.", ActionCategory.ENVIRONMENT, "Direcția Spații Verzi",
                START, START.plusHours(3), "Parcul Copiilor", null, null, null, 50, 20, null, null, null);
    }

    @Test
    void actionCreate_valid_hasNoViolations() {
        assertThat(validator.validate(valid())).isEmpty();
    }

    @Test
    void actionCreate_missingRequiredAndOutOfRange_isRejected() {
        var req = new ActionCreate("", "", null, null, null, null, " ", null, 91.0, null, 5, 0, null, null, null);
        assertThat(validator.validate(req)).extracting(c -> c.getPropertyPath().toString())
                .containsExactlyInAnyOrder("title", "description", "category", "startAt", "endAt", "locationName", "lat", "pointsReward", "capacity");
    }

    @Test
    void confirmCode_mustBeAnAradExperienceCode() {
        assertThat(validator.validate(new ConfirmCodeRequest("AX-C1-K4N7PA"))).isEmpty();
        assertThat(validator.validate(new ConfirmCodeRequest("AX-T1-K4N7PA"))).isEmpty();   // formatul e comun; tipul greșit dă 404 în service
        assertThat(validator.validate(new ConfirmCodeRequest("nu-e-cod"))).hasSize(1);
        assertThat(validator.validate(new ConfirmCodeRequest(""))).isNotEmpty();
    }
}
