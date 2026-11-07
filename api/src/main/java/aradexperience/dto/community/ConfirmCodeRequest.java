package aradexperience.dto.community;

import aradexperience.annotations.Code;
import jakarta.validation.constraints.NotBlank;

/** POST /city/registrations/confirm: codul din QR-ul localnicului (`AX-C{actionId}-XXXXXX`). */
public record ConfirmCodeRequest(@NotBlank @Code String code) {
}
