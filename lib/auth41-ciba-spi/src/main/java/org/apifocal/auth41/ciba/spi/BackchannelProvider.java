package org.apifocal.auth41.ciba.spi;

import org.keycloak.provider.Provider;

import java.util.Set;

/**
 * SPI for CIBA backchannel authentication implementations.
 *
 * Implementations of this provider handle the delivery of authentication requests
 * to end users via various channels (file, push notification, SMS, etc.) and
 * track the authentication status.
 */
public interface BackchannelProvider extends Provider {

    /**
     * Get the delivery modes supported by this backchannel implementation.
     *
     * @return set of supported delivery modes (e.g., "poll", "ping", "push")
     */
    Set<String> getSupportedDeliveryModes();

    /**
     * Initiate a backchannel authentication request.
     *
     * This method should:
     * 1. Store/persist the authentication request
     * 2. Notify the user via the backchannel (push notification, file, etc.)
     * 3. Return immediately without waiting for user action
     *
     * @param request the authentication request
     * @throws BackchannelException if the request cannot be initiated
     */
    void initiateAuthentication(BackchannelAuthRequest request) throws BackchannelException;

    /**
     * Check the status of a backchannel authentication request.
     *
     * This is used for polling-based flows where the client periodically
     * checks if the user has responded to the authentication request.
     *
     * @param authReqId the authentication request ID
     * @return the current status of the authentication request
     * @throws BackchannelException if the status cannot be retrieved
     */
    BackchannelAuthStatus getAuthenticationStatus(String authReqId) throws BackchannelException;

    /**
     * Cancel a pending authentication request.
     *
     * @param authReqId the authentication request ID to cancel
     * @throws BackchannelException if the request cannot be cancelled
     */
    void cancelAuthentication(String authReqId) throws BackchannelException;

    /**
     * Clean up expired authentication requests.
     *
     * This method should be called periodically to remove old/expired requests
     * and free up resources.
     *
     * @param maxAgeSeconds maximum age in seconds for requests to keep
     * @return number of requests cleaned up
     */
    int cleanupExpiredRequests(int maxAgeSeconds);
}
