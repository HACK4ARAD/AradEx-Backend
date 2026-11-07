package aradexperience.dto.auth;

/**
 * 202 la POST /auth/otp/request: peste cât se poate cere alt cod; `demoCode` doar pentru numerele de demo (0799000xxx);
 * `alreadySent` = true când există deja un cod nefolosit, trimis de mai puțin de 60 s (nu pleacă alt SMS; omul îl folosește pe acela).
 */
public record OtpRequestResponse(long retryAfterSeconds, String demoCode, Boolean alreadySent) {

    public OtpRequestResponse(long retryAfterSeconds, String demoCode) {
        this(retryAfterSeconds, demoCode, null);
    }
}
