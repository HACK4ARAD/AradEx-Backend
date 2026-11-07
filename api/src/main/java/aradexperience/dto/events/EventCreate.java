package aradexperience.dto.events;

import aradexperience.enums.EventCategory;
import aradexperience.enums.TicketType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * docs/API.md: EventCreate (POST/PUT `/business/events`, apoi `/events` în T06). Locația e opțională: pentru firme,
 * lipsa ei înseamnă „la sediul firmei”. Regulile între câmpuri (endAt după startAt, preț la IN_APP, link la EXTERNAL) sunt în service.
 */
public record EventCreate(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 5000) String description,
        @NotNull EventCategory category,
        Long placeId,
        @Size(max = 160) String locationName,
        @Size(max = 250) String address,
        @DecimalMin("-90") @DecimalMax("90") Double lat,
        @DecimalMin("-180") @DecimalMax("180") Double lng,
        @NotNull OffsetDateTime startAt,
        OffsetDateTime endAt,
        @NotNull TicketType ticketType,
        @DecimalMin("0") @DecimalMax("99999") BigDecimal priceAdult,
        @DecimalMin("0") @DecimalMax("99999") BigDecimal priceChild,
        @Size(max = 500) @Pattern(regexp = "https?://.+") String externalUrl,
        @Positive Integer capacity,
        @Size(max = 500) String imageUrl) {
}
