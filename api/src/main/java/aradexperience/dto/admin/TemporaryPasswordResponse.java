package aradexperience.dto.admin;

/** Parola temporară setată de Admin (`XXXX-XXXX-XXXX`), arătată o singură dată; la intrare omul e obligat să-și aleagă alta. */
public record TemporaryPasswordResponse(String temporaryPassword) {
}
