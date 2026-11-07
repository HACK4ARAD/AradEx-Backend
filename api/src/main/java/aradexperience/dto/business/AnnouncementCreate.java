package aradexperience.dto.business;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST `/business/announcements`. Limitele = coloanele din V6. */
public record AnnouncementCreate(@NotBlank @Size(max = 120) String title, @NotBlank @Size(max = 1000) String body,
                                 @Size(max = 500) String imageUrl) {
}
