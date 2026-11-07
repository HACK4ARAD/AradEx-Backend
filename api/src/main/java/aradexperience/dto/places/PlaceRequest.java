package aradexperience.dto.places;

import aradexperience.enums.PlaceType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * POST / PUT /city/places (Primăria, Adminul): PlaceDto fără id, câmpuri calculate și guidePoints.
 * `slug` e opțional (se generează din nume); `visitMinutes` lipsă = 60. Limitele = coloanele din V3.
 */
public record PlaceRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 160) @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "must be lowercase letters, digits and dashes") String slug,
        @NotNull PlaceType type,
        @NotBlank String description,
        @Size(max = 250) String address,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng,
        @Size(max = 500) String imageUrl,
        @Size(max = 250) String imageCredit,
        String openingHours,
        @Min(5) @Max(600) Integer visitMinutes,
        @Size(max = 12) List<@NotBlank @Size(max = 30) String> tags,
        @DecimalMin("0") @DecimalMax("5") Double rating,
        String parkingHint,
        boolean exteriorOnly) {
}
