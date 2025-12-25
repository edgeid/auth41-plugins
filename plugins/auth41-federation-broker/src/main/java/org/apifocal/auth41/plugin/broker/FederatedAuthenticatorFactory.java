package org.apifocal.auth41.plugin.broker;

import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.Collections;
import java.util.List;

/**
 * Factory for creating FederatedAuthenticator instances.
 *
 * <p>This factory registers the federated authenticator with Keycloak's authentication
 * SPI system. It provides metadata about the authenticator and creates instances
 * for each authentication flow execution.
 *
 * <p>Configuration:
 * <pre>
 * --spi-authenticator-auth41-federated-network-id=production-network
 * </pre>
 */
public class FederatedAuthenticatorFactory implements AuthenticatorFactory {

    public static final String PROVIDER_ID = "auth41-federated";
    private static final String DISPLAY_NAME = "Auth41 Federated Authentication";
    private static final String HELP_TEXT = "Authenticates users via federated OIDC providers in the Auth41 trust network";
    private static final String REFERENCE_CATEGORY = "federation";

    private static final AuthenticationExecutionModel.Requirement[] REQUIREMENT_CHOICES = {
        AuthenticationExecutionModel.Requirement.REQUIRED,
        AuthenticationExecutionModel.Requirement.ALTERNATIVE,
        AuthenticationExecutionModel.Requirement.DISABLED
    };

    @Override
    public String getDisplayType() {
        return DISPLAY_NAME;
    }

    @Override
    public String getReferenceCategory() {
        return REFERENCE_CATEGORY;
    }

    @Override
    public boolean isConfigurable() {
        return false; // TODO: Add configuration support for network ID, etc.
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return REQUIREMENT_CHOICES;
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false; // No user-specific setup needed
    }

    @Override
    public String getHelpText() {
        return HELP_TEXT;
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        // TODO: Add configuration properties
        // - Network ID
        // - Account chooser enabled/disabled
        // - Allowed home providers
        // - Shadow user creation strategy
        return Collections.emptyList();
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return new FederatedAuthenticator();
    }

    @Override
    public void init(Config.Scope config) {
        // No initialization needed
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // No post-initialization needed
    }

    @Override
    public void close() {
        // No resources to clean up
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}
