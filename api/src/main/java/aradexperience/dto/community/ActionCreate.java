package aradexperience.dto.community;

import aradexperience.enums.ActionCategory;
import aradexperience.enums.ActionStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/**
 * POST / PUT /city/actions. Limitele = coloanele din V8. `lat`/`lng` lipsă → centrul Aradului; `status` lipsă → PUBLISHED.
 * În service: `endAt > startAt`, la creare doar DRAFT sau PUBLISHED, capacitatea nu scade sub numărul de înscriși.
 */
public record ActionCreate(
        @NotBlank @Size(max = 160) String title,
        @NotBlank @Size(max = 4000) String description,
        @NotNull ActionCategory category,
        @Size(max = 120) String organizer,
        @NotNull OffsetDateTime startAt,
        @NotNull OffsetDateTime endAt,
        @NotBlank @Size(max = 200) String locationName,
        @Size(max = 250) String address,
        @DecimalMin("-90") @DecimalMax("90") Double lat,
        @DecimalMin("-180") @DecimalMax("180") Double lng,
        @NotNull @Min(10) @Max(200) Integer pointsReward,
        @NotNull @Min(1) @Max(1000) Integer capacity,
        @Size(max = 500) String imageUrl,
        @Size(max = 250) String imageCredit,
        ActionStatus status) {
}
