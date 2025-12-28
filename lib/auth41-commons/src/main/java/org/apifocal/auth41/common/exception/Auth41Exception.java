package org.apifocal.auth41.common.exception;

/**
 * Base exception for all Auth41 operations.
 */
public class Auth41Exception extends Exception {

    public Auth41Exception(String message) {
        super(message);
    }

    public Auth41Exception(String message, Throwable cause) {
        super(message, cause);
    }

    public Auth41Exception(Throwable cause) {
        super(cause);
    }
}
