package org.apifocal.auth41.ciba.spi;

import org.apifocal.auth41.common.exception.Auth41Exception;

/**
 * Exception thrown when backchannel authentication operations fail.
 */
public class BackchannelException extends Auth41Exception {

    private final String errorCode;

    public BackchannelException(String message) {
        this(message, null, null);
    }

    public BackchannelException(String message, Throwable cause) {
        this(message, null, cause);
    }

    public BackchannelException(String message, String errorCode) {
        this(message, errorCode, null);
    }

    public BackchannelException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public String toString() {
        if (errorCode != null) {
            return "BackchannelException{" +
                    "errorCode='" + errorCode + '\'' +
                    ", message='" + getMessage() + '\'' +
                    '}';
        }
        return super.toString();
    }
}
