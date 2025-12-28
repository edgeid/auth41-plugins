package org.apifocal.auth41.plugin.backchannel.mock;

import org.apifocal.auth41.ciba.spi.BackchannelAuthRequest;
import org.apifocal.auth41.ciba.spi.BackchannelAuthStatus;
import org.apifocal.auth41.ciba.spi.BackchannelException;
import org.apifocal.auth41.ciba.spi.CibaConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MockBackchannelProvider}.
 *
 * Tests the simulation logic, rate-based outcome determination, delay handling,
 * and configuration behavior.
 */
class MockBackchannelProviderTest {

    private static final String TEST_AUTH_REQ_ID = "urn:uuid:test-123";
    private static final String TEST_CLIENT_ID = "test-client";
    private static final String TEST_LOGIN_HINT = "testuser@example.com";

    private BackchannelAuthRequest createTestRequest(String authReqId, String loginHint) {
        return BackchannelAuthRequest.builder()
            .authReqId(authReqId)
            .clientId(TEST_CLIENT_ID)
            .scope("openid profile")
            .loginHint(loginHint)
            .createdAt(Instant.now())
            .build();
    }

    @Test
    void shouldSupportPollMode() {
        MockBackchannelProvider provider = new MockBackchannelProvider(100, 100, 0, true);

        Set<String> supportedModes = provider.getSupportedDeliveryModes();

        assertThat(supportedModes).containsExactly(CibaConstants.MODE_POLL);
    }

    @Test
    void shouldInitiateAuthenticationAndStorePendingRequest() throws BackchannelException {
        MockBackchannelProvider provider = new MockBackchannelProvider(1000, 100, 0, true);
        BackchannelAuthRequest request = createTestRequest(TEST_AUTH_REQ_ID, TEST_LOGIN_HINT);

        provider.initiateAuthentication(request);

        // Request should be pending immediately after initiation
        BackchannelAuthStatus status = provider.getAuthenticationStatus(TEST_AUTH_REQ_ID);
        assertThat(status.getAuthReqId()).isEqualTo(TEST_AUTH_REQ_ID);
        assertThat(status.getStatus()).isEqualTo(BackchannelAuthStatus.Status.PENDING);
    }

    @Test
    void shouldReturnPendingForUnknownAuthReqId() throws BackchannelException {
        MockBackchannelProvider provider = new MockBackchannelProvider(100, 100, 0, true);

        BackchannelAuthStatus status = provider.getAuthenticationStatus("unknown-id");

        assertThat(status.getAuthReqId()).isEqualTo("unknown-id");
        assertThat(status.getStatus()).isEqualTo(BackchannelAuthStatus.Status.PENDING);
    }

