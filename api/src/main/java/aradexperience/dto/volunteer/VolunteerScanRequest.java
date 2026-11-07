package aradexperience.dto.volunteer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * POST /vol/scan: codul din QR-ul participantului (`AX-C{actionId}-XXXXXX`) și, opțional, acțiunea aleasă în ecranul de scanare
 * (dacă codul e de la altă acțiune → 409, ca voluntarul să știe că omul a venit la altceva).
 */
public record VolunteerScanRequest(
        @NotBlank @Pattern(regexp = "(?i)^\\s*AX-C\\d+-[A-HJ-NP-Z2-9]{6}\\s*$", message = "invalid action code") String code,
        Long actionId) {
}
