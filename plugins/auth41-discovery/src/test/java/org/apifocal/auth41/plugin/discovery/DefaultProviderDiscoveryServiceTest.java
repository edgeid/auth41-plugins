package org.apifocal.auth41.plugin.discovery;

import org.apifocal.auth41.plugin.accounts.AccountStorageProvider;
import org.apifocal.auth41.plugin.accounts.UserAccount;
import org.apifocal.auth41.plugin.topology.TopologyProvider;
import org.apifocal.auth41.plugin.topology.TrustPath;
import org.apifocal.auth41.plugin.trustnetwork.ProviderNode;
import org.apifocal.auth41.plugin.trustnetwork.TrustNetwork;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.models.KeycloakSession;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultProviderDiscoveryServiceTest {

    @Mock
    private KeycloakSession session;

    @Mock
    private AccountStorageProvider accountStorage;

    @Mock
    private TopologyProvider topologyProvider;

    @Mock
    private TrustNetwork trustNetwork;

    private DefaultProviderDiscoveryService service;

    @BeforeEach
    void setUp() {
        lenient().when(session.getProvider(AccountStorageProvider.class)).thenReturn(accountStorage);
        lenient().when(session.getProvider(TopologyProvider.class)).thenReturn(topologyProvider);
        service = new DefaultProviderDiscoveryService(session);
    }

    @Test
    void testFindProvidersByUser() {
        UserAccount account = UserAccount.builder()
            .userIdentifier("user@example.com")
            .email("user@example.com")
            .homeProviderId("provider-a")
            .build();

        when(accountStorage.getAccount("user@example.com")).thenReturn(account);

        Set<String> providers = service.findProvidersByUser("user@example.com");

        assertThat(providers).containsExactly("provider-a");
        verify(accountStorage).getAccount("user@example.com");
    }

    @Test
    void testFindProvidersByUserNotFound() {
        when(accountStorage.getAccount("unknown@example.com")).thenReturn(null);

        Set<String> providers = service.findProvidersByUser("unknown@example.com");

        assertThat(providers).isEmpty();
    }

    @Test
    void testFindProvidersByUserNull() {
        Set<String> providers = service.findProvidersByUser(null);

        assertThat(providers).isEmpty();
        verify(accountStorage, never()).getAccount(any());
    }

    @Test
    void testFindProvidersByUserEmpty() {
        Set<String> providers = service.findProvidersByUser("");

        assertThat(providers).isEmpty();
        verify(accountStorage, never()).getAccount(any());
    }

    @Test
    void testFindProvidersByUserCached() {
        UserAccount account = UserAccount.builder()
            .userIdentifier("user@example.com")
            .email("user@example.com")
            .homeProviderId("provider-a")
            .build();

        when(accountStorage.getAccount("user@example.com")).thenReturn(account);

        // First call - should query storage
        Set<String> providers1 = service.findProvidersByUser("user@example.com");
        assertThat(providers1).containsExactly("provider-a");

        // Second call - should use cache
        Set<String> providers2 = service.findProvidersByUser("user@example.com");
        assertThat(providers2).containsExactly("provider-a");

        // Verify storage was only called once
        verify(accountStorage, times(1)).getAccount("user@example.com");
    }

    @Test
    void testFindProvidersByEmail() {
        UserAccount account = UserAccount.builder()
            .userIdentifier("user@example.com")
            .email("user@example.com")
            .homeProviderId("provider-b")
            .build();

        when(accountStorage.getAccountByEmail("user@example.com")).thenReturn(account);

        Set<String> providers = service.findProvidersByEmail("user@example.com");

        assertThat(providers).containsExactly("provider-b");
        verify(accountStorage).getAccountByEmail("user@example.com");
    }

    @Test
    void testFindProvidersByEmailNotFound() {
        when(accountStorage.getAccountByEmail("unknown@example.com")).thenReturn(null);

        Set<String> providers = service.findProvidersByEmail("unknown@example.com");

        assertThat(providers).isEmpty();
    }

    @Test
    void testFindProvidersByEmailNull() {
        Set<String> providers = service.findProvidersByEmail(null);

        assertThat(providers).isEmpty();
        verify(accountStorage, never()).getAccountByEmail(any());
    }

    @Test
    void testFindProvidersByEmailCached() {
        UserAccount account = UserAccount.builder()
            .userIdentifier("user@example.com")
            .email("user@example.com")
            .homeProviderId("provider-b")
            .build();

        when(accountStorage.getAccountByEmail("user@example.com")).thenReturn(account);

        // First call
        service.findProvidersByEmail("user@example.com");
        // Second call - should use cache
        service.findProvidersByEmail("user@example.com");

        verify(accountStorage, times(1)).getAccountByEmail("user@example.com");
    }

    @Test
    void testFindCibaHomeProvider() {
        // Setup user account
        UserAccount account = UserAccount.builder()
            .userIdentifier("user@example.com")
            .email("user@example.com")
            .homeProviderId("provider-a")
            .build();

        when(accountStorage.getAccount("user@example.com")).thenReturn(account);

        // Setup provider with CIBA support
        ProviderNode provider = ProviderNode.builder()
            .providerId("provider-a")
            .issuer("https://provider-a.example.com")
            .attribute("ciba_supported", "true")
            .build();

        Map<String, ProviderNode> providers = new HashMap<>();
        providers.put("provider-a", provider);
        when(trustNetwork.getProviders()).thenReturn(providers);

        // Setup valid trust path (direct connection, 1 hop)
        TrustPath path = TrustPath.builder()
            .sourceProvider("provider-b")
            .targetProvider("provider-a")
            .path(List.of("provider-b", "provider-a"))
            .reachable(true)
            .build();
        when(topologyProvider.computeTrustPath(trustNetwork, "provider-b", "provider-a")).thenReturn(path);

        String cibaProvider = service.findCibaHomeProvider("user@example.com", "provider-b", trustNetwork);

        assertThat(cibaProvider).isEqualTo("provider-a");
        verify(topologyProvider).computeTrustPath(trustNetwork, "provider-b", "provider-a");
    }

    @Test
    void testFindCibaHomeProviderNoCibaSupport() {
        // Setup user account
        UserAccount account = UserAccount.builder()
            .userIdentifier("user@example.com")
            .email("user@example.com")
            .homeProviderId("provider-a")
            .build();

        when(accountStorage.getAccount("user@example.com")).thenReturn(account);

        // Setup provider WITHOUT CIBA support
        ProviderNode provider = ProviderNode.builder()
            .providerId("provider-a")
            .issuer("https://provider-a.example.com")
            .attribute("ciba_supported", "false")
            .build();

        Map<String, ProviderNode> providers = new HashMap<>();
        providers.put("provider-a", provider);
        when(trustNetwork.getProviders()).thenReturn(providers);

        String cibaProvider = service.findCibaHomeProvider("user@example.com", "provider-b", trustNetwork);

        assertThat(cibaProvider).isNull();
        verify(topologyProvider, never()).computeTrustPath(any(), any(), any());
    }

    @Test
    void testFindCibaHomeProviderNoValidPath() {
        // Setup user account
        UserAccount account = UserAccount.builder()
            .userIdentifier("user@example.com")
            .email("user@example.com")
            .homeProviderId("provider-a")
            .build();

        when(accountStorage.getAccount("user@example.com")).thenReturn(account);

        // Setup provider with CIBA support
        ProviderNode provider = ProviderNode.builder()
            .providerId("provider-a")
            .issuer("https://provider-a.example.com")
            .attribute("ciba_supported", "true")
            .build();

        Map<String, ProviderNode> providers = new HashMap<>();
        providers.put("provider-a", provider);
        when(trustNetwork.getProviders()).thenReturn(providers);

        // No valid trust path (unreachable)
        TrustPath path = TrustPath.builder()
            .sourceProvider("provider-b")
            .targetProvider("provider-a")
            .path(List.of())
            .reachable(false)
            .build();
        when(topologyProvider.computeTrustPath(trustNetwork, "provider-b", "provider-a")).thenReturn(path);

        String cibaProvider = service.findCibaHomeProvider("user@example.com", "provider-b", trustNetwork);

        assertThat(cibaProvider).isNull();
    }

    @Test
    void testFindCibaHomeProviderUserNotFound() {
        when(accountStorage.getAccount("unknown@example.com")).thenReturn(null);

        String cibaProvider = service.findCibaHomeProvider("unknown@example.com", "provider-b", trustNetwork);

        assertThat(cibaProvider).isNull();
        verify(topologyProvider, never()).computeTrustPath(any(), any(), any());
    }

    @Test
    void testFindCibaHomeProviderNullParams() {
        assertThat(service.findCibaHomeProvider(null, "provider-b", trustNetwork)).isNull();
        assertThat(service.findCibaHomeProvider("user@example.com", null, trustNetwork)).isNull();
        assertThat(service.findCibaHomeProvider("user@example.com", "provider-b", null)).isNull();
    }

    @Test
    void testFindCibaHomeProviderStringCibaSupport() {
        // Test various string representations of CIBA support
        testCibaMetadataValue("true", true);
        testCibaMetadataValue("TRUE", true);
        testCibaMetadataValue("yes", true);
        testCibaMetadataValue("YES", true);
        testCibaMetadataValue("1", true);
        testCibaMetadataValue("false", false);
        testCibaMetadataValue("no", false);
        testCibaMetadataValue("0", false);
    }

    private void testCibaMetadataValue(String value, boolean shouldSucceed) {
        // Clear cache before each test
        service.clearAllCache();

        UserAccount account = UserAccount.builder()
            .userIdentifier("user@example.com")
            .email("user@example.com")
            .homeProviderId("provider-a")
            .build();

        when(accountStorage.getAccount("user@example.com")).thenReturn(account);

        ProviderNode provider = ProviderNode.builder()
            .providerId("provider-a")
            .issuer("https://provider-a.example.com")
            .attribute("ciba_supported", value)
            .build();

        Map<String, ProviderNode> providers = new HashMap<>();
        providers.put("provider-a", provider);
        when(trustNetwork.getProviders()).thenReturn(providers);

        TrustPath path = TrustPath.builder()
            .sourceProvider("provider-b")
            .targetProvider("provider-a")
            .path(List.of("provider-b", "provider-a"))
            .reachable(true)
            .build();
        // Use lenient since path validation is only called when CIBA is supported
        lenient().when(topologyProvider.computeTrustPath(trustNetwork, "provider-b", "provider-a")).thenReturn(path);

        String cibaProvider = service.findCibaHomeProvider("user@example.com", "provider-b", trustNetwork);

        if (shouldSucceed) {
            assertThat(cibaProvider).isEqualTo("provider-a");
        } else {
            assertThat(cibaProvider).isNull();
        }
    }

    @Test
    void testCacheAssociation() {
        service.cacheAssociation("user@example.com", "provider-a", Duration.ofMinutes(5));

        // First call should use cache
        Set<String> providers = service.findProvidersByUser("user@example.com");
        assertThat(providers).containsExactly("provider-a");

        // Should not have called storage (cache hit)
        verify(accountStorage, never()).getAccount(any());
    }

    @Test
    void testCacheAssociationNullParams() {
        // Should not throw exceptions
        service.cacheAssociation(null, "provider-a", Duration.ofMinutes(5));
        service.cacheAssociation("user@example.com", null, Duration.ofMinutes(5));
        service.cacheAssociation("user@example.com", "provider-a", null);
    }

    @Test
    void testClearCache() {
        // Cache an association
        service.cacheAssociation("user@example.com", "provider-a", Duration.ofMinutes(5));

        // Clear it
        service.clearCache("user@example.com");

        // Now should query storage
        UserAccount account = UserAccount.builder()
            .userIdentifier("user@example.com")
            .email("user@example.com")
            .homeProviderId("provider-a")
            .build();

        when(accountStorage.getAccount("user@example.com")).thenReturn(account);

        service.findProvidersByUser("user@example.com");

        verify(accountStorage).getAccount("user@example.com");
    }

    @Test
    void testClearCacheNull() {
        // Should not throw exception
        service.clearCache(null);
    }

    @Test
    void testClearAllCache() {
        // Cache multiple associations
        service.cacheAssociation("user1@example.com", "provider-a", Duration.ofMinutes(5));
        service.cacheAssociation("user2@example.com", "provider-b", Duration.ofMinutes(5));

        // Clear all
        service.clearAllCache();

        // Both should now query storage
        UserAccount account1 = UserAccount.builder()
            .userIdentifier("user1@example.com")
            .email("user1@example.com")
            .homeProviderId("provider-a")
            .build();

        UserAccount account2 = UserAccount.builder()
            .userIdentifier("user2@example.com")
            .email("user2@example.com")
            .homeProviderId("provider-b")
            .build();

        when(accountStorage.getAccount("user1@example.com")).thenReturn(account1);
        when(accountStorage.getAccount("user2@example.com")).thenReturn(account2);

        service.findProvidersByUser("user1@example.com");
        service.findProvidersByUser("user2@example.com");

        verify(accountStorage).getAccount("user1@example.com");
        verify(accountStorage).getAccount("user2@example.com");
    }

    @Test
    void testAccountStorageProviderNotAvailable() {
        when(session.getProvider(AccountStorageProvider.class)).thenReturn(null);

        DefaultProviderDiscoveryService serviceWithoutStorage = new DefaultProviderDiscoveryService(session);

        Set<String> providers = serviceWithoutStorage.findProvidersByUser("user@example.com");

        assertThat(providers).isEmpty();
    }

    @Test
    void testTopologyProviderNotAvailable() {
        when(session.getProvider(TopologyProvider.class)).thenReturn(null);

        DefaultProviderDiscoveryService serviceWithoutTopology = new DefaultProviderDiscoveryService(session);

        UserAccount account = UserAccount.builder()
            .userIdentifier("user@example.com")
            .email("user@example.com")
            .homeProviderId("provider-a")
            .build();

        when(accountStorage.getAccount("user@example.com")).thenReturn(account);

        String cibaProvider = serviceWithoutTopology.findCibaHomeProvider("user@example.com", "provider-b", trustNetwork);

        assertThat(cibaProvider).isNull();
    }

    @Test
    void testProviderNotInTrustNetwork() {
        UserAccount account = UserAccount.builder()
            .userIdentifier("user@example.com")
            .email("user@example.com")
            .homeProviderId("provider-unknown")
            .build();

        when(accountStorage.getAccount("user@example.com")).thenReturn(account);
        when(trustNetwork.getProviders()).thenReturn(new HashMap<>());

        String cibaProvider = service.findCibaHomeProvider("user@example.com", "provider-b", trustNetwork);

        assertThat(cibaProvider).isNull();
    }
}
