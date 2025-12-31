package org.apifocal.auth41.plugin.backchannel.mock;

import org.apifocal.auth41.ciba.spi.BackchannelAuthRequest;
import org.apifocal.auth41.ciba.spi.BackchannelAuthStatus;
import org.apifocal.auth41.ciba.spi.BackchannelException;
import org.apifocal.auth41.ciba.spi.BackchannelProvider;
import org.apifocal.auth41.ciba.spi.CibaConstants;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock backchannel provider for CIBA testing.
 *
 * **DO NOT USE IN PRODUCTION**
 *
 * This provider simulates backchannel authentication responses for testing purposes.
 * It stores requests in memory and automatically approves/denies them based on
 * configurable parameters.
 *
 * Configuration:
 * - delay: Simulated authentication delay in milliseconds (default: 3000)
 * - approvalRate: Percentage of requests that are approved (default: 100)
 * - errorRate: Percentage of requests that fail with errors (default: 0)
 * - autoApprove: Whether to automatically approve (default: true)
 */
public class MockBackchannelProvider implements BackchannelProvider {

    private static final Logger logger = Logger.getLogger(MockBackchannelProvider.class);

    private final long delayMillis;
    private final int approvalRate;
    private final int errorRate;
    private final boolean autoApprove;
    private final Random random;

    // In-memory storage of pending auth requests
    private final Map<String, MockAuthRequest> pendingRequests;

    public MockBackchannelProvider(long delayMillis, int approvalRate, int errorRate, boolean autoApprove) {
        this.delayMillis = delayMillis;
        this.approvalRate = approvalRate;
        this.errorRate = errorRate;
        this.autoApprove = autoApprove;
        this.random = new Random();
        this.pendingRequests = new ConcurrentHashMap<>();

        logger.warn("═══════════════════════════════════════════════════════════");
        logger.warn("  MOCK BACKCHANNEL PROVIDER ACTIVE - DO NOT USE IN PRODUCTION");
        logger.warn("  This provider auto-simulates authentication responses");
        logger.warn("  Configuration: delay=" + delayMillis + "ms, approval=" + approvalRate + "%, error=" + errorRate + "%");
        logger.warn("═══════════════════════════════════════════════════════════");
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

        logger.warnf("[MOCK] Simulating authentication request: auth_req_id=%s, loginHint=%s, delay=%dms",
            authReqId, request.getLoginHint(), delayMillis);

        // Store request with timestamp
        MockAuthRequest mockRequest = new MockAuthRequest(
            request,
            Instant.now(),
            determineOutcome()
        );

        pendingRequests.put(authReqId, mockRequest);

        logger.debugf("[MOCK] Stored pending request: %s (will %s after delay)",
            authReqId, mockRequest.outcome);
    }

