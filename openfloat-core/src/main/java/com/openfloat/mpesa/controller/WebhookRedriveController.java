package com.openfloat.mpesa.controller;

import com.openfloat.mpesa.dto.WebhookDeliveryLogDto;
import com.openfloat.mpesa.entity.WebhookDeliveryLog;
import com.openfloat.mpesa.repository.WebhookDeliveryLogRepository;
import com.openfloat.mpesa.service.WebhookDispatcherService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Controller for viewing outbound Webhook delivery logs and re-triggering (redriving) failed dispatches.
 * Accessible to ADMIN and MANAGER roles.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class WebhookRedriveController {

    private final WebhookDeliveryLogRepository logRepository;
    private final WebhookDispatcherService dispatcherService;

    /**
     * List failed webhook delivery attempts across all clients (Admin/Manager view).
     */
    @GetMapping("/failed")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<WebhookDeliveryLogDto>> getFailedWebhooks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Page<WebhookDeliveryLog> logs = logRepository.findAllFailed(PageRequest.of(page, size));
        return ResponseEntity.ok(logs.stream().map(this::mapToDto).collect(Collectors.toList()));
    }

    /**
     * List all webhook delivery logs for a specific client application.
     */
    @GetMapping("/client/{clientAppId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<WebhookDeliveryLogDto>> getLogsByClientApp(
            @PathVariable UUID clientAppId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Page<WebhookDeliveryLog> logs = logRepository.findAllByClientAppIdOrderByCreatedAtDesc(clientAppId, PageRequest.of(page, size));
        return ResponseEntity.ok(logs.stream().map(this::mapToDto).collect(Collectors.toList()));
    }

    /**
     * Manually redrive (re-trigger) a failed webhook dispatch.
     */
    @PostMapping("/{logId}/redrive")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<WebhookDeliveryLogDto> redriveWebhook(@PathVariable UUID logId) {
        WebhookDeliveryLog newLog = dispatcherService.redriveWebhook(logId);
        return ResponseEntity.ok(mapToDto(newLog));
    }

    private WebhookDeliveryLogDto mapToDto(WebhookDeliveryLog log) {
        return WebhookDeliveryLogDto.builder()
                .id(log.getId())
                .transactionId(log.getTransaction() != null ? log.getTransaction().getId() : null)
                .clientAppId(log.getClientApp().getId())
                .clientName(log.getClientApp().getClientName())
                .accountReference(log.getAccountReference())
                .targetUrl(log.getTargetUrl())
                .httpStatus(log.getHttpStatus())
                .requestPayload(log.getRequestPayload())
                .responseBody(log.getResponseBody())
                .errorMessage(log.getErrorMessage())
                .attemptNumber(log.getAttemptNumber())
                .success(log.isSuccess())
                .deliveredAt(log.getDeliveredAt())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
