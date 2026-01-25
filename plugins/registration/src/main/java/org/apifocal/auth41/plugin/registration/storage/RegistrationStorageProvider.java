package org.apifocal.auth41.plugin.registration.storage;

import org.apifocal.auth41.plugin.registration.model.InviteToken;
import org.apifocal.auth41.plugin.registration.model.RegistrationRequest;
import org.keycloak.provider.Provider;

import java.time.Instant;
import java.util.List;

/**
 * SPI for registration workflow storage.
 *
 * <p>This provider stores invite tokens and registration requests,
 * managing the two-step registration workflow with IP-based rate limiting.
 *
 * <p>Implementations should:
 * <ul>
 *   <li>Provide CRUD operations for invite tokens and registration requests
 *   <li>Support rate limiting queries by IP address
 *   <li>Query pending requests for approval processor
 *   <li>Handle concurrent access safely
 * </ul>
 */
public interface RegistrationStorageProvider extends Provider {

    // Invite token operations

    /**
     * Create a new invite token.
     *
     * @param token Invite token to create
     * @throws IllegalArgumentException if token already exists
     */
    void createInviteToken(InviteToken token);

    /**
     * Get invite token by token string.
     *
     * @param inviteToken Token string (UUID)
     * @return Invite token or null if not found
     */
    InviteToken getInviteToken(String inviteToken);

    /**
     * Count recent invite tokens from an IP address.
     * Used for rate limiting.
     *
     * @param ipAddress IP address to check
     * @param since Only count tokens created after this instant
     * @return Number of recent invite tokens from this IP
     */
    long countRecentInvitesByIp(String ipAddress, Instant since);

    /**
     * Mark an invite token as used.
     *
     * @param inviteToken Token string to mark as used
     * @throws IllegalArgumentException if token doesn't exist
     */
    void markInviteTokenUsed(String inviteToken);

    // Registration request operations

    /**
     * Create a new registration request.
     *
     * @param request Registration request to create
     * @throws IllegalArgumentException if request already exists
     */
    void createRegistrationRequest(RegistrationRequest request);

    /**
     * Get registration request by request ID.
     *
     * @param requestId Request ID (UUID)
     * @return Registration request or null if not found
     */
    RegistrationRequest getRegistrationRequest(String requestId);

    /**
     * Update existing registration request.
     * Used to update status when approved/denied.
     *
     * @param request Updated request information
     * @throws IllegalArgumentException if request doesn't exist
     */
    void updateRegistrationRequest(RegistrationRequest request);

    /**
     * Get pending registration requests older than specified time.
     * Used by approval processor to find requests ready for approval.
     *
     * @param olderThan Only return requests created before this instant
     * @return List of pending registration requests
     */
    List<RegistrationRequest> getPendingRequests(Instant olderThan);

    // Cleanup operations

    /**
     * Delete expired invite tokens.
     * Used by cleanup task to prevent database bloat.
     *
     * @param expiredBefore Delete tokens that expired before this instant
     * @return Number of tokens deleted
     */
    int deleteExpiredInviteTokens(Instant expiredBefore);

    /**
     * Delete expired registration requests.
     * Used by cleanup task to prevent database bloat.
     *
     * @param expiredBefore Delete requests that expired before this instant
     * @return Number of requests deleted
     */
    int deleteExpiredRegistrationRequests(Instant expiredBefore);

    // Test-only operations

    /**
     * Delete all invite tokens (test-only).
     * <p>WARNING: This is for testing purposes only.
     *
     * @return Number of tokens deleted
     */
    int deleteAllInviteTokens();

    /**
     * Delete all registration requests (test-only).
     * <p>WARNING: This is for testing purposes only.
     *
     * @return Number of requests deleted
     */
    int deleteAllRegistrationRequests();
}
