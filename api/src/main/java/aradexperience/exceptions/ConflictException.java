package aradexperience.exceptions;

import org.springframework.http.HttpStatus;

/** 409: capacitate epuizată, dublă înscriere, cod deja folosit, puncte insuficiente etc. */
public class ConflictException extends ApiException {
    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
