package org.apifocal.auth41.plugin.backchannel.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apifocal.auth41.ciba.spi.BackchannelAuthRequest;
import org.apifocal.auth41.ciba.spi.BackchannelAuthStatus;
import org.apifocal.auth41.ciba.spi.CibaConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FileBackchannelProviderTest {

    @TempDir
    Path tempDir;

    private FileBackchannelProvider provider;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        provider = new FileBackchannelProvider(tempDir);
        objectMapper = createObjectMapper();
    }

    @AfterEach
    void tearDown() {
        provider.close();
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        return mapper;
    }

    @Test
    void shouldSupportPollMode() {
        Set<String> modes = provider.getSupportedDeliveryModes();
        assertThat(modes).containsExactly(CibaConstants.MODE_POLL);
    }

    @Test
    void shouldWriteRequestToInbox() throws Exception {
        // Given
        BackchannelAuthRequest request = BackchannelAuthRequest.builder()
            .authReqId("auth-123")
            .clientId("test-client")
            .scope("openid profile")
            .loginHint("user@example.com")
            .bindingMessage("Login to Test App")
            .requestedExpiry(300)
            .build();

        // When
        provider.initiateAuthentication(request);

        // Then
        Path requestFile = tempDir.resolve("inbox/auth-123.json");
        assertThat(requestFile).exists();

        FileBackchannelRequest fileRequest = objectMapper.readValue(
            requestFile.toFile(),
            FileBackchannelRequest.class
        );

        assertThat(fileRequest.getAuthReqId()).isEqualTo("auth-123");
        assertThat(fileRequest.getClientId()).isEqualTo("test-client");
        assertThat(fileRequest.getScope()).isEqualTo("openid profile");
        assertThat(fileRequest.getLoginHint()).isEqualTo("user@example.com");
        assertThat(fileRequest.getBindingMessage()).isEqualTo("Login to Test App");
        assertThat(fileRequest.getRequestedExpiry()).isEqualTo(300);
    }

    @Test
    void shouldReturnPendingWhenNoResponse() throws Exception {
        // When
        BackchannelAuthStatus status = provider.getAuthenticationStatus("auth-456");

        // Then
        assertThat(status.getAuthReqId()).isEqualTo("auth-456");
        assertThat(status.getStatus()).isEqualTo(BackchannelAuthStatus.Status.PENDING);
        assertThat(status.getUserId()).isNull();
    }

    @Test
    void shouldReadApprovedResponseFromOutbox() throws Exception {
        // Given
        String authReqId = "auth-789";
        FileBackchannelResponse response = FileBackchannelResponse.approved(authReqId, "user-123", "openid profile");

        Path responseFile = tempDir.resolve("outbox/" + authReqId + ".json");
        Files.createDirectories(responseFile.getParent());
        objectMapper.writeValue(responseFile.toFile(), response);

        // When
        BackchannelAuthStatus status = provider.getAuthenticationStatus(authReqId);

        // Then
        assertThat(status.getAuthReqId()).isEqualTo(authReqId);
        assertThat(status.getStatus()).isEqualTo(BackchannelAuthStatus.Status.APPROVED);
        assertThat(status.getUserId()).isEqualTo("user-123");
        assertThat(status.isApproved()).isTrue();
        assertThat(status.isComplete()).isTrue();
    }

    @Test
    void shouldReadDeniedResponseFromOutbox() throws Exception {
        // Given
        String authReqId = "auth-denied";
        FileBackchannelResponse response = FileBackchannelResponse.denied(authReqId);

        Path responseFile = tempDir.resolve("outbox/" + authReqId + ".json");
        Files.createDirectories(responseFile.getParent());
        objectMapper.writeValue(responseFile.toFile(), response);

        // When
        BackchannelAuthStatus status = provider.getAuthenticationStatus(authReqId);

        // Then
        assertThat(status.getAuthReqId()).isEqualTo(authReqId);
        assertThat(status.getStatus()).isEqualTo(BackchannelAuthStatus.Status.DENIED);
        assertThat(status.getErrorCode()).isEqualTo("access_denied");
        assertThat(status.isApproved()).isFalse();
        assertThat(status.isComplete()).isTrue();
    }

    @Test
    void shouldCancelAuthentication() throws Exception {
        // Given
        BackchannelAuthRequest request = BackchannelAuthRequest.builder()
            .authReqId("auth-cancel")
            .clientId("test-client")
            .loginHint("user@example.com")
            .build();

        provider.initiateAuthentication(request);

        Path requestFile = tempDir.resolve("inbox/auth-cancel.json");
        assertThat(requestFile).exists();

        // When
        provider.cancelAuthentication("auth-cancel");

        // Then
        assertThat(requestFile).doesNotExist();
    }

    @Test
    void shouldCleanupExpiredRequests() throws Exception {
        // Given - create old request (2 hours ago)
        String oldAuthReqId = "auth-old";
        BackchannelAuthRequest oldRequest = BackchannelAuthRequest.builder()
            .authReqId(oldAuthReqId)
            .clientId("test-client")
            .loginHint("user@example.com")
            .createdAt(Instant.now().minusSeconds(7200)) // 2 hours ago
            .build();

        provider.initiateAuthentication(oldRequest);
        Path oldFile = tempDir.resolve("inbox/" + oldAuthReqId + ".json");

        // Manually set file timestamp to 2 hours ago
        Files.setLastModifiedTime(oldFile,
            java.nio.file.attribute.FileTime.from(Instant.now().minusSeconds(7200)));

        // Create recent request
        String newAuthReqId = "auth-new";
        BackchannelAuthRequest newRequest = BackchannelAuthRequest.builder()
            .authReqId(newAuthReqId)
            .clientId("test-client")
            .loginHint("user@example.com")
            .build();

        provider.initiateAuthentication(newRequest);
        Path newFile = tempDir.resolve("inbox/" + newAuthReqId + ".json");

        // When - cleanup requests older than 1 hour
        int cleaned = provider.cleanupExpiredRequests(3600);

        // Then
        assertThat(cleaned).isEqualTo(1);
        assertThat(oldFile).doesNotExist();
        assertThat(newFile).exists();
    }

    @Test
    void shouldCreateInboxAndOutboxDirectories() {
        Path inbox = tempDir.resolve("inbox");
        Path outbox = tempDir.resolve("outbox");

        assertThat(inbox).exists().isDirectory();
        assertThat(outbox).exists().isDirectory();
    }
}
