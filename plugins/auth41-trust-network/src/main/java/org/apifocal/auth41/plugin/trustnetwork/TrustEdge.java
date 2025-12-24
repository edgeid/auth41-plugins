package org.apifocal.auth41.plugin.trustnetwork;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a directed trust relationship between two providers.
 *
 * Trust relationships are directional:
 * - fromProvider trusts toProvider
 * - toProvider may or may not trust fromProvider (separate edge)
 */
public class TrustEdge {

    private final String fromProvider;
    private final String toProvider;
    private final TrustLevel level;
    private final Instant established;

    private TrustEdge(Builder builder) {
        this.fromProvider = Objects.requireNonNull(builder.fromProvider, "fromProvider cannot be null");
        this.toProvider = Objects.requireNonNull(builder.toProvider, "toProvider cannot be null");
        this.level = builder.level != null ? builder.level : TrustLevel.EXPLICIT;
        this.established = builder.established != null ? builder.established : Instant.now();
    }

    public String getFromProvider() {
        return fromProvider;
    }

    public String getToProvider() {
        return toProvider;
    }

    public TrustLevel getLevel() {
        return level;
    }

    public Instant getEstablished() {
        return established;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String fromProvider;
        private String toProvider;
        private TrustLevel level;
        private Instant established;

        public Builder fromProvider(String fromProvider) {
            this.fromProvider = fromProvider;
            return this;
        }

        public Builder toProvider(String toProvider) {
            this.toProvider = toProvider;
            return this;
        }

        public Builder level(TrustLevel level) {
            this.level = level;
            return this;
        }

        public Builder established(Instant established) {
            this.established = established;
            return this;
        }

        public TrustEdge build() {
            return new TrustEdge(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrustEdge trustEdge = (TrustEdge) o;
        return Objects.equals(fromProvider, trustEdge.fromProvider) &&
               Objects.equals(toProvider, trustEdge.toProvider);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromProvider, toProvider);
    }

    @Override
    public String toString() {
        return "TrustEdge{" +
               fromProvider + " → " + toProvider +
               ", level=" + level +
               '}';
    }
}
