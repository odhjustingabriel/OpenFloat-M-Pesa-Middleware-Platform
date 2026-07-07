package com.openfloat.mpesa.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a duplicate resource is detected (idempotency violation).
 * Maps to HTTP 409 Conflict.
 */
public class DuplicateResourceException extends BaseException {

    public DuplicateResourceException(String message) {
        super(message, HttpStatus.CONFLICT, "DUPLICATE_RESOURCE");
    }

    public DuplicateResourceException(String resourceName, String fieldName, Object fieldValue) {
        super(
                String.format("%s already exists with %s: '%s'", resourceName, fieldName, fieldValue),
                HttpStatus.CONFLICT,
                "DUPLICATE_RESOURCE"
        );
    }
}
