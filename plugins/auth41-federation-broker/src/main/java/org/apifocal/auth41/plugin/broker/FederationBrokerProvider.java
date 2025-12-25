package org.apifocal.auth41.plugin.broker;

import org.apifocal.auth41.plugin.trustnetwork.TrustNetwork;
import org.keycloak.provider.Provider;

/**
 * SPI for federated authentication brokering.
 *
 * <p>This provider handles the core federation logic:
 * - Initiating authentication requests to home providers
 * - Exchanging authorization codes for tokens
 * - Validating tokens from federated providers
 * - Re-issuing tokens to relying parties
 * - Adding federation metadata to tokens
 *
 * <p>The broker acts as an intermediary between the relying party and the user's home provider,
 * managing the trust path and token flow through the federation.
 */
public interface FederationBrokerProvider extends Provider {

    /**
     * Initiate a federated authentication request.
     *
     * <p>Constructs an authorization request to the user's home provider and returns
     * the authorization URL that the user should be redirected to.
     *
     * @param request Federation request with user identifier and authentication parameters
     * @param network Trust network containing provider metadata
     * @return Authorization URL to redirect the user to
     * @throws FederationException if unable to construct the authorization request
     */
    String initiateAuthenticationRequest(FederationRequest request, TrustNetwork network)
        throws FederationException;

    /**
     * Exchange authorization code for tokens.
     *
     * <p>After the user authenticates at their home provider, exchange the authorization
     * code for access token and ID token.
     *
     * @param code Authorization code from home provider
     * @param homeProviderId ID of the home provider
     * @param network Trust network containing provider metadata
     * @return TokenSet containing access token, ID token, and optionally refresh token
     * @throws FederationException if token exchange fails
     */
    TokenSet exchangeCodeForToken(String code, String homeProviderId, TrustNetwork network)
        throws FederationException;

    /**
     * Validate a token from a federated provider.
     *
     * <p>Validates the token signature using the provider's JWKS, checks standard claims
     * (issuer, expiration, audience), and extracts user information.
     *
     * @param token ID token or access token from federated provider
     * @param homeProviderId ID of the home provider that issued the token
     * @param network Trust network containing provider metadata
     * @return Validation result with extracted claims
     * @throws FederationException if token validation fails
     */
    TokenValidationResult validateToken(String token, String homeProviderId, TrustNetwork network)
        throws FederationException;

    /**
     * Re-issue a token to the relying party.
     *
     * <p>Creates a new token issued by the current provider (hub), adding federation metadata
     * such as the home provider ID, trust path, and hop count.
     *
     * @param homeTokens Tokens from the home provider
     * @param request Original federation request
     * @param network Trust network for computing trust path
     * @return TokenSet with new tokens issued by current provider
     * @throws FederationException if token re-issuance fails
     */
    TokenSet reissueToken(TokenSet homeTokens, FederationRequest request, TrustNetwork network)
        throws FederationException;

    /**
     * Initiate a CIBA authentication request.
     *
     * <p>For Client Initiated Backchannel Authentication, forwards the CIBA request
     * to the user's home provider and returns the auth_req_id.
     *
     * @param request Federation request with user identifier
     * @param network Trust network containing provider metadata
     * @return CIBA authentication request ID
     * @throws FederationException if CIBA initiation fails
     */
    String initiateCibaRequest(FederationRequest request, TrustNetwork network)
        throws FederationException;

    /**
     * Poll for CIBA token.
     *
     * <p>Polls the home provider for CIBA authentication completion and token issuance.
     *
     * @param authReqId CIBA authentication request ID
     * @param homeProviderId ID of the home provider
     * @param network Trust network containing provider metadata
     * @return TokenSet if authentication complete, null if still pending
     * @throws FederationException if polling fails
     */
    TokenSet pollCibaToken(String authReqId, String homeProviderId, TrustNetwork network)
        throws FederationException;
}
