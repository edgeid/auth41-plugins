package org.apifocal.auth41.plugin.trustnetwork;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrustNetworkConfigLoaderTest {

    private TrustNetworkConfigLoader loader;

    @BeforeEach
    void setUp() {
        loader = new TrustNetworkConfigLoader();
    }

    @Test
    void testLoadFromJsonResource() throws IOException {
        TrustNetwork network = loader.loadFromResource("test-network.json");

        assertThat(network).isNotNull();
        assertThat(network.getNetworkId()).isEqualTo("test-federation");
        assertThat(network.getTopologyType()).isEqualTo("hub-and-spoke");
        assertThat(network.getProviders()).hasSize(3);
        assertThat(network.getTrustRelationships()).hasSize(4);
    }

    @Test
    void testLoadFromJsonResourceNotFound() {
        assertThatThrownBy(() ->
            loader.loadFromResource("non-existent.json")
        ).isInstanceOf(IOException.class)
         .hasMessageContaining("not found");
    }

    @Test
    void testLoadFromJsonString() throws IOException {
        String json = """
            {
              "network_id": "test-network",
              "topology_type": "peer-to-peer",
              "providers": {
                "provider-a": {
                  "issuer": "https://provider-a.test",
                  "role": "peer"
                },
                "provider-b": {
                  "issuer": "https://provider-b.test",
                  "role": "peer"
                }
              },
              "trust_relationships": [
                {
                  "from": "provider-a",
                  "to": "provider-b",
                  "level": "explicit"
                }
              ]
            }
            """;

        TrustNetwork network = loader.loadFromJsonString(json);

        assertThat(network.getNetworkId()).isEqualTo("test-network");
        assertThat(network.getTopologyType()).isEqualTo("peer-to-peer");
        assertThat(network.getProviders()).hasSize(2);
        assertThat(network.getTrustRelationships()).hasSize(1);
    }

    @Test
    void testLoadProvidersFromJson() throws IOException {
        String json = """
            {
              "network_id": "test-network",
              "providers": {
                "hub-a": {
                  "issuer": "https://hub-a.test",
                  "role": "hub"
                },
                "provider-b": {
                  "issuer": "https://provider-b.test",
                  "role": "spoke"
                }
              },
              "trust_relationships": []
            }
            """;

        TrustNetwork network = loader.loadFromJsonString(json);

        assertThat(network.getProviders()).hasSize(2);

        ProviderNode hubA = network.getProvider("hub-a");
        assertThat(hubA).isNotNull();
        assertThat(hubA.getIssuer()).isEqualTo("https://hub-a.test");
        assertThat(hubA.getRole()).isEqualTo("hub");

        ProviderNode providerB = network.getProvider("provider-b");
        assertThat(providerB).isNotNull();
        assertThat(providerB.getIssuer()).isEqualTo("https://provider-b.test");
        assertThat(providerB.getRole()).isEqualTo("spoke");
    }

    @Test
    void testLoadTrustRelationshipsFromJson() throws IOException {
        String json = """
            {
              "network_id": "test-network",
              "providers": {},
              "trust_relationships": [
                {
                  "from": "hub-a",
                  "to": "provider-b",
                  "level": "explicit"
                },
                {
                  "from": "provider-b",
                  "to": "provider-c",
                  "level": "transitive"
                }
              ]
            }
            """;

        TrustNetwork network = loader.loadFromJsonString(json);

        assertThat(network.getTrustRelationships()).hasSize(2);
        assertThat(network.hasTrustRelationship("hub-a", "provider-b")).isTrue();
        assertThat(network.hasTrustRelationship("provider-b", "provider-c")).isTrue();
    }

    @Test
    void testLoadWithVersion() throws IOException {
        String json = """
            {
              "network_id": "test-network",
              "registry_version": "2024-12-24T10:00:00Z",
              "providers": {},
              "trust_relationships": []
            }
            """;

        TrustNetwork network = loader.loadFromJsonString(json);

        assertThat(network.getVersion()).isNotNull();
        assertThat(network.getVersion().toString()).contains("2024-12-24");
    }

    @Test
    void testLoadMinimalJson() throws IOException {
        String json = """
            {
              "network_id": "minimal-network",
              "providers": {},
              "trust_relationships": []
            }
            """;

        TrustNetwork network = loader.loadFromJsonString(json);

        assertThat(network.getNetworkId()).isEqualTo("minimal-network");
        assertThat(network.getTopologyType()).isEqualTo("hub-and-spoke"); // default
        assertThat(network.getProviders()).isEmpty();
        assertThat(network.getTrustRelationships()).isEmpty();
    }

    @Test
    void testLoadInvalidJson() {
        String invalidJson = "{ invalid json }";

        assertThatThrownBy(() ->
            loader.loadFromJsonString(invalidJson)
        ).isInstanceOf(IOException.class);
    }

    @Test
    void testLoadEmptyProvidersList() throws IOException {
        String json = """
            {
              "network_id": "test-network",
              "providers": {},
              "trust_relationships": []
            }
            """;

        TrustNetwork network = loader.loadFromJsonString(json);

        assertThat(network.getProviders()).isEmpty();
    }

    @Test
    void testLoadEmptyTrustRelationships() throws IOException {
        String json = """
            {
              "network_id": "test-network",
              "providers": {
                "provider-a": {
                  "issuer": "https://provider-a.test",
                  "role": "spoke"
                }
              },
              "trust_relationships": []
            }
            """;

        TrustNetwork network = loader.loadFromJsonString(json);

        assertThat(network.getTrustRelationships()).isEmpty();
    }
}