    @Override
    public BackchannelAuthStatus getAuthenticationStatus(String authReqId) throws BackchannelException {
        MockAuthRequest mockRequest = pendingRequests.get(authReqId);

        if (mockRequest == null) {
            // Unknown request - return pending (client might be polling before we've processed it)
            return BackchannelAuthStatus.builder()
                .authReqId(authReqId)
                .status(BackchannelAuthStatus.Status.PENDING)
                .build();
        }

        // Check if enough time has elapsed
        long elapsedMillis = Instant.now().toEpochMilli() - mockRequest.initiatedAt.toEpochMilli();

        if (!autoApprove || elapsedMillis < delayMillis) {
            // Still pending
            logger.debugf("[MOCK] Request %s still pending (elapsed: %dms / %dms)",
                authReqId, elapsedMillis, delayMillis);
            return BackchannelAuthStatus.builder()
                .authReqId(authReqId)
                .status(BackchannelAuthStatus.Status.PENDING)
                .build();
        }

        // Time has elapsed, return the predetermined outcome
        BackchannelAuthStatus.Status status;
        String errorCode = null;
        String errorDescription = null;
        String userId = null;

        switch (mockRequest.outcome) {
            case APPROVE:
                status = BackchannelAuthStatus.Status.APPROVED;
                userId = extractUserId(mockRequest.request.getLoginHint());
                logger.warnf("[MOCK] Auto-approving request: %s (user=%s)", authReqId, userId);
                break;

            case DENY:
                status = BackchannelAuthStatus.Status.DENIED;
                errorCode = CibaConstants.ERROR_ACCESS_DENIED;
                errorDescription = "User denied the authentication request (mock simulation)";
                logger.warnf("[MOCK] Auto-denying request: %s", authReqId);
                break;

            case ERROR:
                status = BackchannelAuthStatus.Status.ERROR;
                errorCode = "server_error";
                errorDescription = "Simulated error during authentication (mock)";
                logger.warnf("[MOCK] Simulating error for request: %s", authReqId);
                break;

            default:
                status = BackchannelAuthStatus.Status.PENDING;
        }

        // Remove from pending if complete
        if (status != BackchannelAuthStatus.Status.PENDING) {
            pendingRequests.remove(authReqId);
        }

        return BackchannelAuthStatus.builder()
            .authReqId(authReqId)
            .status(status)
            .userId(userId)
            .scope(mockRequest.request.getScope())
            .errorCode(errorCode)
            .errorDescription(errorDescription)
            .updatedAt(Instant.now())
            .build();
    }

    @Override
    public void cancelAuthentication(String authReqId) throws BackchannelException {
        MockAuthRequest removed = pendingRequests.remove(authReqId);
        if (removed != null) {
            logger.warnf("[MOCK] Cancelled authentication request: %s", authReqId);
        }
    }

    @Override
    public int cleanupExpiredRequests(int maxAgeSeconds) {
        Instant cutoff = Instant.now().minusSeconds(maxAgeSeconds);
        int cleaned = 0;

        for (Map.Entry<String, MockAuthRequest> entry : pendingRequests.entrySet()) {
            if (entry.getValue().initiatedAt.isBefore(cutoff)) {
                pendingRequests.remove(entry.getKey());
                cleaned++;
            }
        }

        if (cleaned > 0) {
            logger.debugf("[MOCK] Cleaned up %d expired requests (older than %d seconds)",
                cleaned, maxAgeSeconds);
        }

        return cleaned;
    }

    @Override
    public void close() {
        logger.info("[MOCK] Closing mock backchannel provider");
        pendingRequests.clear();
    }

    /**
     * Determine the outcome for a request based on configured rates
     */
    private MockOutcome determineOutcome() {
        if (!autoApprove) {
            return MockOutcome.PENDING;
        }

        int roll = random.nextInt(100);

        // First check for error
        if (roll < errorRate) {
            return MockOutcome.ERROR;
        }

        // Then check for approval
        if (roll < (errorRate + approvalRate)) {
            return MockOutcome.APPROVE;
        }

        // Otherwise deny
        return MockOutcome.DENY;
    }

    /**
     * Extract a mock user ID from the login hint
     */
    private String extractUserId(String loginHint) {
        if (loginHint == null) {
            return "mock-user";
        }

        // Use the part before @ for email, or the whole string otherwise
        int atIndex = loginHint.indexOf('@');
        if (atIndex > 0) {
            return loginHint.substring(0, atIndex);
        }

        return loginHint;
    }

    /**
     * Internal storage for pending authentication requests
     */
    private static class MockAuthRequest {
        final BackchannelAuthRequest request;
        final Instant initiatedAt;
        final MockOutcome outcome;

        MockAuthRequest(BackchannelAuthRequest request, Instant initiatedAt, MockOutcome outcome) {
            this.request = request;
            this.initiatedAt = initiatedAt;
            this.outcome = outcome;
        }
    }

    /**
     * Possible outcomes for mock authentication
     */
    private enum MockOutcome {
        APPROVE,
        DENY,
        ERROR,
        PENDING
    }
}
