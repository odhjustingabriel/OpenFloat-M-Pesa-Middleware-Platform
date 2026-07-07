package com.openfloat.mpesa.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Tamper-evident audit log entry.
 * <p>
 * Each entry contains a SHA-256 hash computed from the previous entry's hash
 * and the current entry's data, forming an unbroken chain.
 * Any modification to a historical entry breaks the chain and is detectable.
 * </p>
 */
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_username", columnList = "username"),
        @Index(name = "idx_audit_action", columnList = "action"),
        @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
        @Index(name = "idx_audit_resource", columnList = "resource")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "username", nullable = false, length = 100)
    private String username;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "resource", length = 255)
    private String resource;

    @Column(name = "resource_id")
    private String resourceId;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "timestamp", nullable = false)
    @Builder.Default
    private Instant timestamp = Instant.now();

    /**
     * SHA-256 hash of (previous_hash | current_entry_data).
     * Forms a tamper-evident chain of audit records.
     */
    @Column(name = "hash", nullable = false, length = 64)
    private String hash;
}
