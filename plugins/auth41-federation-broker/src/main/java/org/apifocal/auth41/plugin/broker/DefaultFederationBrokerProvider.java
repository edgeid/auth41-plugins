package org.apifocal.auth41.plugin.broker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apifocal.auth41.plugin.topology.TopologyProvider;
import org.apifocal.auth41.plugin.topology.TrustPath;
import org.apifocal.auth41.plugin.trustnetwork.ProviderNode;
import org.apifocal.auth41.plugin.trustnetwork.TrustNetwork;
import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Default implementation of FederationBrokerProvider.
 *
 * <p>Handles federated authentication flows by:
 * - Constructing authorization requests to home providers
 * - Exchanging authorization codes for tokens
 * - Validating tokens from federated providers
 * - Re-issuing tokens with federation metadata
 * - Supporting CIBA backchannel authentication
 */
public class DefaultFederationBrokerProvider implements FederationBrokerProvider {

    private static final Logger logger = Logger.getLogger(DefaultFederationBrokerProvider.class);
    private static final int HTTP_CONNECT_TIMEOUT_SECONDS = 10;
    private static final int HTTP_REQUEST_TIMEOUT_SECONDS = 30;
    private static final String DEFAULT_BROKER_CLIENT_ID = "federation-broker";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(HTTP_CONNECT_TIMEOUT_SECONDS))
        .build();

    private final KeycloakSession session;
    private final String brokerClientId;

    public DefaultFederationBrokerProvider(KeycloakSession session) {
        this(session, DEFAULT_BROKER_CLIENT_ID);
    }

    public DefaultFederationBrokerProvider(KeycloakSession session, String brokerClientId) {
        this.session = session;
        this.brokerClientId = brokerClientId != null ? brokerClientId : DEFAULT_BROKER_CLIENT_ID;
    }

    @Override
    public String initiateAuthenticationRequest(FederationRequest request, TrustNetwork network)
            throws FederationException {

        // Get home provider metadata
        ProviderNode homeProvider = network.getProviders().get(request.getHomeProviderId());
        if (homeProvider == null) {
            throw new FederationException("Home provider not found in network: " + request.getHomeProviderId());
        }

        String authEndpoint = homeProvider.getMetadata().getAuthorizationEndpoint();
        if (authEndpoint == null || authEndpoint.isEmpty()) {
            throw new FederationException("Authorization endpoint not configured for provider: " +
                request.getHomeProviderId());
        }

        // Validate trust path
        TopologyProvider topology = session.getProvider(TopologyProvider.class);
        if (topology != null) {
            TrustPath path = topology.computeTrustPath(
                network,
                request.getCurrentProviderId(),
                request.getHomeProviderId()
            );

            if (!path.isReachable()) {
                throw new FederationException("No trust path exists from " +
                    request.getCurrentProviderId() + " to " + request.getHomeProviderId());
            }

            logger.infof("Trust path computed: %s (hop count: %d)",
                String.join(" → ", path.getPath()), path.getHopCount());
        }

        // Build authorization URL
        return buildAuthorizationUrl(authEndpoint, request);
    }

    @Override
    public TokenSet exchangeCodeForToken(String code, String homeProviderId, TrustNetwork network)
            throws FederationException {

        if (code == null || code.isEmpty()) {
            throw new FederationException("Authorization code is required");
        }

        // Get home provider metadata
        ProviderNode homeProvider = network.getProviders().get(homeProviderId);
        if (homeProvider == null) {
            throw new FederationException("Home provider not found in network: " + homeProviderId);
        }

        String tokenEndpoint = homeProvider.getMetadata().getTokenEndpoint();
        if (tokenEndpoint == null || tokenEndpoint.isEmpty()) {
            throw new FederationException("Token endpoint not configured for provider: " + homeProviderId);
        }

        try {
            // Build token request
            Map<String, String> params = new HashMap<>();
            params.put("grant_type", "authorization_code");
            params.put("code", code);
            // Note: In production, client_id and redirect_uri would come from session state
            params.put("client_id", brokerClientId);

            String formData = buildFormData(params);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(tokenEndpoint))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formData))
                .timeout(Duration.ofSeconds(30))
                .build();

            HttpResponse<String> response = httpClient.send(httpRequest,
                HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new FederationException("Token exchange failed with status: " +
                    response.statusCode() + ", body: " + response.body());
            }

            return parseTokenResponse(response.body());

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FederationException("Failed to exchange code for token", e);
        }
    }

    @Override
    public TokenValidationResult validateToken(String token, String homeProviderId, TrustNetwork network)
            throws FederationException {

        if (token == null || token.isEmpty()) {
            return TokenValidationResult.invalid("Token is required");
        }

        // Get home provider metadata
        ProviderNode homeProvider = network.getProviders().get(homeProviderId);
        if (homeProvider == null) {
            return TokenValidationResult.invalid("Home provider not found: " + homeProviderId);
        }

        try {
            // Decode JWT (simplified - in production use proper JWT library)
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return TokenValidationResult.invalid("Invalid JWT format");
            }

            // Decode payload
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            JsonNode claims = objectMapper.readTree(payload);

            // Validate issuer
            String issuer = claims.get("iss").asText();
            if (!issuer.equals(homeProvider.getIssuer())) {
                return TokenValidationResult.invalid("Invalid issuer: " + issuer);
            }

            // Validate expiration
            long exp = claims.get("exp").asLong();
            long now = System.currentTimeMillis() / 1000;
            if (exp < now) {
                return TokenValidationResult.invalid("Token expired");
            }

            // Extract claims
            String sub = claims.get("sub").asText();
            Map<String, Object> claimsMap = new HashMap<>();
            claims.fields().forEachRemaining(entry -> {
                claimsMap.put(entry.getKey(), entry.getValue());
            });

            return TokenValidationResult.builder()
                .valid(true)
                .subject(sub)
                .issuer(issuer)
                .expiresAt(exp)
                .claims(claimsMap)
                .build();

        } catch (Exception e) {
            return TokenValidationResult.invalid("Token validation failed: " + e.getMessage());
        }
    }

    @Override
    public TokenSet reissueToken(TokenSet homeTokens, FederationRequest request, TrustNetwork network)
            throws FederationException {

        // Validate home tokens
        TokenValidationResult validation = validateToken(
            homeTokens.getIdToken(),
            request.getHomeProviderId(),
            network
        );

        if (!validation.isValid()) {
            throw new FederationException("Cannot reissue token: " + validation.getErrorMessage());
        }

        // Compute trust path for metadata
        TopologyProvider topology = session.getProvider(TopologyProvider.class);
        TrustPath path = null;
        if (topology != null) {
            path = topology.computeTrustPath(
                network,
                request.getCurrentProviderId(),
                request.getHomeProviderId()
            );
        }

        // In production, this would use Keycloak's token signing APIs
        // For now, return a simplified token set with federation metadata
        Map<String, Object> federationClaims = new HashMap<>(validation.getClaims());
        federationClaims.put("federated_from", request.getHomeProviderId());
        federationClaims.put("home_subject", validation.getSubject());

        if (path != null) {
            federationClaims.put("trust_path", String.join("→", path.getPath()));
            federationClaims.put("hop_count", path.getHopCount());
        }

        return TokenSet.builder()
            .accessToken(homeTokens.getAccessToken()) // Would be re-signed in production
            .idToken(homeTokens.getIdToken())         // Would be re-signed in production
            .refreshToken(homeTokens.getRefreshToken())
            .tokenType("Bearer")
            .expiresIn(homeTokens.getExpiresIn())
            .additionalClaims(federationClaims)
            .build();
    }

    @Override
    public String initiateCibaRequest(FederationRequest request, TrustNetwork network)
            throws FederationException {

        // Get home provider metadata
        ProviderNode homeProvider = network.getProviders().get(request.getHomeProviderId());
        if (homeProvider == null) {
            throw new FederationException("Home provider not found in network: " + request.getHomeProviderId());
        }

        // Check CIBA support
        Map<String, String> attributes = homeProvider.getAttributes();
        String cibaSupported = attributes != null ? attributes.get("ciba_supported") : null;
        if (!"true".equalsIgnoreCase(cibaSupported)) {
            throw new FederationException("Provider does not support CIBA: " + request.getHomeProviderId());
        }

        String cibaEndpoint = homeProvider.getMetadata().getBackchannelAuthenticationEndpoint();
        if (cibaEndpoint == null || cibaEndpoint.isEmpty()) {
            throw new FederationException("CIBA endpoint not configured for provider: " + request.getHomeProviderId());
        }

        try {
            // Build CIBA request
            Map<String, String> params = new HashMap<>();
            params.put("login_hint", request.getUserIdentifier());
            params.put("scope", request.getScope());
            params.put("client_id", request.getClientId());
            // Note: In production would include client authentication

            String formData = buildFormData(params);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(cibaEndpoint))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formData))
                .timeout(Duration.ofSeconds(30))
                .build();

            HttpResponse<String> response = httpClient.send(httpRequest,
                HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new FederationException("CIBA request failed with status: " +
                    response.statusCode() + ", body: " + response.body());
            }

            JsonNode responseJson = objectMapper.readTree(response.body());
                        JsonNode authReqIdNode = responseJson.get("auth_req_id");
            if (authReqIdNode == null || authReqIdNode.isNull()) {
                throw new FederationException("CIBA response missing required 'auth_req_id' field. Body: " + response.body());
            }
            return authReqIdNode.asText();

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FederationException("Failed to initiate CIBA request", e);
        }
    }

    @Override
    public TokenSet pollCibaToken(String authReqId, String homeProviderId, TrustNetwork network)
            throws FederationException {

        if (authReqId == null || authReqId.isEmpty()) {
            throw new FederationException("Auth request ID is required");
        }

        // Get home provider metadata
        ProviderNode homeProvider = network.getProviders().get(homeProviderId);
        if (homeProvider == null) {
            throw new FederationException("Home provider not found in network: " + homeProviderId);
        }

        String tokenEndpoint = homeProvider.getMetadata().getTokenEndpoint();
        if (tokenEndpoint == null || tokenEndpoint.isEmpty()) {
            throw new FederationException("Token endpoint not configured for provider: " + homeProviderId);
        }

        try {
            // Build token request with CIBA grant type
            Map<String, String> params = new HashMap<>();
            params.put("grant_type", "urn:openid:params:grant-type:ciba");
            params.put("auth_req_id", authReqId);
            params.put("client_id", brokerClientId);

            String formData = buildFormData(params);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(tokenEndpoint))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formData))
                .timeout(Duration.ofSeconds(30))
                .build();

            HttpResponse<String> response = httpClient.send(httpRequest,
                HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 400) {
                // Check if authorization is still pending
                JsonNode error = objectMapper.readTree(response.body());
                JsonNode errorNode = error != null ? error.get("error") : null;
                if (errorNode != null && "authorization_pending".equals(errorNode.asText())) {
                    return null; // Still pending
                }
            }

            if (response.statusCode() != 200) {
                throw new FederationException("CIBA token poll failed with status: " +
                    response.statusCode() + ", body: " + response.body());
            }

            return parseTokenResponse(response.body());

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FederationException("Failed to poll CIBA token", e);
        }
    }

    @Override
    public void close() {
        // No resources to clean up
    }

    // Helper methods

    private String buildAuthorizationUrl(String authEndpoint, FederationRequest request) {
        StringBuilder url = new StringBuilder(authEndpoint);
        url.append("?response_type=code");
        url.append("&client_id=").append(urlEncode(request.getClientId()));
        url.append("&scope=").append(urlEncode(request.getScope()));

        if (request.getRedirectUri() != null) {
            url.append("&redirect_uri=").append(urlEncode(request.getRedirectUri()));
        }
        if (request.getState() != null) {
            url.append("&state=").append(urlEncode(request.getState()));
        }
        if (request.getNonce() != null) {
            url.append("&nonce=").append(urlEncode(request.getNonce()));
        }
        url.append("&login_hint=").append(urlEncode(request.getUserIdentifier()));

        return url.toString();
    }

    private String buildFormData(Map<String, String> params) {
        StringBuilder formData = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                formData.append("&");
            }
            formData.append(urlEncode(entry.getKey()))
                .append("=")
                .append(urlEncode(entry.getValue()));
            first = false;
        }
        return formData.toString();
    }

    private TokenSet parseTokenResponse(String responseBody) throws FederationException {
        try {
            JsonNode json = objectMapper.readTree(responseBody);

            return TokenSet.builder()
                .accessToken(json.get("access_token").asText())
                .idToken(json.has("id_token") ? json.get("id_token").asText() : null)
                .refreshToken(json.has("refresh_token") ? json.get("refresh_token").asText() : null)
                .tokenType(json.has("token_type") ? json.get("token_type").asText() : "Bearer")
                .expiresIn(json.has("expires_in") ? json.get("expires_in").asLong() : 0)
                .scope(json.has("scope") ? json.get("scope").asText() : null)
                .build();
        } catch (IOException e) {
            throw new FederationException("Failed to parse token response", e);
        }
    }

    private String urlEncode(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