    @Test
    void shouldApproveAfterDelayWith100PercentApprovalRate() throws Exception {
        MockBackchannelProvider provider = new MockBackchannelProvider(50, 100, 0, true);
        BackchannelAuthRequest request = createTestRequest(TEST_AUTH_REQ_ID, TEST_LOGIN_HINT);

        provider.initiateAuthentication(request);

        // Should be pending before delay
        BackchannelAuthStatus status = provider.getAuthenticationStatus(TEST_AUTH_REQ_ID);
        assertThat(status.getStatus()).isEqualTo(BackchannelAuthStatus.Status.PENDING);

        // Wait for delay to elapse
        Thread.sleep(100);

        // Should be approved after delay
        status = provider.getAuthenticationStatus(TEST_AUTH_REQ_ID);
        assertThat(status.getStatus()).isEqualTo(BackchannelAuthStatus.Status.APPROVED);
        assertThat(status.getUserId()).isEqualTo("testuser");
        assertThat(status.getErrorCode()).isNull();
        assertThat(status.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldExtractUserIdFromEmailLoginHint() throws Exception {
        MockBackchannelProvider provider = new MockBackchannelProvider(10, 100, 0, true);
        BackchannelAuthRequest request = createTestRequest(TEST_AUTH_REQ_ID, "john.doe@company.com");

        provider.initiateAuthentication(request);
        Thread.sleep(50);

        BackchannelAuthStatus status = provider.getAuthenticationStatus(TEST_AUTH_REQ_ID);

        assertThat(status.getStatus()).isEqualTo(BackchannelAuthStatus.Status.APPROVED);
        assertThat(status.getUserId()).isEqualTo("john.doe");
    }

    @Test
    void shouldUseLoginHintAsUserIdWhenNotEmail() throws Exception {
        MockBackchannelProvider provider = new MockBackchannelProvider(10, 100, 0, true);
        BackchannelAuthRequest request = createTestRequest(TEST_AUTH_REQ_ID, "johndoe");

        provider.initiateAuthentication(request);
        Thread.sleep(50);

        BackchannelAuthStatus status = provider.getAuthenticationStatus(TEST_AUTH_REQ_ID);

        assertThat(status.getStatus()).isEqualTo(BackchannelAuthStatus.Status.APPROVED);
        assertThat(status.getUserId()).isEqualTo("johndoe");
    }

    @Test
    void shouldUseDefaultUserIdWhenLoginHintIsNull() throws Exception {
        MockBackchannelProvider provider = new MockBackchannelProvider(10, 100, 0, true);
        BackchannelAuthRequest request = BackchannelAuthRequest.builder()
            .authReqId(TEST_AUTH_REQ_ID)
            .clientId(TEST_CLIENT_ID)
            .scope("openid")
            .loginHint(null)
            .createdAt(Instant.now())
            .build();

        provider.initiateAuthentication(request);
        Thread.sleep(50);

        BackchannelAuthStatus status = provider.getAuthenticationStatus(TEST_AUTH_REQ_ID);

        assertThat(status.getStatus()).isEqualTo(BackchannelAuthStatus.Status.APPROVED);
        assertThat(status.getUserId()).isEqualTo("mock-user");
    }

    @Test
    void shouldDetermineApprovalBasedOnApprovalRate() throws Exception {
        // With 0% approval and 0% error, all should be denied
        MockBackchannelProvider provider = new MockBackchannelProvider(10, 0, 0, true);

        Set<BackchannelAuthStatus.Status> outcomes = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            String authReqId = "urn:uuid:test-" + i;
            BackchannelAuthRequest request = createTestRequest(authReqId, TEST_LOGIN_HINT);
            provider.initiateAuthentication(request);
        }

        Thread.sleep(50);

        for (int i = 0; i < 10; i++) {
            String authReqId = "urn:uuid:test-" + i;
            BackchannelAuthStatus status = provider.getAuthenticationStatus(authReqId);
            outcomes.add(status.getStatus());
        }

        // All should be denied
        assertThat(outcomes).containsExactly(BackchannelAuthStatus.Status.DENIED);
    }

    @Test
    void shouldDenyWithAccessDeniedError() throws Exception {
        MockBackchannelProvider provider = new MockBackchannelProvider(10, 0, 0, true);
        BackchannelAuthRequest request = createTestRequest(TEST_AUTH_REQ_ID, TEST_LOGIN_HINT);

        provider.initiateAuthentication(request);
        Thread.sleep(50);

        BackchannelAuthStatus status = provider.getAuthenticationStatus(TEST_AUTH_REQ_ID);

        assertThat(status.getStatus()).isEqualTo(BackchannelAuthStatus.Status.DENIED);
        assertThat(status.getErrorCode()).isEqualTo(CibaConstants.ERROR_ACCESS_DENIED);
        assertThat(status.getErrorDescription()).contains("User denied the authentication request");
        assertThat(status.getUserId()).isNull();
    }

    @Test
    void shouldDetermineErrorBasedOnErrorRate() throws Exception {
        // With 100% error rate, all should error
        MockBackchannelProvider provider = new MockBackchannelProvider(10, 0, 100, true);

        Set<BackchannelAuthStatus.Status> outcomes = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            String authReqId = "urn:uuid:test-" + i;
            BackchannelAuthRequest request = createTestRequest(authReqId, TEST_LOGIN_HINT);
            provider.initiateAuthentication(request);
        }

        Thread.sleep(50);

        for (int i = 0; i < 10; i++) {
            String authReqId = "urn:uuid:test-" + i;
            BackchannelAuthStatus status = provider.getAuthenticationStatus(authReqId);
            outcomes.add(status.getStatus());
        }

        // All should error
        assertThat(outcomes).containsExactly(BackchannelAuthStatus.Status.ERROR);
    }

