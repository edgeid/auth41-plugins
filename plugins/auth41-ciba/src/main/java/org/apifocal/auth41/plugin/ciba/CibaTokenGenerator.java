package org.apifocal.auth41.plugin.ciba;

import org.jboss.logging.Logger;
import org.keycloak.OAuth2Constants;
import org.keycloak.models.*;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.protocol.oidc.TokenManager;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.services.util.DefaultClientSessionContext;

/**
 * Helper class for generating OAuth2/OIDC tokens for CIBA authentication.
 *
 * Integrates with Keycloak's TokenManager to create proper OAuth2 token responses
 * for approved CIBA authentication requests.
 */
public class CibaTokenGenerator {

    private static final Logger logger = Logger.getLogger(CibaTokenGenerator.class);

    private final KeycloakSession session;
    private final RealmModel realm;
    private final TokenManager tokenManager;

    public CibaTokenGenerator(KeycloakSession session) {
        this.session = session;
        this.realm = session.getContext().getRealm();
        this.tokenManager = new TokenManager();
    }

    /**
     * Generate OAuth2 token response for an approved CIBA authentication.
     *
     * @param user The authenticated user
     * @param client The client requesting tokens
     * @param scope Requested OAuth2 scopes (space-separated)
     * @param authReqId The CIBA authentication request ID
     * @return TokenResponse containing access_token, refresh_token, id_token, etc.
     */
    public TokenResponse generateTokens(UserModel user, ClientModel client, String scope, String authReqId) {
        logger.debugf("Generating tokens for CIBA: user=%s, client=%s, scope=%s, authReqId=%s",
            user.getUsername(), client.getClientId(), scope, authReqId);

        try {
            // Create user session
            UserSessionModel userSession = createUserSession(user, client);

            // Create authenticated client session
            AuthenticatedClientSessionModel clientSession = createClientSession(userSession, client, scope);

            // Create ClientSessionContext (required by TokenManager)
            ClientSessionContext clientSessionCtx = DefaultClientSessionContext
                .fromClientSessionScopeParameter(clientSession, session);

            // Use TokenManager's AccessTokenResponseBuilder to generate all tokens
            TokenManager.AccessTokenResponseBuilder builder = tokenManager.responseBuilder(
                realm,
                client,
                null,  // EventBuilder (optional)
                session,
                userSession,
                clientSessionCtx
            );

            // Configure token generation based on scope
            if (isOpenIdScope(scope)) {
                builder.generateIDToken(false);  // false = not detached signature
            }

            if (shouldGenerateRefreshToken(client, scope)) {
                builder.generateRefreshToken();
            }

            // Build the response - this handles all token encoding automatically
            AccessTokenResponse response = builder.build();

            logger.infof("Successfully generated tokens for CIBA: authReqId=%s, hasRefreshToken=%s, hasIdToken=%s",
                authReqId, response.getRefreshToken() != null, response.getIdToken() != null);

            return TokenResponse.builder()
                .accessToken(response.getToken())
                .tokenType(response.getTokenType())
                .expiresIn((int) response.getExpiresIn())
                .refreshToken(response.getRefreshToken())
                .refreshExpiresIn(response.getRefreshToken() != null ? (int) response.getRefreshExpiresIn() : null)
                .idToken(response.getIdToken())
                .scope(response.getScope())
                .build();

        } catch (Exception e) {
            logger.errorf(e, "Failed to generate tokens for CIBA: authReqId=%s", authReqId);
            throw new TokenGenerationException("Failed to generate OAuth2 tokens", e);
        }
    }

    /**
     * Create a user session for the authenticated user.
     *
     * In CIBA flow, we create a new session when authentication is approved.
     */
    private UserSessionModel createUserSession(UserModel user, ClientModel client) {
        UserSessionModel userSession = session.sessions().createUserSession(
            realm,
            user,
            user.getUsername(),
            session.getContext().getConnection().getRemoteAddr(),
            "ciba", // authentication method
            false, // rememberMe
            null, // brokerSessionId
            null  // brokerUserId
        );

        logger.debugf("Created user session for CIBA: sessionId=%s, user=%s",
            userSession.getId(), user.getUsername());

        return userSession;
    }

