package org.apifocal.auth41.plugin.registration.storage;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.models.KeycloakSession;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for fail-fast behavior when initialization fails.
 */
class JpaRegistrationStorageProviderFactoryFailFastTest {

    @Test
    void shouldFailFastWhenInitializationFailed() throws Exception {
        // Given - simulate initialization failure
        JpaRegistrationStorageProviderFactory factory = new JpaRegistrationStorageProviderFactory();
        setInitializationState(false, "Database connection failed");

        KeycloakSession session = mock(KeycloakSession.class);

        // When/Then - creating a provider should fail immediately with clear error
        assertThatThrownBy(() -> factory.create(session))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Auth41 Registration Storage Provider is not available")
                .hasMessageContaining("Database connection failed");
    }

    @Test
    void shouldSucceedWhenInitializationSucceeded() throws Exception {
        // Given - simulate successful initialization
        JpaRegistrationStorageProviderFactory factory = new JpaRegistrationStorageProviderFactory();
        setInitializationState(true, null);

        KeycloakSession session = mock(KeycloakSession.class);
        JpaConnectionProvider jpaProvider = mock(JpaConnectionProvider.class);
        EntityManager entityManager = mock(EntityManager.class);
        when(session.getProvider(JpaConnectionProvider.class)).thenReturn(jpaProvider);
        when(jpaProvider.getEntityManager()).thenReturn(entityManager);

        // When - creating a provider should succeed
        RegistrationStorageProvider provider = factory.create(session);

        // Then
        assertThat(provider).isNotNull();
        assertThat(provider).isInstanceOf(JpaRegistrationStorageProvider.class);
    }

    @Test
    void shouldAllowCreationWhenInitializationNotYetAttempted() throws Exception {
        // Given - initialization not yet attempted (null state)
        JpaRegistrationStorageProviderFactory factory = new JpaRegistrationStorageProviderFactory();
        setInitializationState(null, null);

        KeycloakSession session = mock(KeycloakSession.class);
        JpaConnectionProvider jpaProvider = mock(JpaConnectionProvider.class);
        EntityManager entityManager = mock(EntityManager.class);
        when(session.getProvider(JpaConnectionProvider.class)).thenReturn(jpaProvider);
        when(jpaProvider.getEntityManager()).thenReturn(entityManager);

        // When - creating a provider should succeed (initialization happens in postInit)
        RegistrationStorageProvider provider = factory.create(session);

        // Then
        assertThat(provider).isNotNull();
    }

    @Test
    void shouldExposeInitializationState() throws Exception {
        // Given
        setInitializationState(false, "Test error message");

        // When/Then
        assertThat(JpaRegistrationStorageProviderFactory.isInitializationSuccessful()).isFalse();
        assertThat(JpaRegistrationStorageProviderFactory.getInitializationError())
                .isEqualTo("Test error message");
    }

    @Test
    void shouldExposeSuccessfulInitializationState() throws Exception {
        // Given
        setInitializationState(true, null);

        // When/Then
        assertThat(JpaRegistrationStorageProviderFactory.isInitializationSuccessful()).isTrue();
        assertThat(JpaRegistrationStorageProviderFactory.getInitializationError()).isNull();
    }

    /**
     * Helper method to set initialization state via reflection for testing.
     */
    private void setInitializationState(Boolean success, String error) throws Exception {
        Field successField = JpaRegistrationStorageProviderFactory.class
                .getDeclaredField("initializationSuccessful");
        successField.setAccessible(true);
        successField.set(null, success);

        Field errorField = JpaRegistrationStorageProviderFactory.class
                .getDeclaredField("initializationError");
        errorField.setAccessible(true);
        errorField.set(null, error);
    }
}
