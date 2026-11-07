package aradexperience.dto.business;

import aradexperience.enums.BusinessCategory;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** PUT `/business/me`: BusinessDto fără `id`, `verified` și `viewsCount` (acestea nu se schimbă de firmă). Limitele = coloanele din V2. */
public record BusinessUpdate(
        @NotBlank @Size(max = 160) String name,
        @NotNull BusinessCategory category,
        @Size(max = 2000) String description,
        @NotBlank @Size(max = 250) String address,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng,
        @Size(max = 40) String phone,
        @Size(max = 250) @Pattern(regexp = "https?://.+") String website,
        @Size(max = 500) String imageUrl) {
}
