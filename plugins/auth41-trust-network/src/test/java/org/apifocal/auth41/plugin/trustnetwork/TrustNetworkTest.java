package org.apifocal.auth41.plugin.trustnetwork;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrustNetworkTest {

    @Test
    void testBuildMinimalNetwork() {
        TrustNetwork network = TrustNetwork.builder()
            .networkId("test-network")
            .build();

        assertThat(network.getNetworkId()).isEqualTo("test-network");
        assertThat(network.getProviders()).isEmpty();
        assertThat(network.getTrustRelationships()).isEmpty();
        assertThat(network.getVersion()).isNotNull();
    }

    @Test
    void testBuildNetworkWithProviders() {
        TrustNetwork network = TrustNetwork.builder()
            .networkId("test-network")
            .addProvider("hub-a", "https://hub-a.test", "hub")
            .addProvider("provider-b", "https://provider-b.test", "spoke")
            .build();

        assertThat(network.getProviders()).hasSize(2);
        assertThat(network.isMember("hub-a")).isTrue();
        assertThat(network.isMember("provider-b")).isTrue();
        assertThat(network.isMember("unknown")).isFalse();
    }

    @Test
    void testBuildNetworkWithTrustRelationships() {
        TrustNetwork network = TrustNetwork.builder()
            .networkId("test-network")
            .addProvider("hub-a", "https://hub-a.test", "hub")
            .addProvider("provider-b", "https://provider-b.test", "spoke")
            .addTrustRelationship("hub-a", "provider-b", TrustLevel.EXPLICIT)
            .build();

        assertThat(network.getTrustRelationships()).hasSize(1);
        assertThat(network.hasTrustRelationship("hub-a", "provider-b")).isTrue();
        assertThat(network.hasTrustRelationship("provider-b", "hub-a")).isFalse();
    }

    @Test
    void testGetProvider() {
        TrustNetwork network = TrustNetwork.builder()
            .networkId("test-network")
            .addProvider("hub-a", "https://hub-a.test", "hub")
            .build();

        ProviderNode provider = network.getProvider("hub-a");
        assertThat(provider).isNotNull();
        assertThat(provider.getProviderId()).isEqualTo("hub-a");
        assertThat(provider.getIssuer()).isEqualTo("https://hub-a.test");
        assertThat(provider.getRole()).isEqualTo("hub");

        assertThat(network.getProvider("unknown")).isNull();
    }

    @Test
    void testNetworkIdRequired() {
        assertThatThrownBy(() ->
            TrustNetwork.builder().build()
        ).isInstanceOf(NullPointerException.class)
         .hasMessageContaining("networkId");
    }

    @Test
    void testProvidersMapIsUnmodifiable() {
        TrustNetwork network = TrustNetwork.builder()
            .networkId("test-network")
            .addProvider("hub-a", "https://hub-a.test", "hub")
            .build();

        assertThatThrownBy(() ->
            network.getProviders().put("new-provider", null)
        ).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void testTrustRelationshipsSetIsUnmodifiable() {
        TrustNetwork network = TrustNetwork.builder()
            .networkId("test-network")
            .addTrustRelationship("hub-a", "provider-b", TrustLevel.EXPLICIT)
            .build();

        assertThatThrownBy(() ->
            network.getTrustRelationships().add(null)
        ).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void testWithTopologyType() {
        TrustNetwork network = TrustNetwork.builder()
            .networkId("test-network")
            .topologyType("hub-and-spoke")
            .build();

        assertThat(network.getTopologyType()).isEqualTo("hub-and-spoke");
    }

    @Test
    void testWithMetadata() {
        TrustNetwork network = TrustNetwork.builder()
            .networkId("test-network")
            .metadata("operator", "Test Federation")
            .metadata("region", "US")
            .build();

        assertThat(network.getMetadata()).hasSize(2);
        assertThat(network.getMetadata().get("operator")).isEqualTo("Test Federation");
        assertThat(network.getMetadata().get("region")).isEqualTo("US");
    }
}
