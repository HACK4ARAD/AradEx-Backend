package aradexperience.exceptions;

import org.springframework.http.HttpStatus;

/** 429: prea multe cereri (de ex. coduri OTP); `retryAfterSeconds` ajunge în ProblemDetail, ca aplicația să arate cât mai e. */
public class TooManyRequestsException extends ApiException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
