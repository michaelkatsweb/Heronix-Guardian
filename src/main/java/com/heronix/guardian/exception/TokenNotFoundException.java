package com.heronix.guardian.exception;

/**
 * Exception thrown when a token is not found.
 */
public class TokenNotFoundException extends RuntimeException {

    public TokenNotFoundException(String tokenValue) {
        super("Token not found: " + tokenValue);
    }

    public TokenNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
