package org.apifocal.auth41.plugin.trustnetwork;

/**
 * Provider metadata containing OIDC discovery information.
 *
 * This stores essential OIDC provider endpoints and configuration.
 * Immutable data class using builder pattern.
 */
public class ProviderMetadata {

    private final String authorizationEndpoint;
    private final String tokenEndpoint;
    private final String jwksUri;
    private final String userinfoEndpoint;
    private final String organization;
    private final String technicalContact;
    private final String backchannelAuthenticationEndpoint;
    private final String backchannelAuthenticationCallbackEndpoint;

    // Default constructor for backward compatibility (creates empty metadata)
    public ProviderMetadata() {
        this(new Builder());
    }

    private ProviderMetadata(Builder builder) {
        this.authorizationEndpoint = builder.authorizationEndpoint;
        this.tokenEndpoint = builder.tokenEndpoint;
        this.jwksUri = builder.jwksUri;
        this.userinfoEndpoint = builder.userinfoEndpoint;
        this.organization = builder.organization;
        this.technicalContact = builder.technicalContact;
        this.backchannelAuthenticationEndpoint = builder.backchannelAuthenticationEndpoint;
        this.backchannelAuthenticationCallbackEndpoint = builder.backchannelAuthenticationCallbackEndpoint;
    }

    public String getAuthorizationEndpoint() {
        return authorizationEndpoint;
    }

    public String getTokenEndpoint() {
        return tokenEndpoint;
    }

    public String getJwksUri() {
        return jwksUri;
    }

    public String getUserinfoEndpoint() {
        return userinfoEndpoint;
    }

    public String getOrganization() {
        return organization;
    }

    public String getTechnicalContact() {
        return technicalContact;
    }

    public String getBackchannelAuthenticationEndpoint() {
        return backchannelAuthenticationEndpoint;
    }

    public String getBackchannelAuthenticationCallbackEndpoint() {
        return backchannelAuthenticationCallbackEndpoint;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String authorizationEndpoint;
        private String tokenEndpoint;
        private String jwksUri;
        private String userinfoEndpoint;
        private String organization;
        private String technicalContact;
        private String backchannelAuthenticationEndpoint;
        private String backchannelAuthenticationCallbackEndpoint;

        public Builder authorizationEndpoint(String authorizationEndpoint) {
            this.authorizationEndpoint = authorizationEndpoint;
            return this;
        }

        public Builder tokenEndpoint(String tokenEndpoint) {
            this.tokenEndpoint = tokenEndpoint;
            return this;
        }

        public Builder jwksUri(String jwksUri) {
            this.jwksUri = jwksUri;
            return this;
        }

        public Builder userinfoEndpoint(String userinfoEndpoint) {
            this.userinfoEndpoint = userinfoEndpoint;
            return this;
        }

        public Builder organization(String organization) {
            this.organization = organization;
            return this;
        }

        public Builder technicalContact(String technicalContact) {
            this.technicalContact = technicalContact;
            return this;
        }

        public Builder backchannelAuthenticationEndpoint(String backchannelAuthenticationEndpoint) {
            this.backchannelAuthenticationEndpoint = backchannelAuthenticationEndpoint;
            return this;
        }

        public Builder backchannelAuthenticationCallbackEndpoint(String backchannelAuthenticationCallbackEndpoint) {
            this.backchannelAuthenticationCallbackEndpoint = backchannelAuthenticationCallbackEndpoint;
            return this;
        }

        public ProviderMetadata build() {
            return new ProviderMetadata(this);
        }
    }

    @Override
    public String toString() {
        return "ProviderMetadata{" +
               "authorizationEndpoint='" + authorizationEndpoint + '\'' +
               ", tokenEndpoint='" + tokenEndpoint + '\'' +
               ", jwksUri='" + jwksUri + '\'' +
               '}';
    }
}
