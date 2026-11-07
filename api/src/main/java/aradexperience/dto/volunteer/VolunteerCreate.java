package aradexperience.dto.volunteer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * POST /city/volunteers: Primăria creează contul. Telefonul (orice format românesc → E.164) primește codul „prin SMS” (simulat);
 * `coordinatorId` lipsă = cel care creează; `actionIds` = acțiunile publicate la care scanează.
 */
public record VolunteerCreate(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 30) String phone,
        @Email @Size(max = 190) String email,
        Long coordinatorId,
        @Size(max = 50) List<Long> actionIds,
        boolean oneTime) {
}
