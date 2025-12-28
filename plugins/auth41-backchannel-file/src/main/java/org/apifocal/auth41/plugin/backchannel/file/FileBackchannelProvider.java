package org.apifocal.auth41.plugin.backchannel.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apifocal.auth41.ciba.spi.BackchannelAuthRequest;
import org.apifocal.auth41.ciba.spi.BackchannelAuthStatus;
import org.apifocal.auth41.ciba.spi.BackchannelException;
import org.apifocal.auth41.ciba.spi.BackchannelProvider;
import org.apifocal.auth41.ciba.spi.CibaConstants;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * File-based backchannel provider for CIBA testing.
 *
 * Uses inbox/outbox pattern:
 * - Keycloak writes auth requests to inbox/{auth_req_id}.json
 * - External test processes write responses to outbox/{auth_req_id}.json
 * - Filename serves as correlation ID
 */
public class FileBackchannelProvider implements BackchannelProvider {

    private static final Logger logger = Logger.getLogger(FileBackchannelProvider.class);

    private final Path inboxPath;
    private final Path outboxPath;
    private final ObjectMapper objectMapper;

    public FileBackchannelProvider(Path baseDirectory) {
        this.inboxPath = baseDirectory.resolve("inbox");
        this.outboxPath = baseDirectory.resolve("outbox");
        this.objectMapper = createObjectMapper();
        initializeDirectories();
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        return mapper;
    }

    private void initializeDirectories() {
        try {
            Files.createDirectories(inboxPath);
            Files.createDirectories(outboxPath);
            logger.infof("File backchannel initialized: inbox=%s, outbox=%s",
                inboxPath, outboxPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize file backchannel directories", e);
        }
    }

    @Override
    public Set<String> getSupportedDeliveryModes() {
        Set<String> modes = new HashSet<>();
        modes.add(CibaConstants.MODE_POLL);
        return modes;
    }

    @Override
    public void initiateAuthentication(BackchannelAuthRequest request) throws BackchannelException {
        String authReqId = request.getAuthReqId();
        Path requestFile = inboxPath.resolve(authReqId + ".json");

        try {
            // Write request to inbox
            FileBackchannelRequest fileRequest = new FileBackchannelRequest(
                authReqId,
                request.getClientId(),
                request.getScope(),
                request.getLoginHint(),
                request.getBindingMessage(),
                request.getUserCode(),
                request.getRequestedExpiry(),
                request.getCreatedAt()
            );

            objectMapper.writeValue(requestFile.toFile(), fileRequest);
            logger.infof("CIBA request written to inbox: %s (loginHint=%s)",
                authReqId, request.getLoginHint());

        } catch (IOException e) {
            logger.errorf(e, "Failed to write CIBA request to inbox: %s", authReqId);
            throw new BackchannelException("Failed to initiate authentication", e);
        }
    }

    @Override
    public BackchannelAuthStatus getAuthenticationStatus(String authReqId) throws BackchannelException {
        Path responseFile = outboxPath.resolve(authReqId + ".json");

        // Check if response exists in outbox
        if (!Files.exists(responseFile)) {
            // Still pending - no response yet
            return BackchannelAuthStatus.builder()
                .authReqId(authReqId)
                .status(BackchannelAuthStatus.Status.PENDING)
                .build();
        }

        try {
            // Read response from outbox
            FileBackchannelResponse response = objectMapper.readValue(
                responseFile.toFile(),
                FileBackchannelResponse.class
            );

            logger.infof("CIBA response found in outbox: %s (status=%s)",
                authReqId, response.getStatus());

            // Convert file response to status
            BackchannelAuthStatus.Status status = convertStatus(response.getStatus());

            return BackchannelAuthStatus.builder()
                .authReqId(authReqId)
                .status(status)
                .userId(response.getUserId())
                .errorCode(response.getErrorCode())
                .errorDescription(response.getErrorDescription())
                .updatedAt(response.getUpdatedAt())
                .build();

        } catch (IOException e) {
            logger.errorf(e, "Failed to read CIBA response from outbox: %s", authReqId);
            throw new BackchannelException("Failed to get authentication status", e);
        }
    }

    @Override
    public void cancelAuthentication(String authReqId) throws BackchannelException {
        Path requestFile = inboxPath.resolve(authReqId + ".json");
        Path responseFile = outboxPath.resolve(authReqId + ".json");

        try {
            // Delete request from inbox
            if (Files.exists(requestFile)) {
                Files.delete(requestFile);
                logger.infof("CIBA request cancelled (deleted from inbox): %s", authReqId);
            }

            // Delete response from outbox if present
            if (Files.exists(responseFile)) {
                Files.delete(responseFile);
                logger.infof("CIBA response deleted from outbox: %s", authReqId);
            }

        } catch (IOException e) {
            logger.errorf(e, "Failed to cancel CIBA request: %s", authReqId);
            throw new BackchannelException("Failed to cancel authentication", e);
        }
    }

    @Override
    public int cleanupExpiredRequests(int maxAgeSeconds) {
        int cleaned = 0;
        Instant cutoff = Instant.now().minusSeconds(maxAgeSeconds);

        try {
            // Clean inbox
            cleaned += cleanupDirectory(inboxPath, cutoff);

            // Clean outbox
            cleaned += cleanupDirectory(outboxPath, cutoff);

            if (cleaned > 0) {
                logger.infof("Cleaned up %d expired CIBA files (older than %d seconds)",
                    cleaned, maxAgeSeconds);
            }

        } catch (IOException e) {
            logger.errorf(e, "Error during CIBA file cleanup");
        }

        return cleaned;
    }

    private int cleanupDirectory(Path directory, Instant cutoff) throws IOException {
        int cleaned = 0;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.json")) {
            for (Path file : stream) {
                Instant lastModified = Files.getLastModifiedTime(file).toInstant();
                if (lastModified.isBefore(cutoff)) {
                    Files.delete(file);
                    cleaned++;
                    logger.debugf("Deleted expired CIBA file: %s", file.getFileName());
                }
            }
        }

        return cleaned;
    }

    @Override
    public void close() {
        logger.debug("Closing file backchannel provider");
    }

    private BackchannelAuthStatus.Status convertStatus(String statusStr) {
        if (statusStr == null) {
            return BackchannelAuthStatus.Status.PENDING;
        }

        try {
            return BackchannelAuthStatus.Status.valueOf(statusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warnf("Unknown status value: %s, defaulting to PENDING", statusStr);
            return BackchannelAuthStatus.Status.PENDING;
        }
    }
}
