package org.apifocal.auth41.plugin.topology;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

/**
 * Factory for creating PeerToPeerTopologyProvider instances.
 */
public class PeerToPeerTopologyProviderFactory implements TopologyProviderFactory {

    private static final Logger logger = Logger.getLogger(PeerToPeerTopologyProviderFactory.class);
    private static final String PROVIDER_ID = "peer-to-peer";

    @Override
    public TopologyProvider create(KeycloakSession session) {
        return new PeerToPeerTopologyProvider(session);
    }

    @Override
    public void init(Config.Scope config) {
        logger.info("Initializing PeerToPeerTopologyProviderFactory");
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // No post-initialization needed
    }

    @Override
    public void close() {
        logger.info("Closing PeerToPeerTopologyProviderFactory");
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getTopologyType() {
        return PROVIDER_ID;
    }
}
