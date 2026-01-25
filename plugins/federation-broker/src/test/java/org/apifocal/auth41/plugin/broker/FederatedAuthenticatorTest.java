package org.apifocal.auth41.plugin.broker;

import org.apifocal.auth41.plugin.discovery.ProviderDiscoveryService;
import org.apifocal.auth41.plugin.topology.TopologyProvider;
import org.apifocal.auth41.plugin.topology.TrustPath;
import org.apifocal.auth41.plugin.trustnetwork.ProviderMetadata;
import org.apifocal.auth41.plugin.trustnetwork.ProviderNode;
import org.apifocal.auth41.plugin.trustnetwork.TrustNetwork;
import org.apifocal.auth41.plugin.trustnetwork.TrustNetworkProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.http.HttpRequest;
import org.keycloak.models.*;
import org.keycloak.sessions.AuthenticationSessionModel;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FederatedAuthenticatorTest {

    private FederatedAuthenticator authenticator;
    private AuthenticationFlowContext context;
    private KeycloakSession session;
    private RealmModel realm;
    private HttpRequest httpRequest;
    private UriInfo uriInfo;
    private AuthenticationSessionModel authSession;
    private ClientModel client;

    // Service providers
    private ProviderDiscoveryService discovery;
    private TrustNetworkProvider trustProvider;
    private TopologyProvider topology;
    private FederationBrokerProvider broker;
    private UserProvider userProvider;

    // Test data
    private TrustNetwork trustNetwork;
    private ProviderNode homeProvider;

    @BeforeEach
    void setUp() {
        authenticator = new FederatedAuthenticator();

        // Mock context and dependencies
        context = mock(AuthenticationFlowContext.class);
        session = mock(KeycloakSession.class);
        realm = mock(RealmModel.class);
        httpRequest = mock(HttpRequest.class);
        uriInfo = mock(UriInfo.class);
        authSession = mock(AuthenticationSessionModel.class);
        client = mock(ClientModel.class);

        // Mock service providers
        discovery = mock(ProviderDiscoveryService.class);
        trustProvider = mock(TrustNetworkProvider.class);
        topology = mock(TopologyProvider.class);
        broker = mock(FederationBrokerProvider.class);
        userProvider = mock(UserProvider.class);

        // Setup context
        when(context.getSession()).thenReturn(session);
        when(context.getRealm()).thenReturn(realm);
        when(context.getHttpRequest()).thenReturn(httpRequest);
        when(context.getUriInfo()).thenReturn(uriInfo);
        when(context.getAuthenticationSession()).thenReturn(authSession);

        // Setup session providers
        when(session.getProvider(ProviderDiscoveryService.class)).thenReturn(discovery);
        when(session.getProvider(TrustNetworkProvider.class)).thenReturn(trustProvider);
        when(session.getProvider(TopologyProvider.class)).thenReturn(topology);
        when(session.getProvider(FederationBrokerProvider.class)).thenReturn(broker);
        when(session.users()).thenReturn(userProvider);

        // Setup realm
        when(realm.getName()).thenReturn("hub-a");

        // Setup auth session
        when(authSession.getClient()).thenReturn(client);
        when(client.getClientId()).thenReturn("test-client");

        // Setup URI info
        when(uriInfo.getBaseUri()).thenReturn(URI.create("https://hub-a.example.com/"));

        // Setup test data
        setupTestData();
    }

    private void setupTestData() {
        // Create home provider
        homeProvider = ProviderNode.builder()
            .providerId("provider-a")
            .issuer("https://provider-a.example.com")
            .role("spoke")
            .metadata(ProviderMetadata.builder()
                .authorizationEndpoint("https://provider-a.example.com/auth")
                .tokenEndpoint("https://provider-a.example.com/token")
                .jwksUri("https://provider-a.example.com/jwks")
                .build())
            .build();

        // Create trust network
        trustNetwork = TrustNetwork.builder()
            .networkId("test-network")
            .topologyType("hub-and-spoke")
            .addProvider(homeProvider)
            .build();

        when(trustProvider.loadNetwork("default")).thenReturn(trustNetwork);
    }

    @Test
    void testAuthenticateWithLoginHint() throws FederationException {
        // Arrange
        String loginHint = "user@example.com";
        MultivaluedMap<String, String> queryParams = new MultivaluedHashMap<>();
        queryParams.putSingle("login_hint", loginHint);

        when(httpRequest.getUri()).thenReturn(uriInfo);
        when(uriInfo.getQueryParameters()).thenReturn(queryParams);

        when(discovery.findProvidersByUser(loginHint)).thenReturn(Set.of("provider-a"));

        TrustPath trustPath = TrustPath.builder()
            .sourceProvider("hub-a")
            .targetProvider("provider-a")
            .path(Arrays.asList("hub-a", "provider-a"))
            .reachable(true)
            .build();
        when(topology.computeTrustPath(any(), eq("hub-a"), eq("provider-a"))).thenReturn(trustPath);

        String authUrl = "https://provider-a.example.com/auth?response_type=code&client_id=test-client";
        when(broker.initiateAuthenticationRequest(any(FederationRequest.class), eq(trustNetwork)))
            .thenReturn(authUrl);

        when(context.generateAccessCode()).thenReturn("state123", "nonce456");

        // Act
        authenticator.authenticate(context);

        // Assert
        verify(discovery).findProvidersByUser(loginHint);
        verify(topology).computeTrustPath(trustNetwork, "hub-a", "provider-a");
        verify(broker).initiateAuthenticationRequest(any(FederationRequest.class), eq(trustNetwork));
        verify(context).challenge(any(Response.class));
        verify(authSession).setAuthNote(eq("federation_state"), anyString());
        verify(authSession).setAuthNote("home_provider_id", "provider-a");
    }

    @Test
    void testAuthenticateWithoutLoginHint() {
        // Arrange
        MultivaluedMap<String, String> queryParams = new MultivaluedHashMap<>();
        MultivaluedMap<String, String> formParams = new MultivaluedHashMap<>();

        when(httpRequest.getUri()).thenReturn(uriInfo);
        when(uriInfo.getQueryParameters()).thenReturn(queryParams);
        when(httpRequest.getDecodedFormParameters()).thenReturn(formParams);

        // Mock the form builder chain
        org.keycloak.forms.login.LoginFormsProvider formsProvider =
            mock(org.keycloak.forms.login.LoginFormsProvider.class);
        when(context.form()).thenReturn(formsProvider);
        when(formsProvider.setAttribute(anyString(), any())).thenReturn(formsProvider);
        when(formsProvider.createForm(anyString())).thenReturn(Response.ok().build());

        // Act
        authenticator.authenticate(context);

        // Assert
        verify(context).challenge(any(Response.class)); // Account chooser shown
        verify(discovery, never()).findProvidersByUser(anyString());
    }

    @Test
    void testAuthenticateUserNotFound() {
        // Arrange
        String loginHint = "unknown@example.com";
        MultivaluedMap<String, String> queryParams = new MultivaluedHashMap<>();
        queryParams.putSingle("login_hint", loginHint);

        when(httpRequest.getUri()).thenReturn(uriInfo);
        when(uriInfo.getQueryParameters()).thenReturn(queryParams);

        when(discovery.findProvidersByUser(loginHint)).thenReturn(Collections.emptySet());

        // Act
        authenticator.authenticate(context);

        // Assert
        verify(context).failure(AuthenticationFlowError.UNKNOWN_USER);
    }

    @Test
    void testAuthenticateNoTrustPath() {
        // Arrange
        String loginHint = "user@example.com";
        MultivaluedMap<String, String> queryParams = new MultivaluedHashMap<>();
        queryParams.putSingle("login_hint", loginHint);

        when(httpRequest.getUri()).thenReturn(uriInfo);
        when(uriInfo.getQueryParameters()).thenReturn(queryParams);

        when(discovery.findProvidersByUser(loginHint)).thenReturn(Set.of("provider-a"));

        TrustPath noPath = TrustPath.builder()
            .sourceProvider("hub-a")
            .targetProvider("provider-a")
            .reachable(false)
            .build();
        when(topology.computeTrustPath(any(), eq("hub-a"), eq("provider-a"))).thenReturn(noPath);

        // Act
        authenticator.authenticate(context);

        // Assert
        verify(context).failure(AuthenticationFlowError.INVALID_USER);
    }

    @Test
    void testActionSuccessfulCallback() throws FederationException {
        // Arrange
        String code = "auth_code_123";
        String state = "state_123";

        MultivaluedMap<String, String> queryParams = new MultivaluedHashMap<>();
        queryParams.putSingle("code", code);
        queryParams.putSingle("state", state);

        when(httpRequest.getUri()).thenReturn(uriInfo);
        when(uriInfo.getQueryParameters()).thenReturn(queryParams);
        when(uriInfo.getRequestUri()).thenReturn(URI.create("https://hub-a.example.com/realms/hub-a/broker/auth41-federated/endpoint"));

        when(authSession.getAuthNote("federation_state")).thenReturn(state);
        when(authSession.getAuthNote("home_provider_id")).thenReturn("provider-a");
        when(authSession.getAuthNote("federation_redirect_uri")).thenReturn("https://hub-a.example.com/realms/hub-a/broker/auth41-federated/endpoint");

        TokenSet tokens = TokenSet.builder()
            .accessToken("access_token")
            .idToken("eyJhbGciOiJub25lIn0.eyJpc3MiOiJodHRwczovL3Byb3ZpZGVyLWEuZXhhbXBsZS5jb20iLCJzdWIiOiJ1c2VyMTIzIiwiZW1haWwiOiJ1c2VyQGV4YW1wbGUuY29tIiwiZXhwIjo5OTk5OTk5OTk5fQ.")
            .tokenType("Bearer")
            .expiresIn(3600)
            .build();
        when(broker.exchangeCodeForToken(code, "provider-a", trustNetwork)).thenReturn(tokens);

        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", "https://provider-a.example.com");
        claims.put("sub", "user123");
        claims.put("email", "user@example.com");
        claims.put("exp", 9999999999L);

        TokenValidationResult validation = TokenValidationResult.builder()
            .valid(true)
            .subject("user123")
            .issuer("https://provider-a.example.com")
            .claims(claims)
            .build();
        when(broker.validateToken(anyString(), eq("provider-a"), eq(trustNetwork))).thenReturn(validation);

        UserModel user = mock(UserModel.class);
        when(user.getUsername()).thenReturn("provider-a:user123");
        when(userProvider.getUserByUsername(realm, "provider-a:user123")).thenReturn(null);
        when(userProvider.addUser(realm, "provider-a:user123")).thenReturn(user);

        // Act
        authenticator.action(context);

        // Assert
        verify(broker).exchangeCodeForToken(code, "provider-a", trustNetwork);
        verify(broker).validateToken(anyString(), eq("provider-a"), eq(trustNetwork));
        verify(context).setUser(user);
        verify(context).success();
    }

    @Test
    void testActionErrorResponse() {
        // Arrange
        MultivaluedMap<String, String> queryParams = new MultivaluedHashMap<>();
        queryParams.putSingle("error", "access_denied");

        when(httpRequest.getUri()).thenReturn(uriInfo);
        when(uriInfo.getQueryParameters()).thenReturn(queryParams);

        // Act
        authenticator.action(context);

        // Assert
        verify(context).failure(AuthenticationFlowError.IDENTITY_PROVIDER_ERROR);
    }

    @Test
    void testActionStateMismatch() {
        // Arrange
        MultivaluedMap<String, String> queryParams = new MultivaluedHashMap<>();
        queryParams.putSingle("code", "code123");
        queryParams.putSingle("state", "wrong_state");

        when(httpRequest.getUri()).thenReturn(uriInfo);
        when(uriInfo.getQueryParameters()).thenReturn(queryParams);

        when(authSession.getAuthNote("federation_state")).thenReturn("expected_state");

        // Act
        authenticator.action(context);

        // Assert
        verify(context).failure(AuthenticationFlowError.INVALID_CREDENTIALS);
    }

    @Test
    void testActionRedirectUriMismatch() throws FederationException {
        // Arrange
        String code = "auth_code_123";
        String state = "state_123";

        MultivaluedMap<String, String> queryParams = new MultivaluedHashMap<>();
        queryParams.putSingle("code", code);
        queryParams.putSingle("state", state);

        when(httpRequest.getUri()).thenReturn(uriInfo);
        when(uriInfo.getQueryParameters()).thenReturn(queryParams);
        when(uriInfo.getRequestUri()).thenReturn(URI.create("https://hub-a.example.com/realms/hub-a/broker/auth41-federated/endpoint?code=auth_code_123&state=state_123"));

        when(authSession.getAuthNote("federation_state")).thenReturn(state);
        when(authSession.getAuthNote("federation_redirect_uri")).thenReturn("https://different-uri.example.com/callback");

        // Act
        authenticator.action(context);

        // Assert
        verify(context).failure(AuthenticationFlowError.INVALID_CREDENTIALS);
        verify(broker, never()).exchangeCodeForToken(anyString(), anyString(), any());
    }

    @Test
    void testActionInvalidToken() throws FederationException {
        // Arrange
        String code = "auth_code_123";
        String state = "state_123";

        MultivaluedMap<String, String> queryParams = new MultivaluedHashMap<>();
        queryParams.putSingle("code", code);
        queryParams.putSingle("state", state);

        when(httpRequest.getUri()).thenReturn(uriInfo);
        when(uriInfo.getQueryParameters()).thenReturn(queryParams);
        when(uriInfo.getRequestUri()).thenReturn(URI.create("https://hub-a.example.com/realms/hub-a/broker/auth41-federated/endpoint"));

        when(authSession.getAuthNote("federation_state")).thenReturn(state);
        when(authSession.getAuthNote("home_provider_id")).thenReturn("provider-a");
        when(authSession.getAuthNote("federation_redirect_uri")).thenReturn("https://hub-a.example.com/realms/hub-a/broker/auth41-federated/endpoint");

        TokenSet tokens = TokenSet.builder()
            .accessToken("access_token")
            .idToken("invalid_token")
            .tokenType("Bearer")
            .expiresIn(3600)
            .build();
        when(broker.exchangeCodeForToken(code, "provider-a", trustNetwork)).thenReturn(tokens);

        TokenValidationResult validation = TokenValidationResult.invalid("Token expired");
        when(broker.validateToken(anyString(), eq("provider-a"), eq(trustNetwork))).thenReturn(validation);

        // Act
        authenticator.action(context);

        // Assert
        verify(context).failure(AuthenticationFlowError.INVALID_CREDENTIALS);
    }

    @Test
    void testRequiresUser() {
        assertFalse(authenticator.requiresUser());
    }

    @Test
    void testConfiguredFor() {
        UserModel user = mock(UserModel.class);
        assertTrue(authenticator.configuredFor(session, realm, user));
    }

    @Test
    void testSetRequiredActions() {
        UserModel user = mock(UserModel.class);
        // Should not throw exception
        authenticator.setRequiredActions(session, realm, user);
    }
}
