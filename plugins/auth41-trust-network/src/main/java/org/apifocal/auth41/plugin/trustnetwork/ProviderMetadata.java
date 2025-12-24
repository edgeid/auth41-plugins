package org.apifocal.auth41.plugin.trustnetwork;

/**
 * Provider metadata containing OIDC discovery information.
 *
 * This stores essential OIDC provider endpoints and configuration.
 */
public class ProviderMetadata {

    private String authorizationEndpoint;
    private String tokenEndpoint;
    private String jwksUri;
    private String userinfoEndpoint;
    private String organization;
    private String technicalContact;

    public ProviderMetadata() {
    }

    public String getAuthorizationEndpoint() {
        return authorizationEndpoint;
    }

    public void setAuthorizationEndpoint(String authorizationEndpoint) {
        this.authorizationEndpoint = authorizationEndpoint;
    }

    public String getTokenEndpoint() {
        return tokenEndpoint;
    }

    public void setTokenEndpoint(String tokenEndpoint) {
        this.tokenEndpoint = tokenEndpoint;
    }

    public String getJwksUri() {
        return jwksUri;
    }

    public void setJwksUri(String jwksUri) {
        this.jwksUri = jwksUri;
    }

    public String getUserinfoEndpoint() {
        return userinfoEndpoint;
    }

    public void setUserinfoEndpoint(String userinfoEndpoint) {
        this.userinfoEndpoint = userinfoEndpoint;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public String getTechnicalContact() {
        return technicalContact;
    }

    public void setTechnicalContact(String technicalContact) {
        this.technicalContact = technicalContact;
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
