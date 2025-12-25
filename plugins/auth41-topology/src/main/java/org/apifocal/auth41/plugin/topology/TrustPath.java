package org.apifocal.auth41.plugin.topology;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a trust path between two providers in a trust network.
 *
 * A trust path is an ordered sequence of provider IDs that forms a chain of trust
 * from a source provider to a target provider.
 */
public class TrustPath {

    private final String sourceProvider;
    private final String targetProvider;
    private final List<String> path;
    private final boolean reachable;

    private TrustPath(Builder builder) {
        this.sourceProvider = Objects.requireNonNull(builder.sourceProvider, "sourceProvider cannot be null");
        this.targetProvider = Objects.requireNonNull(builder.targetProvider, "targetProvider cannot be null");
        this.path = builder.path != null ? Collections.unmodifiableList(builder.path) : Collections.emptyList();
        this.reachable = builder.reachable;
    }

    public String getSourceProvider() {
        return sourceProvider;
    }

    public String getTargetProvider() {
        return targetProvider;
    }

    /**
     * Get the ordered list of provider IDs forming the trust path.
     * The first element is the source provider, the last is the target provider.
     *
     * @return Ordered list of provider IDs, or empty list if not reachable
     */
    public List<String> getPath() {
        return path;
    }

    /**
     * Check if the target provider is reachable from the source provider
     *
     * @return true if a trust path exists, false otherwise
     */
    public boolean isReachable() {
        return reachable;
    }

    /**
     * Get the length of the trust path (number of hops).
     * For direct trust, this returns 1.
     * For unreachable providers, this returns -1.
     *
     * @return Number of hops in the trust path, or -1 if unreachable
     */
    public int getHopCount() {
        if (!reachable) {
            return -1;
        }
        return Math.max(0, path.size() - 1);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String sourceProvider;
        private String targetProvider;
        private List<String> path;
        private boolean reachable;

        public Builder sourceProvider(String sourceProvider) {
            this.sourceProvider = sourceProvider;
            return this;
        }

        public Builder targetProvider(String targetProvider) {
            this.targetProvider = targetProvider;
            return this;
        }

        public Builder path(List<String> path) {
            this.path = path != null ? List.copyOf(path) : null;
            return this;
        }

        public Builder reachable(boolean reachable) {
            this.reachable = reachable;
            return this;
        }

        public TrustPath build() {
            return new TrustPath(this);
        }
    }

    @Override
    public String toString() {
        if (!reachable) {
            return "TrustPath{" + sourceProvider + " → " + targetProvider + " (unreachable)}";
        }
        return "TrustPath{" + String.join(" → ", path) + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrustPath trustPath = (TrustPath) o;
        return reachable == trustPath.reachable &&
               Objects.equals(sourceProvider, trustPath.sourceProvider) &&
               Objects.equals(targetProvider, trustPath.targetProvider) &&
               Objects.equals(path, trustPath.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceProvider, targetProvider, path, reachable);
    }
}
