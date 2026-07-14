package com.openfloat.mpesa.auth.service;

import com.openfloat.mpesa.auth.entity.ApiClient;
import com.openfloat.mpesa.auth.repository.ApiClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class JpaRegisteredClientRepository implements RegisteredClientRepository {

    private final ApiClientRepository apiClientRepository;

    @Override
    public void save(RegisteredClient registeredClient) {
        log.warn("Saving clients via JpaRegisteredClientRepository is not supported directly. Please use User/Client management endpoints.");
        throw new UnsupportedOperationException("Direct client save is not supported in this context.");
    }

    @Override
    public RegisteredClient findById(String id) {
        log.debug("Finding registered client by internal ID: {}", id);
        return apiClientRepository.findById(UUID.fromString(id))
                .map(this::toRegisteredClient)
                .orElse(null);
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        log.debug("Finding registered client by client ID: {}", clientId);
        return apiClientRepository.findByClientId(clientId)
                .map(this::toRegisteredClient)
                .orElse(null);
    }

    private RegisteredClient toRegisteredClient(ApiClient client) {
        if (!"ACTIVE".equalsIgnoreCase(client.getStatus())) {
            log.warn("Client [{}] is disabled/inactive", client.getClientId());
            return null;
        }

        return RegisteredClient.withId(client.getId().toString())
                .clientId(client.getClientId())
                .clientSecret(client.getClientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                // For staff-portal web clients, we might support password / authorization_code flow
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://localhost:8080/login/oauth2/code/gateway")
                .redirectUri("http://localhost:3000/login/callback")
                .scope("read")
                .scope("write")
                .clientSettings(ClientSettings.builder()
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofHours(2))
                        .refreshTokenTimeToLive(Duration.ofDays(7))
                        .build())
                .build();
    }
}
