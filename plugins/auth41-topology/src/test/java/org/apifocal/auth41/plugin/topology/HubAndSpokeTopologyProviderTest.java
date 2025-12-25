package org.apifocal.auth41.plugin.topology;

import org.apifocal.auth41.plugin.trustnetwork.TrustLevel;
import org.apifocal.auth41.plugin.trustnetwork.TrustNetwork;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.models.KeycloakSession;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;

class HubAndSpokeTopologyProviderTest {

    @Mock
    private KeycloakSession session;

    private HubAndSpokeTopologyProvider provider;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        provider = new HubAndSpokeTopologyProvider(session);
    }

    @Test
    void testDirectSpokeToHub() {
        TrustNetwork network = TrustNetwork.builder()
            .networkId("test-network")
            .topologyType("hub-and-spoke")
            .addProvider("hub-a", "https://hub-a.test", "hub")
            .addProvider("provider-b", "https://provider-b.test", "spoke")
            .addTrustRelationship("provider-b", "hub-a", TrustLevel.EXPLICIT)
            .build();

        TrustPath path = provider.computeTrustPath(network, "provider-b", "hub-a");

        assertThat(path.isReachable()).isTrue();
        assertThat(path.getPath()).containsExactly("provider-b", "hub-a");
        assertThat(path.getHopCount()).isEqualTo(1);
    }

    @Test
    void testDirectHubToSpoke() {
        TrustNetwork network = TrustNetwork.builder()
            .networkId("test-network")
            .topologyType("hub-and-spoke")
            .addProvider("hub-a", "https://hub-a.test", "hub")
            .addProvider("provider-b", "https://provider-b.test", "spoke")
            .addTrustRelationship("hub-a", "provider-b", TrustLevel.EXPLICIT)
            .build();

        TrustPath path = provider.computeTrustPath(network, "hub-a", "provider-b");

        assertThat(path.isReachable()).isTrue();
        assertThat(path.getPath()).containsExactly("hub-a", "provider-b");
        assertThat(path.getHopCount()).isEqualTo(1);
    }

    @Test
    void testSpokeToSpokeThroughHub() {
        TrustNetwork network = TrustNetwork.builder()
            .networkId("test-network")
            .topologyType("hub-and-spoke")
            .addProvider("hub-a", "https://hub-a.test", "hub")
            .addProvider("provider-b", "https://provider-b.test", "spoke")
            .addProvider("provider-c", "https://provider-c.test", "spoke")
            .addTrustRelationship("provider-b", "hub-a", TrustLevel.EXPLICIT)
            .addTrustRelationship("hub-a", "provider-c", TrustLevel.EXPLICIT)
            .build();

        TrustPath path = provider.computeTrustPath(network, "provider-b", "provider-c");

        assertThat(path.isReachable()).isTrue();
        assertThat(path.getPath()).containsExactly("provider-b", "hub-a", "provider-c");
        assertThat(path.getHopCount()).isEqualTo(2);
    }

    @Test
    void testSpokeToSpokeNoCommonHub() {
        TrustNetwork network = TrustNetwork.builder()
            .networkId("test-network")
            .topologyType("hub-and-spoke")
            .addProvider("hub-a", "https://hub-a.test", "hub")
            .addProvider("hub-b", "https://hub-b.test", "hub")
            .addProvider("provider-c", "https://provider-c.test", "spoke")
            .addProvider("provider-d", "https://provider-d.test", "spoke")
            .addTrustRelationship("provider-c", "hub-a", TrustLevel.EXPLICIT)
            .addTrustRelationship("hub-b", "provider-d", TrustLevel.EXPLICIT)
            .build();

        TrustPath path = provider.computeTrustPath(network, "provider-c", "provider-d");

        assertThat(path.isReachable()).isFalse();
    }

    @Test
    void testSameProvider() {
        TrustNetwork network = TrustNetwork.builder()
            .networkId("test-network")
            .topologyType("hub-and-spoke")
            .addProvider("provider-a", "https://provider-a.test", "spoke")
            .build();

        TrustPath path = provider.computeTrustPath(network, "provider-a", "provider-a");

        assertThat(path.isReachable()).isTrue();
        assertThat(path.getPath()).containsExactly("provider-a");
        assertThat(path.getHopCount()).isEqualTo(0);
    }

    @Test
    void testProviderNotInNetwork() {
        TrustNetwork network = TrustNetwork.builder()
            .networkId("test-network")
            .topologyType("hub-and-spoke")
            .addProvider("hub-a", "https://hub-a.test", "hub")
            .build();

        TrustPath path = provider.computeTrustPath(network, "hub-a", "non-existent");

        assertThat(path.isReachable()).isFalse();
    }

    @Test
    void testNullInputs() {
        TrustPath path1 = provider.computeTrustPath(null, "a", "b");
        assertThat(path1.isReachable()).isFalse();

        TrustNetwork network = TrustNetwork.builder()
            .networkId("test")
            .build();

        TrustPath path2 = provider.computeTrustPath(network, null, "b");
        assertThat(path2.isReachable()).isFalse();

        TrustPath path3 = provider.computeTrustPath(network, "a", null);
        assertThat(path3.isReachable()).isFalse();
    }

    @Test
    void testValidateTopology() {
        TrustNetwork validNetwork = TrustNetwork.builder()
            .networkId("test-network")
            .topologyType("hub-and-spoke")
            .addProvider("hub-a", "https://hub-a.test", "hub")
            .build();

        assertThat(provider.validateTopology(validNetwork)).isTrue();

        TrustNetwork noHubNetwork = TrustNetwork.builder()
            .networkId("test-network")
            .topologyType("hub-and-spoke")
            .addProvider("provider-a", "https://provider-a.test", "spoke")
            .build();

        assertThat(provider.validateTopology(noHubNetwork)).isFalse();

        assertThat(provider.validateTopology(null)).isFalse();
    }

    @Test
    void testGetTopologyType() {
        assertThat(provider.getTopologyType()).isEqualTo("hub-and-spoke");
    }
}
