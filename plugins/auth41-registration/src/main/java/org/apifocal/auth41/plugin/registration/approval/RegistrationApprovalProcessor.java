package org.apifocal.auth41.plugin.registration.approval;

import org.apifocal.auth41.plugin.registration.config.RegistrationConfig;
import org.apifocal.auth41.plugin.registration.model.RegistrationRequest;
import org.apifocal.auth41.plugin.registration.storage.RegistrationStorageProvider;
import org.jboss.logging.Logger;
import org.keycloak.models.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Processes pending registration requests for auto-approval.
 *
 * <p>This processor:
 * <ul>
 *   <li>Finds pending requests older than approval delay</li>
 *   <li>Creates Keycloak user accounts</li>
 *   <li>Sets user attributes from registration data</li>
 *   <li>Updates request status to APPROVED</li>
 * </ul>
 *
 * <p>Should be called periodically (e.g., every 5 seconds) by a timer task.
 */
public class RegistrationApprovalProcessor {

    private static final Logger logger = Logger.getLogger(RegistrationApprovalProcessor.class);

    private final KeycloakSession session;
    private final RegistrationConfig config;

    public RegistrationApprovalProcessor(KeycloakSession session, RegistrationConfig config) {
        this.session = session;
        this.config = config;
    }

    /**
     * Process pending registration requests.
     *
     * <p>Finds all pending requests older than the approval delay threshold
     * and attempts to create user accounts for them.
     *
     * @return Number of requests processed
     */
    public int processPendingRequests() {
        try {
            RegistrationStorageProvider storage = session.getProvider(RegistrationStorageProvider.class);
            if (storage == null) {
                logger.warn("RegistrationStorageProvider not available");
                return 0;
            }

            // Calculate threshold time (now - approval delay)
            Instant threshold = Instant.now().minusSeconds(config.getApprovalDelaySeconds());

            // Get pending requests older than threshold
            List<RegistrationRequest> pendingRequests = storage.getPendingRequests(threshold);

            if (pendingRequests.isEmpty()) {
                logger.trace("No pending registration requests to process");
                return 0;
            }

            logger.debugf("Processing %d pending registration requests", pendingRequests.size());

            int processed = 0;
            for (RegistrationRequest request : pendingRequests) {
                try {
                    processRequest(request, storage);
                } catch (Exception e) {
                    logger.errorf(e, "Failed to process registration request %s", request.getRequestId());
                    markRequestAsError(request, storage, e.getMessage());
                }
                processed++;
            }

            logger.infof("Processed %d registration requests", processed);
            return processed;

        } catch (Exception e) {
            logger.error("Error processing registration requests", e);
            return 0;
        }
    }

    /**
     * Process a single registration request.
     *
     * @param request Registration request to process
     * @param storage Storage provider
     */
    private void processRequest(RegistrationRequest request, RegistrationStorageProvider storage) {
        // Get realm
        RealmModel realm = session.realms().getRealm(request.getRealmId());
        if (realm == null) {
            throw new IllegalStateException("Realm not found: " + request.getRealmId());
        }

        // Check if user already exists
        if (session.users().getUserByEmail(realm, request.getEmail()) != null) {
            logger.warnf("User with email %s already exists in realm %s, marking request as error",
                    request.getEmail(), realm.getName());
            markRequestAsError(request, storage, "User already exists");
            return;
        }

        // Create user - handle race condition where user might be created concurrently
        UserModel user;
        try {
            user = createUser(realm, request);
        } catch (Exception e) {
            // Check if user was created by another concurrent request
            UserModel existingUser = session.users().getUserByEmail(realm, request.getEmail());
            if (existingUser != null) {
                logger.warnf("User with email %s was created concurrently, marking request as error",
                        request.getEmail());
                markRequestAsError(request, storage, "User already exists");
                return;
            }
            // If not a duplicate user error, rethrow
            throw e;
        }

        // Update request status to approved
        RegistrationRequest approved = RegistrationRequest.builder()
                .requestId(request.getRequestId())
                .email(request.getEmail())
                .realmId(request.getRealmId())
                .attributes(request.getAttributes())
                .status(RegistrationRequest.Status.APPROVED)
                .createdAt(request.getCreatedAt())
                .approvedAt(Instant.now())
                .expiresAt(request.getExpiresAt())
                .userId(user.getId())
                .build();

        storage.updateRegistrationRequest(approved);

        logger.infof("Approved registration request %s, created user %s (%s) in realm %s",
                request.getRequestId(), user.getUsername(), user.getId(), realm.getName());
    }

    /**
     * Create a Keycloak user from registration request.
     *
     * @param realm Realm to create user in
     * @param request Registration request
     * @return Created user
     */
    private UserModel createUser(RealmModel realm, RegistrationRequest request) {
        // Create user with email as username
        UserModel user = session.users().addUser(realm, request.getEmail());
        user.setEmail(request.getEmail());
        user.setEmailVerified(false);
        user.setEnabled(true);

        // Set attributes from registration data
        Map<String, Object> attributes = request.getAttributes();

        // Standard attributes
        if (attributes.containsKey("firstName")) {
            user.setFirstName(String.valueOf(attributes.get("firstName")));
        }
        if (attributes.containsKey("lastName")) {
            user.setLastName(String.valueOf(attributes.get("lastName")));
        }

        // Custom attributes
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            String key = entry.getKey();
            // Skip standard attributes we already handled
            if (key.equals("firstName") || key.equals("lastName")) {
                continue;
            }
            // Store as user attribute
            if (entry.getValue() != null) {
                user.setSingleAttribute(key, String.valueOf(entry.getValue()));
            }
        }

        logger.debugf("Created user %s with attributes: %s", user.getUsername(), attributes);

        return user;
    }

    /**
     * Mark a registration request as error.
     *
     * @param request Registration request
     * @param storage Storage provider
     * @param errorMessage Error message
     */
    private void markRequestAsError(RegistrationRequest request, RegistrationStorageProvider storage,
                                    String errorMessage) {
        try {
            RegistrationRequest error = RegistrationRequest.builder()
                    .requestId(request.getRequestId())
                    .email(request.getEmail())
                    .realmId(request.getRealmId())
                    .attributes(request.getAttributes())
                    .status(RegistrationRequest.Status.ERROR)
                    .createdAt(request.getCreatedAt())
                    .approvedAt(request.getApprovedAt())
                    .expiresAt(request.getExpiresAt())
                    .userId(request.getUserId())
                    .build();

            storage.updateRegistrationRequest(error);

            logger.warnf("Marked registration request %s as ERROR: %s", request.getRequestId(), errorMessage);
        } catch (Exception e) {
            logger.errorf(e, "Failed to mark request %s as error", request.getRequestId());
        }
    }
}
