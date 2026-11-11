package aradexperience.dto.auth;

/** GET /auth/config: `demoOnly` = încă nu suntem live, intră doar conturile demo (fără conturi noi și fără recuperarea parolei). */
public record AuthConfigDto(boolean demoOnly) {
}
