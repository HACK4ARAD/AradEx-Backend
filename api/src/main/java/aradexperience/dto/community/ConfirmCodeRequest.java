package aradexperience.dto.community;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * POST /city/registrations/confirm: codul din QR-ul localnicului (`AX-C{actionId}-XXXXXX`). Același format ca `@Code`, dar acceptă
 * și litere mici / spații la capete (cod tastat de mână); service-ul îl normalizează (trim + litere mari).
 */
public record ConfirmCodeRequest(@NotBlank @Pattern(regexp = "(?i)^\\s*AX-[TOC]\\d+-[A-HJ-NP-Z2-9]{6}\\s*$", message = "invalid AradExperience code") String code) {
}
