package com.openfloat.mpesa.controller;

import com.openfloat.mpesa.common.dto.ApiResponse;
import com.openfloat.mpesa.dto.ClientAppRegistrationDto;
import com.openfloat.mpesa.dto.ClientAppRegistrationResultDto;
import com.openfloat.mpesa.dto.ClientAppResponseDto;
import com.openfloat.mpesa.service.ClientAppService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller exposing REST endpoints for managing external client applications (websites/apps).
 * Accessible to ADMIN and MANAGER roles.
 */
@RestController
@RequestMapping("/api/v1/clients")
@RequiredArgsConstructor
public class ClientAppController {

    private final ClientAppService clientAppService;

    /**
     * Register a new client application (website or app).
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<ClientAppRegistrationResultDto>> registerClient(
            @Valid @RequestBody ClientAppRegistrationDto dto,
            Authentication authentication) {

        String registeredBy = authentication != null ? authentication.getName() : "ADMIN";
        ClientAppRegistrationResultDto result = clientAppService.registerClientApp(dto, registeredBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result, "Client application registered successfully"));
    }

    /**
     * List all registered client applications.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<ClientAppResponseDto>>> listClients() {
        return ResponseEntity.ok(ApiResponse.success(clientAppService.getAllClientApps()));
    }

    /**
     * Get details of a single client application.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<ClientAppResponseDto>> getClient(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(clientAppService.getClientAppById(id)));
    }

    /**
     * Update client application lifecycle status (ACTIVE or SUSPENDED).
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<ClientAppResponseDto>> updateStatus(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {

        String status = body.get("status");
        return ResponseEntity.ok(ApiResponse.success(clientAppService.updateClientStatus(id, status)));
    }

    /**
     * Update registered webhook callback URL for a client application.
     */
    @PutMapping("/{id}/callback-url")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<ClientAppResponseDto>> updateCallbackUrl(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {

        String callbackUrl = body.get("callbackUrl");
        return ResponseEntity.ok(ApiResponse.success(clientAppService.updateCallbackUrl(id, callbackUrl)));
    }
}
