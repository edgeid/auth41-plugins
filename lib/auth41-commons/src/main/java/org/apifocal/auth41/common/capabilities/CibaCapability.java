package org.apifocal.auth41.common.capabilities;

/**
 * CIBA (Client-Initiated Backchannel Authentication) capability constants and utilities.
 *
 * <p>CIBA allows clients to initiate authentication without user interaction on the same device.
 * This is useful for scenarios like:
 * <ul>
 *   <li>Mobile app authentication from a different device</li>
 *   <li>Push notifications for authentication</li>
 *   <li>Decoupled authentication flows</li>
 * </ul>
 *
 * @see <a href="https://openid.net/specs/openid-client-initiated-backchannel-authentication-core-1_0.html">CIBA Specification</a>
 */
public class CibaCapability {

    /**
     * Metadata key for CIBA support flag in provider attributes.
     */
    public static final String CIBA_SUPPORTED_KEY = "ciba_supported";

    /**
     * Metadata key for CIBA backchannel authentication endpoint.
     */
    public static final String CIBA_ENDPOINT_KEY = "backchannel_authentication_endpoint";

    /**
     * Metadata key for CIBA token delivery modes.
     * Values: "poll", "ping", "push"
     */
    public static final String CIBA_TOKEN_DELIVERY_MODES_KEY = "backchannel_token_delivery_modes_supported";

    /**
     * Metadata key for CIBA authentication request signing algorithm.
     */
    public static final String CIBA_SIGNING_ALG_KEY = "backchannel_authentication_request_signing_alg_values_supported";

    /**
     * Metadata key for CIBA user code parameter support.
     */
    public static final String CIBA_USER_CODE_SUPPORTED_KEY = "backchannel_user_code_parameter_supported";

    /**
     * Poll mode: client polls the token endpoint.
     */
    public static final String DELIVERY_MODE_POLL = "poll";

    /**
     * Ping mode: server pings the client when authentication completes.
     */
    public static final String DELIVERY_MODE_PING = "ping";

    /**
     * Push mode: server pushes tokens to client callback.
     */
    public static final String DELIVERY_MODE_PUSH = "push";

    private CibaCapability() {
        // Utility class
    }

    /**
     * Check if a boolean-like string value represents true.
     *
     * <p>Accepts: "true", "yes", "1", "enabled" (case-insensitive)
     *
     * @param value the value to check
     * @return true if represents a truthy value
     */
    public static boolean isTruthy(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return "true".equals(normalized) ||
               "yes".equals(normalized) ||
               "1".equals(normalized) ||
               "enabled".equals(normalized);
    }

    /**
     * Validate that a token delivery mode is supported.
     *
     * @param mode the delivery mode to validate
     * @return true if valid mode
     */
    public static boolean isValidDeliveryMode(String mode) {
        if (mode == null) {
            return false;
        }
        return DELIVERY_MODE_POLL.equalsIgnoreCase(mode) ||
               DELIVERY_MODE_PING.equalsIgnoreCase(mode) ||
               DELIVERY_MODE_PUSH.equalsIgnoreCase(mode);
    }
}
