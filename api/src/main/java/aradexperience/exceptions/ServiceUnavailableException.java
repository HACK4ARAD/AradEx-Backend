package aradexperience.exceptions;

import org.springframework.http.HttpStatus;

/** 503: o dependență de care API-ul nu se poate lipsi (baza de date) nu răspunde. */
public class ServiceUnavailableException extends ApiException {

    public ServiceUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