    @Test
    void shouldReturnErrorWithServerErrorCode() throws Exception {
        MockBackchannelProvider provider = new MockBackchannelProvider(10, 0, 100, true);
        BackchannelAuthRequest request = createTestRequest(TEST_AUTH_REQ_ID, TEST_LOGIN_HINT);

        provider.initiateAuthentication(request);
        Thread.sleep(50);

        BackchannelAuthStatus status = provider.getAuthenticationStatus(TEST_AUTH_REQ_ID);

        assertThat(status.getStatus()).isEqualTo(BackchannelAuthStatus.Status.ERROR);
        assertThat(status.getErrorCode()).isEqualTo("server_error");
        assertThat(status.getErrorDescription()).contains("Simulated error");
        assertThat(status.getUserId()).isNull();
    }

    @Test
    void shouldDistributeOutcomesAccordingToRates() throws Exception {
        // 60% approval, 20% error, 20% deny
        MockBackchannelProvider provider = new MockBackchannelProvider(10, 60, 20, true);

        int approvedCount = 0;
        int deniedCount = 0;
        int errorCount = 0;
        int iterations = 1000;

        for (int i = 0; i < iterations; i++) {
            String authReqId = "urn:uuid:test-" + i;
            BackchannelAuthRequest request = createTestRequest(authReqId, TEST_LOGIN_HINT);
            provider.initiateAuthentication(request);
        }

        Thread.sleep(50);

        for (int i = 0; i < iterations; i++) {
            String authReqId = "urn:uuid:test-" + i;
            BackchannelAuthStatus status = provider.getAuthenticationStatus(authReqId);

            switch (status.getStatus()) {
                case APPROVED:
                    approvedCount++;
                    break;
                case DENIED:
                    deniedCount++;
                    break;
                case ERROR:
                    errorCount++;
                    break;
                default:
                    break;
            }
        }

        // Allow some variance due to randomness (±10%)
        assertThat(approvedCount).isBetween(500, 700); // ~60%
        assertThat(errorCount).isBetween(100, 300);    // ~20%
        assertThat(deniedCount).isBetween(100, 300);   // ~20%
    }

    @Test
    void shouldRemoveRequestAfterCompletion() throws Exception {
        MockBackchannelProvider provider = new MockBackchannelProvider(10, 100, 0, true);
        BackchannelAuthRequest request = createTestRequest(TEST_AUTH_REQ_ID, TEST_LOGIN_HINT);

        provider.initiateAuthentication(request);
        Thread.sleep(50);

        // First check should return approved and remove from pending
        BackchannelAuthStatus status1 = provider.getAuthenticationStatus(TEST_AUTH_REQ_ID);
        assertThat(status1.getStatus()).isEqualTo(BackchannelAuthStatus.Status.APPROVED);

        // Second check should return pending (unknown request)
        BackchannelAuthStatus status2 = provider.getAuthenticationStatus(TEST_AUTH_REQ_ID);
        assertThat(status2.getStatus()).isEqualTo(BackchannelAuthStatus.Status.PENDING);
        assertThat(status2.getUserId()).isNull();
    }

    @Test
    void shouldStayPendingWhenAutoApproveIsFalse() throws Exception {
        MockBackchannelProvider provider = new MockBackchannelProvider(10, 100, 0, false);
        BackchannelAuthRequest request = createTestRequest(TEST_AUTH_REQ_ID, TEST_LOGIN_HINT);

        provider.initiateAuthentication(request);
        Thread.sleep(50);

        // Should remain pending even after delay
        BackchannelAuthStatus status = provider.getAuthenticationStatus(TEST_AUTH_REQ_ID);
        assertThat(status.getStatus()).isEqualTo(BackchannelAuthStatus.Status.PENDING);
    }

