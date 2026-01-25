package org.apifocal.auth41.plugin.trustnetwork;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.models.KeycloakSession;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigBasedTrustNetworkProviderTest {

    @Mock
    private KeycloakSession session;

    private Map<String, TrustNetwork> networkCache;
    private TrustNetworkConfigLoader configLoader;
    private ConfigBasedTrustNetworkProvider provider;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        networkCache = new ConcurrentHashMap<>();
        configLoader = new TrustNetworkConfigLoader();
        provider = new ConfigBasedTrustNetworkProvider(session, networkCache, configLoader);
    }

    @Test
    void testLoadNetworkFromCache() {
        TrustNetwork network = TrustNetwork.builder()
            .networkId("test-network")
            .addProvider("hub-a", "https://hub-a.test", "hub")
            .build();

        networkCache.put("test-network", network);

        TrustNetwork loaded = provider.loadNetwork("test-network");

        assertThat(loaded).isNotNull();
        assertThat(loaded.getNetworkId()).isEqualTo("test-network");
    }

    @Test
    void testLoadNetworkNotFound() {
        TrustNetwork loaded = provider.loadNetwork("non-existent");

        assertThat(loaded).isNull();
    }

    @Test
    void testLoadNetworkWithNullId() {
        TrustNetwork loaded = provider.loadNetwork(null);

        assertThat(loaded).isNull();
    }

    @Test
    void testLoadNetworkWithEmptyId() {
        TrustNetwork loaded = provider.loadNetwork("");

        assertThat(loaded).isNull();
    }

    @Test
    void testIsMember() {
        TrustNetwork network = TrustNetwork.builder()
            .networkId("test-network")
            .addProvider("hub-a", "https://hub-a.test", "hub")
            .addProvider("provider-b", "https://provider-b.test", "spoke")
            .build();

        networkCache.put("test-network", network);

        assertThat(provider.isMember("hub-a", "test-network")).isTrue();
        assertThat(provider.isMember("provider-b", "test-network")).isTrue();
        assertThat(provider.isMember("unknown", "test-network")).isFalse();
    }

    @Test
    void testIsMemberWithNullProvider() {
        TrustNetwork network = TrustNetwork.builder()
            .networkId("test-network")
            .build();

        networkCache.put("test-network", network);

        assertThat(provider.isMember(null, "test-network")).isFalse();
    }

    @Test
    void testIsMemberWithNullNetwork() {
        assertThat(provider.isMember("hub-a", null)).isFalse();
    }

    @Test
    void testIsMemberNetworkNotFound() {
        assertThat(provider.isMember("hub-a", "non-existent")).isFalse();
    }

    @Test
    void testGetProviderMetadata() {
        TrustNetwork network = TrustNetwork.builder()
            .networkId("test-network")
            .addProvider("hub-a", "https://hub-a.test", "hub")
            .build();

        networkCache.put("test-network", network);

        ProviderNode metadata = provider.getProviderMetadata("hub-a", "test-network");

        assertThat(metadata).isNotNull();
        assertThat(metadata.getProviderId()).isEqualTo("hub-a");
        assertThat(metadata.getIssuer()).isEqualTo("https://hub-a.test");
        assertThat(metadata.getRole()).isEqualTo("hub");
    }

    @Test
    void testGetProviderMetadataNotFound() {
        TrustNetwork network = TrustNetwork.builder()
            .networkId("test-network")
            .build();

        networkCache.put("test-network", network);

        ProviderNode metadata = provider.getProviderMetadata("unknown", "test-network");

        assertThat(metadata).isNull();
    }

    @Test
    void testGetProviderMetadataWithNullProvider() {
        ProviderNode metadata = provider.getProviderMetadata(null, "test-network");

        assertThat(metadata).isNull();
    }

    @Test
    void testGetProviderMetadataWithNullNetwork() {
        ProviderNode metadata = provider.getProviderMetadata("hub-a", null);

        assertThat(metadata).isNull();
    }

    @Test
    void testGetTrustRelationships() {
        TrustNetwork network = TrustNetwork.builder()
            .networkId("test-network")
            .addProvider("hub-a", "https://hub-a.test", "hub")
            .addProvider("provider-b", "https://provider-b.test", "spoke")
            .addTrustRelationship("hub-a", "provider-b", TrustLevel.EXPLICIT)
            .addTrustRelationship("provider-b", "hub-a", TrustLevel.EXPLICIT)
            .build();

        networkCache.put("test-network", network);

        Set<TrustEdge> relationships = provider.getTrustRelationships("test-network");

        assertThat(relationships).hasSize(2);
    }

    @Test
    void testGetTrustRelationshipsNetworkNotFound() {
        Set<TrustEdge> relationships = provider.getTrustRelationships("non-existent");

        assertThat(relationships).isEmpty();
    }

    @Test
    void testGetTrustRelationshipsWithNullNetwork() {
        Set<TrustEdge> relationships = provider.getTrustRelationships(null);

        assertThat(relationships).isEmpty();
    }

    @Test
    void testHasTrustRelationship() {
        TrustNetwork network = TrustNetwork.builder()
            .networkId("test-network")
            .addProvider("hub-a", "https://hub-a.test", "hub")
            .addProvider("provider-b", "https://provider-b.test", "spoke")
            .addTrustRelationship("hub-a", "provider-b", TrustLevel.EXPLICIT)
            .build();

        networkCache.put("test-network", network);

        assertThat(provider.hasTrustRelationship("hub-a", "provider-b", "test-network")).isTrue();
        assertThat(provider.hasTrustRelationship("provider-b", "hub-a", "test-network")).isFalse();
    }

    @Test
    void testHasTrustRelationshipWithNullFromProvider() {
        assertThat(provider.hasTrustRelationship(null, "provider-b", "test-network")).isFalse();
    }

    @Test
    void testHasTrustRelationshipWithNullToProvider() {
        assertThat(provider.hasTrustRelationship("hub-a", null, "test-network")).isFalse();
    }

    @Test
    void testHasTrustRelationshipWithNullNetwork() {
        assertThat(provider.hasTrustRelationship("hub-a", "provider-b", null)).isFalse();
    }

    @Test
    void testHasTrustRelationshipNetworkNotFound() {
        assertThat(provider.hasTrustRelationship("hub-a", "provider-b", "non-existent")).isFalse();
    }

    @Test
    void testRefreshNetworkDoesNotFail() {
        // refreshNetwork is currently a no-op, just verify it doesn't throw
        provider.refreshNetwork("test-network");
        provider.refreshNetwork(null);
        provider.refreshNetwork("");
    }

    @Test
    void testClose() {
        // close should not throw
        provider.close();
    }
}
