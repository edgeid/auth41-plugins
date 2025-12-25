package org.apifocal.auth41.plugin.broker;

import org.apifocal.auth41.plugin.topology.TopologyProvider;
import org.apifocal.auth41.plugin.topology.TrustPath;
import org.apifocal.auth41.plugin.trustnetwork.ProviderMetadata;
import org.apifocal.auth41.plugin.trustnetwork.ProviderNode;
import org.apifocal.auth41.plugin.trustnetwork.TrustNetwork;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.models.KeycloakSession;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultFederationBrokerProviderTest {

    @Mock
    private KeycloakSession session;

    @Mock
    private TopologyProvider topologyProvider;

    @Mock
    private TrustNetwork trustNetwork;

    private DefaultFederationBrokerProvider provider;

    @BeforeEach
    void setUp() {
        lenient().when(session.getProvider(TopologyProvider.class)).thenReturn(topologyProvider);
        provider = new DefaultFederationBrokerProvider(session);
    }

    @Test
    void testInitiateAuthenticationRequest() throws FederationException {
        // Setup provider metadata
        ProviderNode homeProvider = ProviderNode.builder()
            .providerId("provider-a")
            .issuer("https://provider-a.example.com")
            .metadata(ProviderMetadata.builder()
                .authorizationEndpoint("https://provider-a.example.com/auth")
                .build())
            .build();

        Map<String, ProviderNode> providers = new HashMap<>();
        providers.put("provider-a", homeProvider);
        when(trustNetwork.getProviders()).thenReturn(providers);

        // Setup trust path
        TrustPath path = TrustPath.builder()
            .sourceProvider("hub-a")
            .targetProvider("provider-a")
            .path(List.of("hub-a", "provider-a"))
            .reachable(true)
            .build();
        when(topologyProvider.computeTrustPath(trustNetwork, "hub-a", "provider-a"))
            .thenReturn(path);

        // Create federation request
        FederationRequest request = FederationRequest.builder()
            .userIdentifier("user@example.com")
            .homeProviderId("provider-a")
            .currentProviderId("hub-a")
            .clientId("test-client")
            .scope("openid profile")
            .state("random-state")
            .nonce("random-nonce")
            .redirectUri("https://hub-a.example.com/callback")
            .build();

        String authUrl = provider.initiateAuthenticationRequest(request, trustNetwork);

        assertThat(authUrl).isNotNull();
        assertThat(authUrl).startsWith("https://provider-a.example.com/auth");
        assertThat(authUrl).contains("response_type=code");
        assertThat(authUrl).contains("client_id=test-client");
        assertThat(authUrl).contains("scope=openid");
        assertThat(authUrl).contains("login_hint=user%40example.com");
        assertThat(authUrl).contains("state=random-state");
        assertThat(authUrl).contains("nonce=random-nonce");
    }

    @Test
    void testInitiateAuthenticationRequestProviderNotFound() {
        when(trustNetwork.getProviders()).thenReturn(new HashMap<>());

        FederationRequest request = FederationRequest.builder()
            .userIdentifier("user@example.com")
            .homeProviderId("unknown-provider")
            .currentProviderId("hub-a")
            .clientId("test-client")
            .build();

        assertThatThrownBy(() -> provider.initiateAuthenticationRequest(request, trustNetwork))
            .isInstanceOf(FederationException.class)
            .hasMessageContaining("Home provider not found in network");
    }

    @Test
    void testInitiateAuthenticationRequestNoAuthEndpoint() {
        ProviderNode homeProvider = ProviderNode.builder()
            .providerId("provider-a")
            .issuer("https://provider-a.example.com")
            .metadata(ProviderMetadata.builder().build()) // No auth endpoint
            .build();

        Map<String, ProviderNode> providers = new HashMap<>();
        providers.put("provider-a", homeProvider);
        when(trustNetwork.getProviders()).thenReturn(providers);

        FederationRequest request = FederationRequest.builder()
            .userIdentifier("user@example.com")
            .homeProviderId("provider-a")
            .currentProviderId("hub-a")
            .clientId("test-client")
            .build();

        assertThatThrownBy(() -> provider.initiateAuthenticationRequest(request, trustNetwork))
            .isInstanceOf(FederationException.class)
            .hasMessageContaining("Authorization endpoint not configured");
    }

    @Test
    void testInitiateAuthenticationRequestNoTrustPath() {
        ProviderNode homeProvider = ProviderNode.builder()
            .providerId("provider-a")
            .issuer("https://provider-a.example.com")
            .metadata(ProviderMetadata.builder()
                .authorizationEndpoint("https://provider-a.example.com/auth")
                .build())
            .build();

        Map<String, ProviderNode> providers = new HashMap<>();
        providers.put("provider-a", homeProvider);
        when(trustNetwork.getProviders()).thenReturn(providers);

        // No trust path exists
        TrustPath path = TrustPath.builder()
            .sourceProvider("hub-a")
            .targetProvider("provider-a")
            .path(List.of())
            .reachable(false)
            .build();
        when(topologyProvider.computeTrustPath(trustNetwork, "hub-a", "provider-a"))
            .thenReturn(path);

        FederationRequest request = FederationRequest.builder()
            .userIdentifier("user@example.com")
            .homeProviderId("provider-a")
            .currentProviderId("hub-a")
            .clientId("test-client")
            .build();

        assertThatThrownBy(() -> provider.initiateAuthenticationRequest(request, trustNetwork))
            .isInstanceOf(FederationException.class)
            .hasMessageContaining("No trust path exists");
    }

    @Test
    void testValidateTokenInvalidFormat() throws FederationException {
        ProviderNode homeProvider = ProviderNode.builder()
            .providerId("provider-a")
            .issuer("https://provider-a.example.com")
            .build();

        Map<String, ProviderNode> providers = new HashMap<>();
        providers.put("provider-a", homeProvider);
        when(trustNetwork.getProviders()).thenReturn(providers);

        TokenValidationResult result = provider.validateToken("invalid.token", "provider-a", trustNetwork);

        assertThat(result).isNotNull();
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("Invalid JWT format");
    }

    @Test
    void testValidateTokenProviderNotFound() throws FederationException {
        when(trustNetwork.getProviders()).thenReturn(new HashMap<>());

        TokenValidationResult result = provider.validateToken("header.payload.signature", "unknown-provider", trustNetwork);

        assertThat(result).isNotNull();
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("Home provider not found");
    }

    @Test
    void testInitiateCibaRequestProviderNotFound() {
        when(trustNetwork.getProviders()).thenReturn(new HashMap<>());

        FederationRequest request = FederationRequest.builder()
            .userIdentifier("user@example.com")
            .homeProviderId("unknown-provider")
            .currentProviderId("hub-a")
            .clientId("test-client")
            .build();

        assertThatThrownBy(() -> provider.initiateCibaRequest(request, trustNetwork))
            .isInstanceOf(FederationException.class)
            .hasMessageContaining("Home provider not found in network");
    }

    @Test
    void testInitiateCibaRequestNoCibaSupport() {
        ProviderNode homeProvider = ProviderNode.builder()
            .providerId("provider-a")
            .issuer("https://provider-a.example.com")
            .attribute("ciba_supported", "false") // CIBA not supported
            .build();

        Map<String, ProviderNode> providers = new HashMap<>();
        providers.put("provider-a", homeProvider);
        when(trustNetwork.getProviders()).thenReturn(providers);

        FederationRequest request = FederationRequest.builder()
            .userIdentifier("user@example.com")
            .homeProviderId("provider-a")
            .currentProviderId("hub-a")
            .clientId("test-client")
            .build();

        assertThatThrownBy(() -> provider.initiateCibaRequest(request, trustNetwork))
            .isInstanceOf(FederationException.class)
            .hasMessageContaining("Provider does not support CIBA");
    }

    @Test
    void testPollCibaTokenNullAuthReqId() {
        assertThatThrownBy(() -> provider.pollCibaToken(null, "provider-a", trustNetwork))
            .isInstanceOf(FederationException.class)
            .hasMessageContaining("Auth request ID is required");
    }

    @Test
    void testPollCibaTokenProviderNotFound() {
        when(trustNetwork.getProviders()).thenReturn(new HashMap<>());

        assertThatThrownBy(() -> provider.pollCibaToken("auth-req-123", "unknown-provider", trustNetwork))
            .isInstanceOf(FederationException.class)
            .hasMessageContaining("Home provider not found in network");
    }

    @Test
    void testFederationRequestBuilder() {
        FederationRequest request = FederationRequest.builder()
            .userIdentifier("user@example.com")
            .homeProviderId("provider-a")
            .currentProviderId("hub-a")
            .clientId("test-client")
            .scope("openid profile email")
            .redirectUri("https://example.com/callback")
            .state("state-123")
            .nonce("nonce-456")
            .additionalParam("prompt", "consent")
            .build();

        assertThat(request.getUserIdentifier()).isEqualTo("user@example.com");
        assertThat(request.getHomeProviderId()).isEqualTo("provider-a");
        assertThat(request.getCurrentProviderId()).isEqualTo("hub-a");
        assertThat(request.getClientId()).isEqualTo("test-client");
        assertThat(request.getScope()).isEqualTo("openid profile email");
        assertThat(request.getRedirectUri()).isEqualTo("https://example.com/callback");
        assertThat(request.getState()).isEqualTo("state-123");
        assertThat(request.getNonce()).isEqualTo("nonce-456");
        assertThat(request.getAdditionalParams()).containsEntry("prompt", "consent");
    }

    @Test
    void testTokenSetBuilder() {
        TokenSet tokens = TokenSet.builder()
            .accessToken("access-token-123")
            .idToken("id-token-456")
            .refreshToken("refresh-token-789")
            .tokenType("Bearer")
            .expiresIn(3600)
            .scope("openid profile")
            .additionalClaim("custom_claim", "custom_value")
            .build();

        assertThat(tokens.getAccessToken()).isEqualTo("access-token-123");
        assertThat(tokens.getIdToken()).isEqualTo("id-token-456");
        assertThat(tokens.getRefreshToken()).isEqualTo("refresh-token-789");
        assertThat(tokens.getTokenType()).isEqualTo("Bearer");
        assertThat(tokens.getExpiresIn()).isEqualTo(3600);
        assertThat(tokens.getScope()).isEqualTo("openid profile");
        assertThat(tokens.getAdditionalClaims()).containsEntry("custom_claim", "custom_value");
    }

    @Test
    void testTokenValidationResultBuilder() {
        TokenValidationResult result = TokenValidationResult.builder()
            .valid(true)
            .subject("user-123")
            .issuer("https://provider-a.example.com")
            .expiresAt(1234567890)
            .claim("email", "user@example.com")
            .build();

        assertThat(result.isValid()).isTrue();
        assertThat(result.getSubject()).isEqualTo("user-123");
        assertThat(result.getIssuer()).isEqualTo("https://provider-a.example.com");
        assertThat(result.getExpiresAt()).isEqualTo(1234567890);
        assertThat(result.getClaims()).containsEntry("email", "user@example.com");
    }

    @Test
    void testTokenValidationResultInvalid() {
        TokenValidationResult result = TokenValidationResult.invalid("Token expired");

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("Token expired");
    }
}