    @Test
    void shouldCancelPendingAuthentication() throws BackchannelException {
        MockBackchannelProvider provider = new MockBackchannelProvider(1000, 100, 0, true);
        BackchannelAuthRequest request = createTestRequest(TEST_AUTH_REQ_ID, TEST_LOGIN_HINT);

        provider.initiateAuthentication(request);

        // Verify it's pending
        BackchannelAuthStatus status1 = provider.getAuthenticationStatus(TEST_AUTH_REQ_ID);
        assertThat(status1.getStatus()).isEqualTo(BackchannelAuthStatus.Status.PENDING);

        // Cancel it
        provider.cancelAuthentication(TEST_AUTH_REQ_ID);

        // Should now be unknown (returns pending for unknown)
        BackchannelAuthStatus status2 = provider.getAuthenticationStatus(TEST_AUTH_REQ_ID);
        assertThat(status2.getStatus()).isEqualTo(BackchannelAuthStatus.Status.PENDING);
    }

    @Test
    void shouldHandleCancellationOfNonExistentRequest() throws BackchannelException {
        MockBackchannelProvider provider = new MockBackchannelProvider(100, 100, 0, true);

        // Should not throw
        provider.cancelAuthentication("non-existent-id");
    }

    @Test
    void shouldCleanupExpiredRequests() throws Exception {
        MockBackchannelProvider provider = new MockBackchannelProvider(10000, 100, 0, false);

        // Create multiple requests
        for (int i = 0; i < 5; i++) {
            String authReqId = "urn:uuid:test-" + i;
            BackchannelAuthRequest request = createTestRequest(authReqId, TEST_LOGIN_HINT);
            provider.initiateAuthentication(request);
        }

        // Wait a bit
        Thread.sleep(100);

        // Cleanup requests older than 0 seconds (should remove all)
        int cleaned = provider.cleanupExpiredRequests(0);

        assertThat(cleaned).isEqualTo(5);

        // All should now be unknown
        for (int i = 0; i < 5; i++) {
            String authReqId = "urn:uuid:test-" + i;
            BackchannelAuthStatus status = provider.getAuthenticationStatus(authReqId);
            // Unknown requests return PENDING status
            assertThat(status.getStatus()).isEqualTo(BackchannelAuthStatus.Status.PENDING);
            assertThat(status.getUserId()).isNull();
        }
    }

    @Test
    void shouldNotCleanupRecentRequests() throws Exception {
        MockBackchannelProvider provider = new MockBackchannelProvider(10000, 100, 0, false);

        // Create requests
        for (int i = 0; i < 3; i++) {
            String authReqId = "urn:uuid:test-" + i;
            BackchannelAuthRequest request = createTestRequest(authReqId, TEST_LOGIN_HINT);
            provider.initiateAuthentication(request);
        }

        // Cleanup requests older than 1000 seconds (should remove none)
        int cleaned = provider.cleanupExpiredRequests(1000);

        assertThat(cleaned).isEqualTo(0);

        // All should still be pending
        for (int i = 0; i < 3; i++) {
            String authReqId = "urn:uuid:test-" + i;
            BackchannelAuthStatus status = provider.getAuthenticationStatus(authReqId);
            assertThat(status.getStatus()).isEqualTo(BackchannelAuthStatus.Status.PENDING);
        }
    }

    @Test
    void shouldCleanupOnlyExpiredRequests() throws Exception {
        MockBackchannelProvider provider = new MockBackchannelProvider(10000, 100, 0, false);

        // Create old requests
        provider.initiateAuthentication(createTestRequest("old-1", TEST_LOGIN_HINT));
        provider.initiateAuthentication(createTestRequest("old-2", TEST_LOGIN_HINT));

        // Wait 2 seconds for old requests to age
        Thread.sleep(2000);

        // Create new requests
        provider.initiateAuthentication(createTestRequest("new-1", TEST_LOGIN_HINT));
        provider.initiateAuthentication(createTestRequest("new-2", TEST_LOGIN_HINT));

        // Cleanup requests older than 1 second
        // Old requests are ~2 seconds old, new requests are ~0 seconds old
        // So only old requests should be cleaned
        int cleaned = provider.cleanupExpiredRequests(1);

        // Should clean only the 2 old requests
        assertThat(cleaned).isEqualTo(2);

        // Old should be unknown
        assertThat(provider.getAuthenticationStatus("old-1").getUserId()).isNull();
        assertThat(provider.getAuthenticationStatus("old-2").getUserId()).isNull();

        // New should still be pending
        BackchannelAuthStatus newStatus1 = provider.getAuthenticationStatus("new-1");
        BackchannelAuthStatus newStatus2 = provider.getAuthenticationStatus("new-2");
        assertThat(newStatus1.getStatus()).isEqualTo(BackchannelAuthStatus.Status.PENDING);
        assertThat(newStatus2.getStatus()).isEqualTo(BackchannelAuthStatus.Status.PENDING);
    }

