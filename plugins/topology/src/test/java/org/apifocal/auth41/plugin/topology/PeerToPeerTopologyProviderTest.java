package org.apifocal.auth41.plugin.topology;

import org.apifocal.auth41.plugin.trustnetwork.TrustLevel;
import org.apifocal.auth41.plugin.trustnetwork.TrustNetwork;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.models.KeycloakSession;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;

class PeerToPeerTopologyProviderTest {

    @Mock
    private KeycloakSession session;

    private PeerToPeerTopologyProvider provider;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        provider = new PeerToPeerTopologyProvider(session);
    }

    @Test
    void testDirectTrust() {
        TrustNetwork network = TrustNetwork.builder()
            .networkId("test-network")
            .topologyType("peer-to-peer")
            .addProvider("peer-a", "https://peer-a.test", "peer")
            .addProvider("peer-b", "https://peer-b.test", "peer")
            .addTrustRelationship("peer-a", "peer-b", TrustLevel.EXPLICIT)
            .build();

        TrustPath path = provider.computeTrustPath(network, "peer-a", "peer-b");

        assertThat(path.isReachable()).isTrue();
        assertThat(path.getPath()).containsExactly("peer-a", "peer-b");
        assertThat(path.getHopCount()).isEqualTo(1);
    }

    @Test
    void testTransitiveTrust() {
        TrustNetwork network = TrustNetwork.builder()
            .networkId("test-network")
            .topologyType("peer-to-peer")
            .addProvider("peer-a", "https://peer-a.test", "peer")
            .addProvider("peer-b", "https://peer-b.test", "peer")
            .addProvider("peer-c", "https://peer-c.test", "peer")
            .addTrustRelationship("peer-a", "peer-b", TrustLevel.EXPLICIT)
            .addTrustRelationship("peer-b", "peer-c", TrustLevel.TRANSITIVE)
            .build();

        TrustPath path = provider.computeTrustPath(network, "peer-a", "peer-c");

        assertThat(path.isReachable()).isTrue();
        assertThat(path.getPath()).containsExactly("peer-a", "peer-b", "peer-c");
        assertThat(path.getHopCount()).isEqualTo(2);
    }

    @Test
    void testLongerPath() {
        TrustNetwork network = TrustNetwork.builder()
            .networkId("test-network")
            .topologyType("peer-to-peer")
            .addProvider("peer-a", "https://peer-a.test", "peer")
            .addProvider("peer-b", "https://peer-b.test", "peer")
            .addProvider("peer-c", "https://peer-c.test", "peer")
            .addProvider("peer-d", "https://peer-d.test", "peer")
            .addTrustRelationship("peer-a", "peer-b", TrustLevel.EXPLICIT)
            .addTrustRelationship("peer-b", "peer-c", TrustLevel.EXPLICIT)
            .addTrustRelationship("peer-c", "peer-d", TrustLevel.EXPLICIT)
            .build();

        TrustPath path = provider.computeTrustPath(network, "peer-a", "peer-d");

        assertThat(path.isReachable()).isTrue();
        assertThat(path.getPath()).hasSize(4);
        assertThat(path.getHopCount()).isEqualTo(3);
    }

    @Test
    void testShortestPathWithMultipleRoutes() {
        // Create a diamond-shaped network with multiple paths
        TrustNetwork network = TrustNetwork.builder()
            .networkId("test-network")
            .topologyType("peer-to-peer")
            .addProvider("peer-a", "https://peer-a.test", "peer")
            .addProvider("peer-b", "https://peer-b.test", "peer")
            .addProvider("peer-c", "https://peer-c.test", "peer")
            .addProvider("peer-d", "https://peer-d.test", "peer")
            // Direct path: a -> d
            .addTrustRelationship("peer-a", "peer-d", TrustLevel.EXPLICIT)
            // Longer paths: a -> b -> d, a -> c -> d
            .addTrustRelationship("peer-a", "peer-b", TrustLevel.EXPLICIT)
            .addTrustRelationship("peer-b", "peer-d", TrustLevel.EXPLICIT)
            .addTrustRelationship("peer-a", "peer-c", TrustLevel.EXPLICIT)
            .addTrustRelationship("peer-c", "peer-d", TrustLevel.EXPLICIT)
            .build();

        TrustPath path = provider.computeTrustPath(network, "peer-a", "peer-d");

        // BFS should find the shortest path (direct)
        assertThat(path.isReachable()).isTrue();
        assertThat(path.getHopCount()).isEqualTo(1);
        assertThat(path.getPath()).containsExactly("peer-a", "peer-d");
    }

    @Test
    void testNoPath() {
        TrustNetwork network = TrustNetwork.builder()
            .networkId("test-network")
            .topologyType("peer-to-peer")
            .addProvider("peer-a", "https://peer-a.test", "peer")
            .addProvider("peer-b", "https://peer-b.test", "peer")
            .addProvider("peer-c", "https://peer-c.test", "peer")
            .addTrustRelationship("peer-a", "peer-b", TrustLevel.EXPLICIT)
            // peer-c is isolated
            .build();

        TrustPath path = provider.computeTrustPath(network, "peer-a", "peer-c");

        assertThat(path.isReachable()).isFalse();
    }

    @Test
    void testSameProvider() {
        TrustNetwork network = TrustNetwork.builder()
            .networkId("test-network")
            .topologyType("peer-to-peer")
            .addProvider("peer-a", "https://peer-a.test", "peer")
            .build();

        TrustPath path = provider.computeTrustPath(network, "peer-a", "peer-a");

        assertThat(path.isReachable()).isTrue();
        assertThat(path.getPath()).containsExactly("peer-a");
        assertThat(path.getHopCount()).isEqualTo(0);
    }

    @Test
    void testCyclicNetwork() {
        // Create a network with cycles: a -> b -> c -> a
        TrustNetwork network = TrustNetwork.builder()
            .networkId("test-network")
            .topologyType("peer-to-peer")
            .addProvider("peer-a", "https://peer-a.test", "peer")
            .addProvider("peer-b", "https://peer-b.test", "peer")
            .addProvider("peer-c", "https://peer-c.test", "peer")
            .addTrustRelationship("peer-a", "peer-b", TrustLevel.EXPLICIT)
            .addTrustRelationship("peer-b", "peer-c", TrustLevel.EXPLICIT)
            .addTrustRelationship("peer-c", "peer-a", TrustLevel.EXPLICIT)
            .build();

        // Should still find path despite cycles
        TrustPath path = provider.computeTrustPath(network, "peer-a", "peer-c");

        assertThat(path.isReachable()).isTrue();
        assertThat(path.getPath()).containsExactly("peer-a", "peer-b", "peer-c");
    }

    @Test
    void testProviderNotInNetwork() {
        TrustNetwork network = TrustNetwork.builder()
            .networkId("test-network")
            .topologyType("peer-to-peer")
            .addProvider("peer-a", "https://peer-a.test", "peer")
            .build();

        TrustPath path = provider.computeTrustPath(network, "peer-a", "non-existent");

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
        TrustNetwork network = TrustNetwork.builder()
            .networkId("test-network")
            .topologyType("peer-to-peer")
            .addProvider("peer-a", "https://peer-a.test", "peer")
            .build();

        assertThat(provider.validateTopology(network)).isTrue();
        assertThat(provider.validateTopology(null)).isFalse();
    }

    @Test
    void testGetTopologyType() {
        assertThat(provider.getTopologyType()).isEqualTo("peer-to-peer");
    }
}
