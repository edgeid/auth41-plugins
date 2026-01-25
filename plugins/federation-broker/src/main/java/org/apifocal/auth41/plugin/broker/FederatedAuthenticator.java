package org.apifocal.auth41.plugin.broker;

import org.apifocal.auth41.plugin.discovery.ProviderDiscoveryService;
import org.apifocal.auth41.plugin.topology.TopologyProvider;
import org.apifocal.auth41.plugin.topology.TrustPath;
import org.apifocal.auth41.plugin.trustnetwork.ProviderNode;
import org.apifocal.auth41.plugin.trustnetwork.TrustNetwork;
import org.apifocal.auth41.plugin.trustnetwork.TrustNetworkProvider;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserProvider;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.Set;

/**
 * Keycloak authenticator that implements federated authentication flows.
 *
 * <p>This authenticator integrates into Keycloak's browser authentication flow to enable
 * federated authentication. It:
 * - Extracts user identifier from login_hint
 * - Discovers user's home provider via ProviderDiscoveryService
 * - Validates trust path via TopologyProvider
 * - Redirects to home provider for authentication
 * - Handles callback with authorization code
 * - Validates tokens from home provider
 * - Creates/updates shadow user in local realm
 *
 * <p>Configuration:
 * - Add to realm's browser authentication flow
 * - Set as ALTERNATIVE or REQUIRED
 * - Configure trust network via TrustNetworkProvider
 */
public class FederatedAuthenticator implements Authenticator {

    private static final Logger logger = Logger.getLogger(FederatedAuthenticator.class);
    private static final String LOGIN_HINT_PARAM = "login_hint";
    private static final String CODE_PARAM = "code";
    private static final String STATE_PARAM = "state";
    private static final String ERROR_PARAM = "error";
    private static final String AUTH_NOTE_STATE = "federation_state";
    private static final String AUTH_NOTE_HOME_PROVIDER = "home_provider_id";
    private static final String AUTH_NOTE_REDIRECT_URI = "federation_redirect_uri";

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        logger.info("FederatedAuthenticator.authenticate() called");

        // 1. Extract login_hint (user identifier)
        String loginHint = getLoginHint(context);

        if (loginHint == null || loginHint.isEmpty()) {
            logger.debug("No login_hint provided, showing account chooser");
            showAccountChooser(context);
            return;
        }

        logger.infof("Login hint: %s", loginHint);

        // 2. Get required services
        KeycloakSession session = context.getSession();
        ProviderDiscoveryService discovery = session.getProvider(ProviderDiscoveryService.class);
        TrustNetworkProvider trustProvider = session.getProvider(TrustNetworkProvider.class);
        TopologyProvider topology = session.getProvider(TopologyProvider.class);

        if (discovery == null) {
            logger.error("ProviderDiscoveryService not available");
            context.failure(AuthenticationFlowError.INTERNAL_ERROR);
            return;
        }

        if (trustProvider == null) {
            logger.error("TrustNetworkProvider not available");
            context.failure(AuthenticationFlowError.INTERNAL_ERROR);
            return;
        }

        // 3. Load trust network
        TrustNetwork network;
        try {
            // TODO: Make network ID configurable
            network = trustProvider.loadNetwork("default");
            if (network == null) {
                logger.error("Trust network 'default' not found");
                context.failure(AuthenticationFlowError.INTERNAL_ERROR);
                return;
            }
        } catch (Exception e) {
            logger.error("Failed to load trust network", e);
            context.failure(AuthenticationFlowError.INTERNAL_ERROR);
            return;
        }

        // 4. Discover user's home provider
        Set<String> providers = discovery.findProvidersByUser(loginHint);
        if (providers.isEmpty()) {
            logger.warnf("No home provider found for user: %s", loginHint);
            context.failure(AuthenticationFlowError.UNKNOWN_USER);
            return;
        }
        if (providers.size() > 1) {
            logger.warnf("Multiple home providers found for user %s: %s", loginHint, String.join(", ", providers));
            context.failure(AuthenticationFlowError.INVALID_USER);
            return;
        }

        String homeProviderId = providers.iterator().next();
        logger.infof("Discovered home provider: %s", homeProviderId);

        // 5. Validate trust path
        String currentProviderId = context.getRealm().getName();

        if (topology != null) {
            TrustPath path = topology.computeTrustPath(network, currentProviderId, homeProviderId);
            if (!path.isReachable()) {
                logger.warnf("No trust path from %s to %s", currentProviderId, homeProviderId);
                context.failure(AuthenticationFlowError.INVALID_USER);
                return;
            }
            logger.infof("Trust path validated: %s (hops: %d)",
                String.join(" → ", path.getPath()), path.getHopCount());
        }