    @Test
    void shouldClearAllRequestsOnClose() throws BackchannelException {
        MockBackchannelProvider provider = new MockBackchannelProvider(10000, 100, 0, false);

        // Create requests
        for (int i = 0; i < 3; i++) {
            String authReqId = "urn:uuid:test-" + i;
            BackchannelAuthRequest request = createTestRequest(authReqId, TEST_LOGIN_HINT);
            provider.initiateAuthentication(request);
        }

        // Close provider
        provider.close();

        // All should be unknown (pending without user info)
        for (int i = 0; i < 3; i++) {
            String authReqId = "urn:uuid:test-" + i;
            BackchannelAuthStatus status = provider.getAuthenticationStatus(authReqId);
            assertThat(status.getStatus()).isEqualTo(BackchannelAuthStatus.Status.PENDING);
            assertThat(status.getUserId()).isNull();
        }
    }

    @Test
    void shouldHandleMultipleConcurrentRequests() throws Exception {
        MockBackchannelProvider provider = new MockBackchannelProvider(50, 100, 0, true);

        // Initiate multiple requests
        int requestCount = 20;
        for (int i = 0; i < requestCount; i++) {
            String authReqId = "urn:uuid:concurrent-" + i;
            BackchannelAuthRequest request = createTestRequest(authReqId, "user" + i + "@example.com");
            provider.initiateAuthentication(request);
        }

        Thread.sleep(100);

        // Check all requests are approved
        for (int i = 0; i < requestCount; i++) {
            String authReqId = "urn:uuid:concurrent-" + i;
            BackchannelAuthStatus status = provider.getAuthenticationStatus(authReqId);
            assertThat(status.getStatus()).isEqualTo(BackchannelAuthStatus.Status.APPROVED);
            assertThat(status.getUserId()).isEqualTo("user" + i);
        }
    }

    @Test
    void shouldHandleZeroDelayConfiguration() throws Exception {
        MockBackchannelProvider provider = new MockBackchannelProvider(0, 100, 0, true);
        BackchannelAuthRequest request = createTestRequest(TEST_AUTH_REQ_ID, TEST_LOGIN_HINT);

        provider.initiateAuthentication(request);

        // Should be approved immediately (no sleep needed)
        BackchannelAuthStatus status = provider.getAuthenticationStatus(TEST_AUTH_REQ_ID);
        assertThat(status.getStatus()).isEqualTo(BackchannelAuthStatus.Status.APPROVED);
    }

    @Test
    void shouldHandleEdgeCaseRatesCorrectly() throws Exception {
        // Edge case: 50/50 split between approval and error
        MockBackchannelProvider provider = new MockBackchannelProvider(10, 50, 50, true);

        int approvedCount = 0;
        int errorCount = 0;
        int deniedCount = 0;
        int iterations = 1000;

        for (int i = 0; i < iterations; i++) {
            String authReqId = "urn:uuid:test-" + i;
            BackchannelAuthRequest request = createTestRequest(authReqId, TEST_LOGIN_HINT);
            provider.initiateAuthentication(request);
        }

        Thread.sleep(50);

        for (int i = 0; i < iterations; i++) {
            String authReqId = "urn:uuid:test-" + i;
            BackchannelAuthStatus status = provider.getAuthenticationStatus(authReqId);

            switch (status.getStatus()) {
                case APPROVED:
                    approvedCount++;
                    break;
                case DENIED:
                    deniedCount++;
                    break;
                case ERROR:
                    errorCount++;
                    break;
                default:
                    break;
            }
        }

        // Should be roughly 50% each, no denials
        assertThat(approvedCount).isBetween(400, 600);
        assertThat(errorCount).isBetween(400, 600);
        assertThat(deniedCount).isEqualTo(0);
    }
}
