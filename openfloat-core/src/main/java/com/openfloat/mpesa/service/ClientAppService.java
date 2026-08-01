package com.openfloat.mpesa.service;

import com.openfloat.mpesa.common.exception.ResourceNotFoundException;
import com.openfloat.mpesa.common.util.HashUtils;
import com.openfloat.mpesa.dto.ClientAppRegistrationDto;
import com.openfloat.mpesa.dto.ClientAppRegistrationResultDto;
import com.openfloat.mpesa.dto.ClientAppResponseDto;
import com.openfloat.mpesa.entity.ClientApp;
import com.openfloat.mpesa.repository.ClientAppRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service managing multi-tenant client application registrations,
 * API key generation, callback URL updates, and client lifecycle statuses.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class ClientAppService {

    private final ClientAppRepository clientAppRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Registers a new external client application (website, mobile app).
     * Mints a unique API key and Webhook HMAC secret, returning them ONCE in the response.
     */
    @Transactional
    public ClientAppRegistrationResultDto registerClientApp(ClientAppRegistrationDto dto, String registeredBy) {
        String uppercasePrefix = dto.getAccountPrefix().toUpperCase().trim();
        if (clientAppRepository.existsByAccountPrefixIgnoreCase(uppercasePrefix)) {
            throw new IllegalArgumentException("Account prefix '" + uppercasePrefix + "' is already registered to another client application");
        }

        // Generate raw API Key (e.g. of_live_...) and raw Webhook Secret
        String rawApiKey = "of_live_" + generateRandomToken(32);
        String rawWebhookSecret = "whsec_" + generateRandomToken(32);

        String apiKeyHash = HashUtils.sha256(rawApiKey);
        String webhookSecretHash = HashUtils.sha256(rawWebhookSecret);

        ClientApp clientApp = ClientApp.builder()
                .clientName(dto.getClientName().trim())
                .accountPrefix(uppercasePrefix)
                .callbackUrl(dto.getCallbackUrl().trim())
                .apiKeyHash(apiKeyHash)
                .webhookSecret(webhookSecretHash)
                .status("ACTIVE")
                .registeredBy(registeredBy != null ? registeredBy : "SYSTEM")
                .notes(dto.getNotes())
                .build();

        ClientApp saved = clientAppRepository.save(clientApp);
        log.info("Registered new ClientApp ID={} name='{}' prefix='{}' by user='{}'",
                saved.getId(), saved.getClientName(), saved.getAccountPrefix(), registeredBy);

        return ClientAppRegistrationResultDto.builder()
                .id(saved.getId())
                .clientName(saved.getClientName())
                .accountPrefix(saved.getAccountPrefix())
                .callbackUrl(saved.getCallbackUrl())
                .status(saved.getStatus())
                .registeredBy(saved.getRegisteredBy())
                .notes(saved.getNotes())
                .createdAt(saved.getCreatedAt())
                .apiKey(rawApiKey)
                .webhookSecret(rawWebhookSecret)
                .build();
    }

    /** Retrieve all registered client applications ordered by creation date descending. */
    @Transactional(readOnly = true)
    public List<ClientAppResponseDto> getAllClientApps() {
        return clientAppRepository.findAllOrderByCreatedAtDesc().stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
    }

    /** Retrieve a single client application by ID. */
    @Transactional(readOnly = true)
    public ClientAppResponseDto getClientAppById(UUID id) {
        ClientApp app = clientAppRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Client application not found with ID: " + id));
        return mapToResponseDto(app);
    }

    /** Update status of a client application (e.g. ACTIVE -> SUSPENDED). */
    @Transactional
    public ClientAppResponseDto updateClientStatus(UUID id, String status) {
        ClientApp app = clientAppRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Client application not found with ID: " + id));

        String upperStatus = status.toUpperCase().trim();
        if (!upperStatus.equals("ACTIVE") && !upperStatus.equals("SUSPENDED")) {
            throw new IllegalArgumentException("Invalid status: " + status + ". Allowed values: ACTIVE, SUSPENDED");
        }

        app.setStatus(upperStatus);
        ClientApp updated = clientAppRepository.save(app);
        log.info("Updated ClientApp ID={} status to '{}'", id, upperStatus);
        return mapToResponseDto(updated);
    }

    /** Update the callback URL of a client application. */
    @Transactional
    public ClientAppResponseDto updateCallbackUrl(UUID id, String newCallbackUrl) {
        ClientApp app = clientAppRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Client application not found with ID: " + id));

        if (newCallbackUrl == null || !newCallbackUrl.matches("^https?://.*$")) {
            throw new IllegalArgumentException("Callback URL must be a valid HTTP or HTTPS endpoint");
        }

        app.setCallbackUrl(newCallbackUrl.trim());
        ClientApp updated = clientAppRepository.save(app);
        log.info("Updated ClientApp ID={} callback URL to '{}'", id, newCallbackUrl);
        return mapToResponseDto(updated);
    }

    private ClientAppResponseDto mapToResponseDto(ClientApp app) {
        return ClientAppResponseDto.builder()
                .id(app.getId())
                .clientName(app.getClientName())
                .accountPrefix(app.getAccountPrefix())
                .callbackUrl(app.getCallbackUrl())
                .status(app.getStatus())
                .registeredBy(app.getRegisteredBy())
                .notes(app.getNotes())
                .createdAt(app.getCreatedAt())
                .updatedAt(app.getUpdatedAt())
                .build();
    }

    private String generateRandomToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