        // 6. Build authentication request
        FederationBrokerProvider broker = session.getProvider(FederationBrokerProvider.class);
        if (broker == null) {
            logger.error("FederationBrokerProvider not available");
            context.failure(AuthenticationFlowError.INTERNAL_ERROR);
            return;
        }

        // Build redirect URI for callback
        String redirectUri = context.getUriInfo().getBaseUri() +
            "realms/" + context.getRealm().getName() + "/broker/auth41-federated/endpoint";

        // Generate state for CSRF protection
        String state = context.generateAccessCode();

        FederationRequest request = FederationRequest.builder()
            .userIdentifier(loginHint)
            .homeProviderId(homeProviderId)
            .currentProviderId(currentProviderId)
            .clientId(context.getAuthenticationSession().getClient().getClientId())
            .scope("openid profile email")
            .redirectUri(redirectUri)
            .state(state)
            .nonce(context.generateAccessCode())
            .build();

        try {
            String authUrl = broker.initiateAuthenticationRequest(request, network);
            logger.infof("Redirecting to home provider: %s", authUrl);

            // Store state in authentication session for validation on callback
            context.getAuthenticationSession().setAuthNote(AUTH_NOTE_STATE, state);
            context.getAuthenticationSession().setAuthNote(AUTH_NOTE_HOME_PROVIDER, homeProviderId);
            context.getAuthenticationSession().setAuthNote(AUTH_NOTE_REDIRECT_URI, redirectUri);

            // Redirect to home provider
            Response response = Response.seeOther(URI.create(authUrl)).build();
            context.challenge(response);

        } catch (FederationException e) {
            logger.error("Failed to initiate authentication request", e);
            context.failure(AuthenticationFlowError.INTERNAL_ERROR);
        }
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        logger.info("FederatedAuthenticator.action() called - handling callback");

        MultivaluedMap<String, String> params = context.getHttpRequest().getUri().getQueryParameters();

        // 1. Check for error response
        String error = params.getFirst(ERROR_PARAM);
        if (error != null) {
            logger.warnf("Error from home provider: %s", error);
            context.failure(AuthenticationFlowError.IDENTITY_PROVIDER_ERROR);
            return;
        }

        // 2. Extract authorization code
        String code = params.getFirst(CODE_PARAM);
        if (code == null || code.isEmpty()) {
            logger.warn("No authorization code in callback");
            context.failure(AuthenticationFlowError.INVALID_CREDENTIALS);
            return;
        }

        // 3. Validate state (CSRF protection)
        String returnedState = params.getFirst(STATE_PARAM);
        String expectedState = context.getAuthenticationSession().getAuthNote(AUTH_NOTE_STATE);

        if (expectedState == null || !expectedState.equals(returnedState)) {
            logger.warn("State mismatch - possible CSRF attack");
            context.failure(AuthenticationFlowError.INVALID_CREDENTIALS);
            return;
        }

        // 4. Validate redirect URI (ensure callback came to expected URI)
        String currentUri = context.getHttpRequest().getUri().getRequestUri().toString();
        String expectedRedirectUri = context.getAuthenticationSession().getAuthNote(AUTH_NOTE_REDIRECT_URI);

        if (expectedRedirectUri != null) {
            // Extract base URI without query parameters for comparison
            String currentBaseUri = currentUri.split("\\?")[0];
            if (!expectedRedirectUri.equals(currentBaseUri)) {
                logger.warnf("Redirect URI mismatch - expected: %s, got: %s",
                    expectedRedirectUri, currentBaseUri);
                context.failure(AuthenticationFlowError.INVALID_CREDENTIALS);
                return;
            }
        }

        // 5. Get home provider ID from session
        String homeProviderId = context.getAuthenticationSession().getAuthNote(AUTH_NOTE_HOME_PROVIDER);
        if (homeProviderId == null) {
            logger.error("Home provider ID not found in session");
            context.failure(AuthenticationFlowError.INTERNAL_ERROR);
            return;
        }

        // 6. Load trust network
        TrustNetworkProvider trustProvider = context.getSession().getProvider(TrustNetworkProvider.class);
        TrustNetwork network;
        try {
            network = trustProvider.loadNetwork("default");
        } catch (Exception e) {
            logger.error("Failed to load trust network", e);
            context.failure(AuthenticationFlowError.INTERNAL_ERROR);
            return;
        }

        // 7. Exchange code for token
        FederationBrokerProvider broker = context.getSession().getProvider(FederationBrokerProvider.class);
        if (broker == null) {
            logger.error("FederationBrokerProvider not available");
            context.failure(AuthenticationFlowError.INTERNAL_ERROR);
            return;
        }

