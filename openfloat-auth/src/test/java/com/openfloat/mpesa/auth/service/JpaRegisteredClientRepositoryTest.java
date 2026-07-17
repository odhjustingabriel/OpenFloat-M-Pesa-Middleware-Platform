package com.openfloat.mpesa.auth.service;

import com.openfloat.mpesa.auth.entity.ApiClient;
import com.openfloat.mpesa.auth.repository.ApiClientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link JpaRegisteredClientRepository}.
 * <p>
 * Verifies that the JPA-to-OAuth2 client mapping produces a correctly configured
 * {@link RegisteredClient} with the expected grant types, authentication methods,
 * scopes, and token settings.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class JpaRegisteredClientRepositoryTest {

    @Mock private ApiClientRepository apiClientRepository;

    private JpaRegisteredClientRepository repository;

    @BeforeEach
    void setUp() {
        repository = new JpaRegisteredClientRepository(apiClientRepository);
    }

    @Test
    void findByClientIdReturnsCorrectlyMappedRegisteredClientForActiveClient() {
        ApiClient active = activeApiClient();
        when(apiClientRepository.findByClientId("openfloat-core")).thenReturn(Optional.of(active));

        RegisteredClient result = repository.findByClientId("openfloat-core");

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(active.getId().toString());
        assertThat(result.getClientId()).isEqualTo("openfloat-core");
        assertThat(result.getClientSecret()).isEqualTo("{bcrypt}$2a$10$hashedSecret");

        // Grant types
        assertThat(result.getAuthorizationGrantTypes())
                .contains(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .contains(AuthorizationGrantType.AUTHORIZATION_CODE)
                .contains(AuthorizationGrantType.REFRESH_TOKEN);

        // Authentication methods
        assertThat(result.getClientAuthenticationMethods())
                .contains(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .contains(ClientAuthenticationMethod.CLIENT_SECRET_POST);

        // Scopes
        assertThat(result.getScopes()).containsExactlyInAnyOrder("read", "write");

        // Token settings
        assertThat(result.getTokenSettings().getAccessTokenTimeToLive()).isEqualTo(Duration.ofHours(2));
        assertThat(result.getTokenSettings().getRefreshTokenTimeToLive()).isEqualTo(Duration.ofDays(7));
    }

    @Test
    void findByClientIdReturnsNullForInactiveClient() {
        ApiClient inactive = activeApiClient();
        inactive.setStatus("BLOCKED");
        when(apiClientRepository.findByClientId("openfloat-core")).thenReturn(Optional.of(inactive));

        RegisteredClient result = repository.findByClientId("openfloat-core");

        assertThat(result).isNull();
    }

    @Test
    void findByClientIdReturnsNullWhenClientNotFound() {
        when(apiClientRepository.findByClientId("nonexistent")).thenReturn(Optional.empty());

        RegisteredClient result = repository.findByClientId("nonexistent");

        assertThat(result).isNull();
    }

    @Test
    void findByIdReturnsMappedClientForValidUUID() {
        ApiClient client = activeApiClient();
        when(apiClientRepository.findById(client.getId())).thenReturn(Optional.of(client));

        RegisteredClient result = repository.findById(client.getId().toString());

        assertThat(result).isNotNull();
        assertThat(result.getClientId()).isEqualTo("openfloat-core");
    }

    private ApiClient activeApiClient() {
        UUID id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        return ApiClient.builder()
                .id(id)
                .clientId("openfloat-core")
                .clientSecret("{bcrypt}$2a$10$hashedSecret")
                .clientName("OpenFloat Core Service")
                .status("ACTIVE")
                .rateLimit(100)
                .createdAt(Instant.now())
                .build();
    }
}
