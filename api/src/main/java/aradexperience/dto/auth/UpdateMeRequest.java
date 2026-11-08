package aradexperience.dto.auth;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** PATCH /me — ambele câmpuri opționale. */
public record UpdateMeRequest(@Size(min = 1, max = 80) String displayName, @Pattern(regexp = "ro|en|hu") String locale) {
}
