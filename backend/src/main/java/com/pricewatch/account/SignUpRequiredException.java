package com.pricewatch.account;

/** A guest asked for something only accounts get: re-checks, more products, claiming guest products. */
public class SignUpRequiredException extends RuntimeException {

    public SignUpRequiredException(String message) {
        super(message);
    }
}
