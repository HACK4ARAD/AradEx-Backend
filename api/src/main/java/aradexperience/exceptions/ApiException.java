package aradexperience.exceptions;

import org.springframework.http.HttpStatus;

/** Bază pentru erorile de business. Handler-ul global le transformă în ProblemDetail (RFC 7807). */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
