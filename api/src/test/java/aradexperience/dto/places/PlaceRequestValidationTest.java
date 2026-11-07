package aradexperience.dto.places;

import aradexperience.enums.PlaceType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Regulile jakarta de pe cererile din „Obiective & ghiduri” (separat de DtoValidationTest, ca să nu atingem fișierul T03). */
class PlaceRequestValidationTest {

    private static final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    static PlaceRequest place(String name, String slug, Double lat, Integer visitMinutes, List<String> tags) {
        return new PlaceRequest(name, slug, PlaceType.MUSEUM, "Descriere", "Str. X 1, Arad", lat, 21.31, null, null, null,
                visitMinutes, tags, 4.5, null, false);
    }

    @Test
    void placeRequest_minimal_isValid() {
        assertThat(validator.validate(place("Muzeul", null, 46.18, null, null))).isEmpty();
    }

    @Test
    void placeRequest_badSlugCoordinatesDurationAndTags_areRejected() {
        var v = validator.validate(place(" ", "Cu Spatii", 120.0, 2, List.of("ok", "")));
        assertThat(v).extracting(c -> c.getPropertyPath().toString())
                .containsExactlyInAnyOrder("name", "slug", "lat", "visitMinutes", "tags[1].<list element>");
    }

    @Test
    void guidePointRequest_needsTitleAndText_positionFromOne() {
        assertThat(validator.validate(new GuidePointRequest(null, "Titlu", "Text", null, null, null, null, null))).isEmpty();
        var v = validator.validate(new GuidePointRequest(0, "", "", null, null, 91.0, null, 3));
        assertThat(v).extracting(c -> c.getPropertyPath().toString())
                .containsExactlyInAnyOrder("position", "title", "text", "lat", "durationSec");
    }
}
