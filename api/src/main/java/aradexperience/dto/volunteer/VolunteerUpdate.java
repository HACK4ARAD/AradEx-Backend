package aradexperience.dto.volunteer;

import aradexperience.enums.VolunteerStatus;
import jakarta.validation.constraints.Size;

import java.util.List;

/** PATCH /city/volunteers/{id}: câmpurile lipsă rămân neschimbate (acțiunile alocate, suspendare / reactivare, coordonatorul). */
public record VolunteerUpdate(@Size(max = 50) List<Long> actionIds, VolunteerStatus status, Long coordinatorId) {
}
