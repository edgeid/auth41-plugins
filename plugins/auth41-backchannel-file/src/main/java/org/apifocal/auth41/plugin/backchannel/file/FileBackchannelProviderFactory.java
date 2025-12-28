package org.apifocal.auth41.plugin.backchannel.file;

import org.apifocal.auth41.ciba.spi.BackchannelProvider;
import org.apifocal.auth41.ciba.spi.BackchannelProviderFactory;
import org.apifocal.auth41.ciba.spi.CibaConstants;
import org.apifocal.auth41.spi.AbstractProviderFactory;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

/**
 * Factory for creating file-based backchannel providers.
 */
public class FileBackchannelProviderFactory extends AbstractProviderFactory<BackchannelProvider>
        implements BackchannelProviderFactory {

    private static final String PROVIDER_ID = "file";
    private static final String DEFAULT_BASE_DIR = "/var/auth41/backchannel";
    private static final String CONFIG_BASE_DIR = "baseDirectory";

    private Path baseDirectory;

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    protected void doInit(Config.Scope config) {
        String baseDirPath = getConfig(config, CONFIG_BASE_DIR, DEFAULT_BASE_DIR);
        this.baseDirectory = Paths.get(baseDirPath);
        logger.infof("File backchannel base directory: %s", baseDirectory);
    }

    @Override
    public BackchannelProvider create(KeycloakSession session) {
        return new FileBackchannelProvider(baseDirectory);
    }

    @Override
    public Set<String> getSupportedDeliveryModes() {
        Set<String> modes = new HashSet<>();
        modes.add(CibaConstants.MODE_POLL);
        return modes;
    }
}
