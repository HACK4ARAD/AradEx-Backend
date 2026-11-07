package aradexperience.dto.offers;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST `/business/redemptions/use`: codul citit din QR sau scris de mână (se acceptă și litere mici / spații; se normalizează). */
public record UseCodeRequest(@NotBlank @Size(max = 40) String code) {
}
