package org.apifocal.auth41.plugin.topology;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrustPathTest {

    @Test
    void testDirectPath() {
        TrustPath path = TrustPath.builder()
            .sourceProvider("provider-a")
            .targetProvider("provider-b")
            .path(List.of("provider-a", "provider-b"))
            .reachable(true)
            .build();

        assertThat(path.getSourceProvider()).isEqualTo("provider-a");
        assertThat(path.getTargetProvider()).isEqualTo("provider-b");
        assertThat(path.getPath()).containsExactly("provider-a", "provider-b");
        assertThat(path.isReachable()).isTrue();
        assertThat(path.getHopCount()).isEqualTo(1);
    }

    @Test
    void testMultiHopPath() {
        TrustPath path = TrustPath.builder()
            .sourceProvider("provider-a")
            .targetProvider("provider-c")
            .path(List.of("provider-a", "hub-b", "provider-c"))
            .reachable(true)
            .build();

        assertThat(path.getHopCount()).isEqualTo(2);
        assertThat(path.getPath()).hasSize(3);
    }

    @Test
    void testUnreachablePath() {
        TrustPath path = TrustPath.builder()
            .sourceProvider("provider-a")
            .targetProvider("provider-b")
            .reachable(false)
            .build();

        assertThat(path.isReachable()).isFalse();
        assertThat(path.getHopCount()).isEqualTo(-1);
        assertThat(path.getPath()).isEmpty();
    }

    @Test
    void testSameProviderPath() {
        TrustPath path = TrustPath.builder()
            .sourceProvider("provider-a")
            .targetProvider("provider-a")
            .path(List.of("provider-a"))
            .reachable(true)
            .build();

        assertThat(path.isReachable()).isTrue();
        assertThat(path.getHopCount()).isEqualTo(0);
    }

    @Test
    void testToString() {
        TrustPath reachable = TrustPath.builder()
            .sourceProvider("a")
            .targetProvider("b")
            .path(List.of("a", "hub", "b"))
            .reachable(true)
            .build();

        assertThat(reachable.toString()).contains("→");
        assertThat(reachable.toString()).contains("a");
        assertThat(reachable.toString()).contains("hub");
        assertThat(reachable.toString()).contains("b");

        TrustPath unreachable = TrustPath.builder()
            .sourceProvider("a")
            .targetProvider("b")
            .reachable(false)
            .build();

        assertThat(unreachable.toString()).contains("unreachable");
    }
}