    /**
     * Create an authenticated client session linked to the user session.
     */
    private AuthenticatedClientSessionModel createClientSession(
            UserSessionModel userSession,
            ClientModel client,
            String scope) {

        AuthenticatedClientSessionModel clientSession = userSession.getAuthenticatedClientSessionByClient(client.getId());

        if (clientSession == null) {
            clientSession = session.sessions().createClientSession(realm, client, userSession);
        }

        // Set protocol
        clientSession.setProtocol(OIDCLoginProtocol.LOGIN_PROTOCOL);

        // Set redirect URI (not used in CIBA, but required by some token mappers)
        clientSession.setRedirectUri("");

        // Set requested scopes
        if (scope != null && !scope.isEmpty()) {
            clientSession.setNote(OAuth2Constants.SCOPE, scope);
        }

        logger.debugf("Created client session for CIBA: clientSessionId=%s, client=%s",
            clientSession.getId(), client.getClientId());

        return clientSession;
    }

    /**
     * Check if refresh token should be generated.
     *
     * Refresh tokens are typically only for confidential clients or public clients with offline access.
     */
    private boolean shouldGenerateRefreshToken(ClientModel client, String scope) {
        // Check if offline_access scope is requested
        boolean hasOfflineAccess = scope != null && scope.contains(OAuth2Constants.OFFLINE_ACCESS);

        // Confidential clients always get refresh tokens
        // Public clients only if offline_access is requested
        return client.isPublicClient() ? hasOfflineAccess : true;
    }

    /**
     * Check if openid scope is requested (required for ID token).
     */
    private boolean isOpenIdScope(String scope) {
        return scope != null && scope.contains("openid");
    }

    /**
     * Token response builder for OAuth2/OIDC token responses.
     */
    public static class TokenResponse {
        private final String accessToken;
        private final String tokenType;
        private final Integer expiresIn;
        private final String refreshToken;
        private final Integer refreshExpiresIn;
        private final String idToken;
        private final String scope;

        private TokenResponse(Builder builder) {
            this.accessToken = builder.accessToken;
            this.tokenType = builder.tokenType;
            this.expiresIn = builder.expiresIn;
            this.refreshToken = builder.refreshToken;
            this.refreshExpiresIn = builder.refreshExpiresIn;
            this.idToken = builder.idToken;
            this.scope = builder.scope;
        }

        public String getAccessToken() { return accessToken; }
        public String getTokenType() { return tokenType; }
        public Integer getExpiresIn() { return expiresIn; }
        public String getRefreshToken() { return refreshToken; }
        public Integer getRefreshExpiresIn() { return refreshExpiresIn; }
        public String getIdToken() { return idToken; }
        public String getScope() { return scope; }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String accessToken;
            private String tokenType;
            private Integer expiresIn;
            private String refreshToken;
            private Integer refreshExpiresIn;
            private String idToken;
            private String scope;

            public Builder accessToken(String accessToken) {
                this.accessToken = accessToken;
                return this;
            }

            public Builder tokenType(String tokenType) {
                this.tokenType = tokenType;
                return this;
            }

            public Builder expiresIn(Integer expiresIn) {
                this.expiresIn = expiresIn;
                return this;
            }

            public Builder refreshToken(String refreshToken) {
                this.refreshToken = refreshToken;
                return this;
            }

            public Builder refreshExpiresIn(Integer refreshExpiresIn) {
                this.refreshExpiresIn = refreshExpiresIn;
                return this;
            }

            public Builder idToken(String idToken) {
                this.idToken = idToken;
                return this;
            }

            public Builder scope(String scope) {
                this.scope = scope;
                return this;
            }

            public TokenResponse build() {
                return new TokenResponse(this);
            }
        }
    }

    /**
     * Exception thrown when token generation fails.
     */
    public static class TokenGenerationException extends RuntimeException {
        public TokenGenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
