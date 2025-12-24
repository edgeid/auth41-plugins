package org.apifocal.auth41.plugin.trustnetwork;

/**
 * Level of trust in a trust relationship.
 */
public enum TrustLevel {
    /**
     * Explicit trust - directly configured trust relationship
     */
    EXPLICIT,

    /**
     * Transitive trust - trust derived through intermediate providers
     * (e.g., A trusts B, B trusts C, therefore A transitively trusts C)
     */
    TRANSITIVE
}
