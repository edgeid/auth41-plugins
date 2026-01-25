package org.apifocal.auth41.plugin.broker;

/**
 * Exception thrown when federation operations fail.
 *
 * <p>This exception indicates errors during federated authentication such as:
 * - Unable to reach home provider
 * - Token validation failures
 * - Invalid trust paths
 * - CIBA request failures
 */
public class FederationException extends Exception {

    public FederationException(String message) {
        super(message);
    }

    public FederationException(String message, Throwable cause) {
        super(message, cause);
    }
}
