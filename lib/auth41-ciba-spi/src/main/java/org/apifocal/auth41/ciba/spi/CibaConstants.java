package org.apifocal.auth41.ciba.spi;

/**
 * Constants for CIBA (Client-Initiated Backchannel Authentication) implementation.
 */
public final class CibaConstants {

    private CibaConstants() {
        // Utility class
    }

    // CIBA grant type
    public static final String GRANT_TYPE_CIBA = "urn:openid:params:grant-type:ciba";

    // CIBA token delivery modes
    public static final String MODE_POLL = "poll";
    public static final String MODE_PING = "ping";
    public static final String MODE_PUSH = "push";

    // CIBA request parameters
    public static final String PARAM_LOGIN_HINT = "login_hint";
    public static final String PARAM_LOGIN_HINT_TOKEN = "login_hint_token";
    public static final String PARAM_ID_TOKEN_HINT = "id_token_hint";
    public static final String PARAM_BINDING_MESSAGE = "binding_message";
    public static final String PARAM_USER_CODE = "user_code";
    public static final String PARAM_REQUESTED_EXPIRY = "requested_expiry";
    public static final String PARAM_CLIENT_NOTIFICATION_TOKEN = "client_notification_token";

    // CIBA response parameters
    public static final String PARAM_AUTH_REQ_ID = "auth_req_id";
    public static final String PARAM_EXPIRES_IN = "expires_in";
    public static final String PARAM_INTERVAL = "interval";

    // CIBA error codes
    public static final String ERROR_EXPIRED_TOKEN = "expired_token";
    public static final String ERROR_AUTHORIZATION_PENDING = "authorization_pending";
    public static final String ERROR_SLOW_DOWN = "slow_down";
    public static final String ERROR_ACCESS_DENIED = "access_denied";
    public static final String ERROR_INVALID_REQUEST = "invalid_request";
    public static final String ERROR_UNAUTHORIZED_CLIENT = "unauthorized_client";
    public static final String ERROR_INVALID_GRANT = "invalid_grant";

    // Default configuration values
    public static final int DEFAULT_EXPIRES_IN = 300; // 5 minutes
    public static final int DEFAULT_POLL_INTERVAL = 5; // 5 seconds
    public static final int DEFAULT_CLEANUP_THRESHOLD = 3600; // 1 hour
    public static final int MAX_BINDING_MESSAGE_LENGTH = 256; // Maximum length for binding message display

    // Discovery document keys
    public static final String DISCOVERY_BACKCHANNEL_AUTH_ENDPOINT = "backchannel_authentication_endpoint";
    public static final String DISCOVERY_BACKCHANNEL_TOKEN_DELIVERY_MODES = "backchannel_token_delivery_modes_supported";
    public static final String DISCOVERY_BACKCHANNEL_AUTH_REQUEST_SIGNING_ALG = "backchannel_authentication_request_signing_alg_values_supported";
    public static final String DISCOVERY_BACKCHANNEL_USER_CODE_PARAM = "backchannel_user_code_parameter_supported";
}
