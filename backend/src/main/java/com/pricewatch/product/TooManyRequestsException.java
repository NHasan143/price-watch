package com.pricewatch.product;

import java.time.Duration;

/** Thrown when a guest adds products faster than {@link GuestRateLimiter} allows; becomes HTTP 429. */
public class TooManyRequestsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyRequestsException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
