package com.openfloat.mpesa.service;

import com.openfloat.mpesa.common.exception.ResourceNotFoundException;
import com.openfloat.mpesa.dto.AccountRefGenerateRequestDto;
import com.openfloat.mpesa.dto.AccountRefResponseDto;
import com.openfloat.mpesa.entity.AccountReferenceMapping;
import com.openfloat.mpesa.entity.ClientApp;
import com.openfloat.mpesa.repository.AccountReferenceMappingRepository;
import com.openfloat.mpesa.repository.ClientAppRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for generating, storing, and managing dynamic Account References.
 * <p>
 * Generates unique references formatted as {@code {PREFIX}-{CODE}} (e.g. {@code ECOMM-8X92K4})
 * linked to a specific {@link ClientApp} and target callback URL.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class AccountReferenceService {

    private final AccountReferenceMappingRepository mappingRepository;
    private final ClientAppRepository clientAppRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    private static final String ALPHANUMERIC_CHARSET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"; // excluded 0,1,O,I to avoid customer entry confusion

    /**
     * Generates a unique, trackable Account Reference for a registered client application.
     */
    @Transactional
    public AccountRefResponseDto generateReference(AccountRefGenerateRequestDto dto) {
        String searchPrefix = dto.getAccountPrefix().toUpperCase().trim();

        ClientApp clientApp = clientAppRepository.findByAccountPrefixIgnoreCase(searchPrefix)
                .orElseThrow(() -> new ResourceNotFoundException("No active client application found with prefix: " + searchPrefix));

        if (!"ACTIVE".equalsIgnoreCase(clientApp.getStatus())) {
            throw new IllegalStateException("Client application '" + clientApp.getClientName() + "' is currently SUSPENDED");
        }

        // Determine target callback URL (override takes precedence if provided)
        String targetCallbackUrl = (dto.getCallbackUrlOverride() != null && !dto.getCallbackUrlOverride().isBlank())
                ? dto.getCallbackUrlOverride().trim()
                : clientApp.getCallbackUrl();

        // Mint unique Account Reference: {PREFIX}-{6_CHAR_CODE}
        String refCode = generateUniqueRefCode(clientApp.getAccountPrefix());

        int ttl = (dto.getTtlMinutes() != null && dto.getTtlMinutes() > 0) ? dto.getTtlMinutes() : 1440; // default 24h
        Instant expiresAt = Instant.now().plus(ttl, ChronoUnit.MINUTES);

        AccountReferenceMapping mapping = AccountReferenceMapping.builder()
                .accountReference(refCode)
                .clientApp(clientApp)
                .callbackUrl(targetCallbackUrl)
                .requestedAmount(dto.getRequestedAmount())
                .currency("KES")
                .description(dto.getDescription())
                .status("PENDING")
                .expiresAt(expiresAt)
                .build();

        AccountReferenceMapping saved = mappingRepository.save(mapping);
        log.info("Generated AccountReference='{}' for ClientApp='{}' expiresAt={}",
                saved.getAccountReference(), clientApp.getClientName(), saved.getExpiresAt());

        return mapToResponseDto(saved);
    }

    /** Find reference mapping by exact reference string (e.g. ECOMM-8X92K4). */
    @Transactional(readOnly = true)
    public Optional<AccountReferenceMapping> findByAccountReference(String reference) {
        if (reference == null || reference.isBlank()) return Optional.empty();
        return mappingRepository.findByAccountReference(reference.trim().toUpperCase());
    }

    /** Retrieve all references for a specific client application. */
    @Transactional(readOnly = true)
    public List<AccountRefResponseDto> getReferencesByClientAppId(UUID clientAppId) {
        return mappingRepository.findAllByClientAppId(clientAppId).stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
    }

    /** Expire all stale PENDING references whose TTL has passed. */
    @Transactional
    public int expireStaleReferences() {
        List<AccountReferenceMapping> expired = mappingRepository.findExpiredPendingReferences(Instant.now());
        for (AccountReferenceMapping mapping : expired) {
            mapping.setStatus("EXPIRED");
        }
        if (!expired.isEmpty()) {
            mappingRepository.saveAll(expired);
            log.info("Expired {} stale pending account references", expired.size());
        }
        return expired.size();
    }

    private String generateUniqueRefCode(String prefix) {
        String code;
        int attempts = 0;
        do {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                sb.append(ALPHANUMERIC_CHARSET.charAt(secureRandom.nextInt(ALPHANUMERIC_CHARSET.length())));
            }
            code = prefix + "-" + sb.toString();
            attempts++;
            if (attempts > 20) {
                // Expand code length if collision rate is high
                code = prefix + "-" + System.currentTimeMillis() % 1000000;
                break;
            }
        } while (mappingRepository.existsByAccountReference(code));
        return code;
    }

    private AccountRefResponseDto mapToResponseDto(AccountReferenceMapping m) {
        return AccountRefResponseDto.builder()
                .id(m.getId())
                .accountReference(m.getAccountReference())
                .clientAppId(m.getClientApp().getId())
                .clientAppName(m.getClientApp().getClientName())
                .accountPrefix(m.getClientApp().getAccountPrefix())
                .callbackUrl(m.getCallbackUrl())
                .requestedAmount(m.getRequestedAmount())
                .currency(m.getCurrency())
                .description(m.getDescription())
                .status(m.getStatus())
                .expiresAt(m.getExpiresAt())
                .paidAt(m.getPaidAt())
                .transactionId(m.getTransaction() != null ? m.getTransaction().getId().toString() : null)
                .createdAt(m.getCreatedAt())
                .build();
    }
}
