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
                .orElseGet(() -> {
                    if ("openfloat-staff-portal".equalsIgnoreCase(clientId)) {
                        log.info("Returning default PKCE RegisteredClient configuration for 'openfloat-staff-portal'");
                        return createStaffPortalClient();
                    }
                    return null;
                });
    }

    private RegisteredClient createStaffPortalClient() {
        return RegisteredClient.withId("openfloat-staff-portal-id")
                .clientId("openfloat-staff-portal")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .redirectUri("http://localhost:5173/oauth/callback")
                .redirectUri("http://localhost:5173/oauth2/callback")
                .redirectUri("http://localhost:8080/login/oauth2/code/gateway")
                .redirectUri("http://localhost:3000/oauth/callback")
                .redirectUri("http://localhost:3000/login/callback")
                .scope("openid")
                .scope("profile")
                .scope("read")
                .scope("write")
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofHours(2))
                        .refreshTokenTimeToLive(Duration.ofDays(7))
                        .build())
                .build();
    }

    private RegisteredClient toRegisteredClient(ApiClient client) {
        if (!"ACTIVE".equalsIgnoreCase(client.getStatus())) {
            log.warn("Client [{}] is disabled/inactive", client.getClientId());
            return null;
        }

        return RegisteredClient.withId(client.getId().toString())
                .clientId(client.getClientId())
                .clientSecret(client.getClientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://localhost:5173/oauth/callback")
                .redirectUri("http://localhost:5173/oauth2/callback")
                .redirectUri("http://localhost:8080/login/oauth2/code/gateway")
                .redirectUri("http://localhost:3000/oauth/callback")
                .redirectUri("http://localhost:3000/login/callback")
                .scope("openid")
                .scope("profile")
                .scope("read")
                .scope("write")
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(false)
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofHours(2))
                        .refreshTokenTimeToLive(Duration.ofDays(7))
                        .build())
                .build();
    }
}
