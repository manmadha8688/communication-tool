package com.cloudfuze.assessment.exception;

import org.springframework.http.HttpStatus;

/** An error with the HTTP status and the sentence the user should read. */
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
