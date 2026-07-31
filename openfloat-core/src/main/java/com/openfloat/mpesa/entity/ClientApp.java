package com.openfloat.mpesa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents an external system (website, mobile app, or service) registered
 * with the middleware to receive M-Pesa payment notifications via Webhooks.
 * <p>
 * Each ClientApp is assigned a unique {@code accountPrefix} (e.g. {@code ECOMM},
 * {@code SCH}) used to generate trackable Account References for the shared Paybill.
 * When a C2B payment arrives with a matching reference, the middleware dispatches
 * a signed Webhook to this application's {@code callbackUrl}.
 * </p>
 */
@Entity
@Table(name = "client_applications", indexes = {
        @Index(name = "idx_client_app_prefix", columnList = "account_prefix", unique = true),
        @Index(name = "idx_client_app_status", columnList = "status"),
        @Index(name = "idx_client_app_created", columnList = "created_at")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientApp {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Human-readable name of the client system.
     * E.g. "Acme E-Commerce", "XYZ School Portal".
     */
    @Column(name = "client_name", nullable = false, length = 200)
    private String clientName;

    /**
     * Short uppercase prefix used to scope all Account References for this client.
     * Must be unique across all registered clients.
     * E.g. "ECOMM", "SCH", "HOSP".
     */
    @Column(name = "account_prefix", nullable = false, unique = true, length = 20)
    private String accountPrefix;

    /**
     * HTTPS URL to which the middleware dispatches Webhook notifications on payment.
     * Must be reachable from the middleware's network.
     */
    @Column(name = "callback_url", nullable = false)
    private String callbackUrl;

    /**
     * SHA-256 hash of the issued API key for this client.
     * The raw API key is only exposed once at registration time.
     */
    @Column(name = "api_key_hash", nullable = false)
    private String apiKeyHash;

    /**
     * HMAC-SHA256 signing secret (stored as SHA-256 hash).
     * Used by the middleware to sign outbound webhook payloads so the
     * client can verify authenticity via the {@code X-OpenFloat-Signature} header.
     */
    @Column(name = "webhook_secret", nullable = false)
    private String webhookSecret;

    /**
     * Lifecycle status of this client registration.
     * {@code ACTIVE} — receiving webhooks normally.
     * {@code SUSPENDED} — registration paused; no webhooks dispatched.
     */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    /**
     * Username of the Admin or Manager who registered this client application.
     */
    @Column(name = "registered_by", nullable = false, length = 100)
    private String registeredBy;

    /** Optional internal notes or description about this client app. */
    @Column(name = "notes")
    private String notes;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
