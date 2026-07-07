package com.openfloat.mpesa.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a client exceeds its configured rate limit.
 * Maps to HTTP 429 Too Many Requests.
 */
public class RateLimitExceededException extends BaseException {

    public RateLimitExceededException(String clientId) {
        super(
                String.format("Rate limit exceeded for client: %s", clientId),
                HttpStatus.TOO_MANY_REQUESTS,
                "RATE_LIMIT_EXCEEDED"
        );
    }

    public RateLimitExceededException(String clientId, int limit, int windowSeconds) {
        super(
                String.format("Rate limit exceeded for client '%s': %d requests per %d seconds",
                        clientId, limit, windowSeconds),
                HttpStatus.TOO_MANY_REQUESTS,
                "RATE_LIMIT_EXCEEDED"
        );
    }
}