        TokenSet tokens;
        try {
            tokens = broker.exchangeCodeForToken(code, homeProviderId, network);
            logger.info("Successfully exchanged code for tokens");
        } catch (FederationException e) {
            logger.error("Failed to exchange code for token", e);
            context.failure(AuthenticationFlowError.INVALID_CREDENTIALS);
            return;
        }

        // 8. Validate ID token
        TokenValidationResult validation;
        try {
            validation = broker.validateToken(tokens.getIdToken(), homeProviderId, network);
            if (!validation.isValid()) {
                logger.warnf("Token validation failed: %s", validation.getErrorMessage());
                context.failure(AuthenticationFlowError.INVALID_CREDENTIALS);
                return;
            }
            logger.info("Token validated successfully");
        } catch (FederationException e) {
            logger.error("Token validation error", e);
            context.failure(AuthenticationFlowError.INVALID_CREDENTIALS);
            return;
        }

        // 9. Create or update shadow user
        UserModel user;
        try {
            user = getOrCreateShadowUser(context, homeProviderId, validation);
            logger.infof("Shadow user created/updated: %s", user.getUsername());
        } catch (Exception e) {
            logger.error("Failed to create/update shadow user", e);
            context.failure(AuthenticationFlowError.INTERNAL_ERROR);
            return;
        }

        // 10. Set user and complete authentication
        context.setUser(user);
        context.success();
    }

    @Override
    public boolean requiresUser() {
        // User is discovered during authentication, not required upfront
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        // Always available for any user
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        // No required actions needed
    }

    @Override
    public void close() {
        // No resources to clean up
    }

    // Helper methods

    private String getLoginHint(AuthenticationFlowContext context) {
        // Check query parameters first
        MultivaluedMap<String, String> params = context.getHttpRequest().getUri().getQueryParameters();
        String loginHint = params.getFirst(LOGIN_HINT_PARAM);

        if (loginHint == null) {
            // Check form parameters
            MultivaluedMap<String, String> formParams = context.getHttpRequest().getDecodedFormParameters();
            loginHint = formParams.getFirst(LOGIN_HINT_PARAM);
        }

        return loginHint;
    }

    private void showAccountChooser(AuthenticationFlowContext context) {
        // Challenge with account chooser form
        Response response = context.form()
            .setAttribute("realm", context.getRealm())
            .createForm("account-chooser.ftl");
        context.challenge(response);
    }

    private UserModel getOrCreateShadowUser(AuthenticationFlowContext context,
                                           String homeProviderId,
                                           TokenValidationResult validation) {
        KeycloakSession session = context.getSession();
        RealmModel realm = context.getRealm();

        // Extract claims from validated token
        String subject = validation.getSubject();
        String email = getClaimAsString(validation, "email");
        String firstName = getClaimAsString(validation, "given_name");
        String lastName = getClaimAsString(validation, "family_name");

        // Create federated user identifier: homeProvider:subject
        // This is used as the Keycloak username to ensure uniqueness across providers
        String federatedUserId = homeProviderId + ":" + subject;

        // Look for existing user by federated identifier
        UserProvider userProvider = session.users();
        UserModel user = userProvider.getUserByUsername(realm, federatedUserId);

        if (user == null) {
            // Create new shadow user
            logger.infof("Creating new shadow user: %s", federatedUserId);
            user = userProvider.addUser(realm, federatedUserId);
            user.setEnabled(true);

            String emailVerifiedClaim = getClaimAsString(validation, "email_verified");
            boolean emailVerified = emailVerifiedClaim != null && Boolean.parseBoolean(emailVerifiedClaim);
            user.setEmailVerified(emailVerified);
        } else {
            logger.infof("Found existing shadow user: %s", federatedUserId);
        }

        // Update user attributes
        if (email != null) {
            user.setEmail(email);
        }
        if (firstName != null) {
            user.setFirstName(firstName);
        }
        if (lastName != null) {
            user.setLastName(lastName);
        }

        // Store federation metadata
        user.setSingleAttribute("federated_from", homeProviderId);
        user.setSingleAttribute("home_subject", subject);
        user.setSingleAttribute("federation_issuer", validation.getIssuer());

        return user;
    }

    private String getClaimAsString(TokenValidationResult validation, String claimName) {
        Object claim = validation.getClaims().get(claimName);
        if (claim == null) {
            return null;
        }

        // Handle JsonNode from Jackson
        if (claim.getClass().getName().contains("JsonNode")) {
            try {
                // Use reflection to call asText() on JsonNode
                return (String) claim.getClass().getMethod("asText").invoke(claim);
            } catch (Exception e) {
                logger.warnf("Failed to extract claim %s as text", claimName);
                return claim.toString();
            }
        }

        return claim.toString();
    }
}
