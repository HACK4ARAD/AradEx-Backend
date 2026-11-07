package aradexperience.exceptions;

import org.springframework.http.HttpStatus;

/** 401: date de autentificare greșite sau cont care nu mai există. */
public class UnauthorizedException extends ApiException {
    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
