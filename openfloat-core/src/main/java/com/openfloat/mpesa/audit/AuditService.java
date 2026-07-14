package com.openfloat.mpesa.audit;

import com.openfloat.mpesa.common.util.HashUtils;
import com.openfloat.mpesa.entity.AuditLog;
import com.openfloat.mpesa.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class AuditService {

    private final AuditLogRepository auditLogRepository;


    /**
     * Records a secure, hash-chained audit log entry.
     * Transaction propagation is set to REQUIRES_NEW to log even if the target business method fails.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public synchronized void log(String username, AuditEventType action, String resource, String resourceId, String details, String ipAddress) {
        log.debug("Creating audit log for user: {}, action: {}", username, action);

        // 1. Fetch latest audit log for previous hash with lock to serialize concurrent logging
        Optional<AuditLog> latestLog = auditLogRepository.findLatestForUpdate();
        String previousHash = latestLog.map(entry -> entry.getHash()).orElse(HashUtils.GENESIS_HASH);

        // 2. Build current entry data string
        Instant timestamp = Instant.now();
        String entryData = String.format("%s|%s|%s|%s|%s|%s|%d",
                username,
                action.name(),
                resource != null ? resource : "",
                resourceId != null ? resourceId : "",
                details != null ? details : "",
                ipAddress != null ? ipAddress : "",
                timestamp.toEpochMilli()
        );

        // 3. Compute chained hash
        String chainedHash = HashUtils.chainedHash(previousHash, entryData);

        AuditLog auditEntry = AuditLog.builder()
                .username(username)
                .action(action.name())
                .resource(resource)
                .resourceId(resourceId)
                .details(details)
                .ipAddress(ipAddress)
                .timestamp(timestamp)
                .hash(chainedHash)
                .build();

        auditEntry = auditLogRepository.save(auditEntry);

        // Log formatted structure for ELK / Splunk collectors
        log.info("AUDIT_LOG_JSON: {\"id\":\"{}\", \"username\":\"{}\", \"action\":\"{}\", \"resource\":\"{}\", \"resourceId\":\"{}\", \"ip\":\"{}\", \"timestamp\":\"{}\", \"hash\":\"{}\"}",
                auditEntry.getId(), username, action.name(), resource, resourceId, ipAddress, timestamp, chainedHash);
    }

    /**
     * Verifies the tamper-evident chain of all audit log records.
     * Returns true if the chain is unbroken and valid, false if tampering is detected.
     */
    @Transactional(readOnly = true)
    public boolean verifyChainIntegrity() {
        log.info("Verifying audit log chain integrity...");
        List<AuditLog> allLogs = auditLogRepository.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "timestamp"));
        if (allLogs.isEmpty()) {
            return true;
        }

        String expectedPreviousHash = HashUtils.GENESIS_HASH;
        for (AuditLog entry : allLogs) {
            String entryData = String.format("%s|%s|%s|%s|%s|%s|%d",
                    entry.getUsername(),
                    entry.getAction(),
                    entry.getResource() != null ? entry.getResource() : "",
                    entry.getResourceId() != null ? entry.getResourceId() : "",
                    entry.getDetails() != null ? entry.getDetails() : "",
                    entry.getIpAddress() != null ? entry.getIpAddress() : "",
                    entry.getTimestamp().toEpochMilli()
            );

            String calculatedHash = HashUtils.chainedHash(expectedPreviousHash, entryData);
            if (!calculatedHash.equals(entry.getHash())) {
                log.error("AUDIT CHAIN CORRUPTED AT RECORD: {}. Calculate: {}, Saved: {}",
                        entry.getId(), calculatedHash, entry.getHash());
                return false;
            }
            expectedPreviousHash = entry.getHash();
        }

        log.info("Audit log chain verified successfully. Unbroken.");
        return true;
    }
}
