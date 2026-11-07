package aradexperience.dto.places;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST / PUT /city/places/{id}/guide-points[/{gpId}]: GuidePointDto fără id.
 * `position` lipsă la creare = la final; la PUT, o poziție ocupată de alt punct face schimb cu el (mută sus/jos).
 * `durationSec` lipsă = estimat din numărul de cuvinte (citire cu voce tare).
 */
public record GuidePointRequest(
        @Min(1) @Max(99) Integer position,
        @NotBlank @Size(max = 160) String title,
        @NotBlank @Size(max = 4000) String text,
        @Size(max = 500) String imageUrl,
        @Size(max = 500) String audioUrl,
        @DecimalMin("-90") @DecimalMax("90") Double lat,
        @DecimalMin("-180") @DecimalMax("180") Double lng,
        @Min(5) @Max(1800) Integer durationSec) {
}
