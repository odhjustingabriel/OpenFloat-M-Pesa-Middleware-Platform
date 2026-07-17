package com.openfloat.mpesa.audit;

import com.openfloat.mpesa.common.util.HashUtils;
import com.openfloat.mpesa.entity.AuditLog;
import com.openfloat.mpesa.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests the hash-chain integrity mechanism in {@link AuditService}.
 * <p>
 * Verifies that:
 * <ul>
 *   <li>The first log entry chains from {@link HashUtils#GENESIS_HASH}</li>
 *   <li>Subsequent entries chain from the previous entry's hash</li>
 *   <li>3 sequential entries form a valid, verifiable chain</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class AuditServiceTest {

    @Mock private AuditLogRepository auditLogRepository;

    private AuditService auditService;

    @BeforeEach
    void setUp() {
        auditService = new AuditService(auditLogRepository);
    }

    @Test
    void firstLogEntryChainsFromGenesisHash() {
        // No previous entry exists
        when(auditLogRepository.findLatestForUpdate()).thenReturn(Optional.empty());
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> {
            AuditLog log = inv.getArgument(0);
            log.setId(UUID.randomUUID());
            return log;
        });

        auditService.log("admin", AuditEventType.LOGIN_SUCCESS, "auth", null, "Login OK", "127.0.0.1");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();

        // Recompute expected hash to verify chain from genesis
        String entryData = buildEntryData(saved);
        String expectedHash = HashUtils.chainedHash(HashUtils.GENESIS_HASH, entryData);
        assertThat(saved.getHash()).isEqualTo(expectedHash);
    }

    @Test
    void secondLogEntryChainsFromPreviousHash() {
        // Simulate an existing log with a known hash
        String previousHash = "aaaa1111bbbb2222cccc3333dddd4444eeee5555ffff6666aaaa7777bbbb8888";
        AuditLog previous = AuditLog.builder()
                .id(UUID.randomUUID())
                .hash(previousHash)
                .build();

        when(auditLogRepository.findLatestForUpdate()).thenReturn(Optional.of(previous));
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> {
            AuditLog log = inv.getArgument(0);
            log.setId(UUID.randomUUID());
            return log;
        });

        auditService.log("operator", AuditEventType.API_CALL, "payments", "TX-1", "STK Push", "10.0.0.1");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();

        // Verify it chains from the previous hash, not genesis
        String entryData = buildEntryData(saved);
        String expectedHash = HashUtils.chainedHash(previousHash, entryData);
        assertThat(saved.getHash()).isEqualTo(expectedHash);
    }

    @Test
    void threeSequentialEntriesFormValidChain() {
        // Entry 1: chains from GENESIS
        when(auditLogRepository.findLatestForUpdate()).thenReturn(Optional.empty());
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> {
            AuditLog log = inv.getArgument(0);
            log.setId(UUID.randomUUID());
            return log;
        });

        auditService.log("user1", AuditEventType.LOGIN_SUCCESS, "auth", null, "Login", "1.1.1.1");

        ArgumentCaptor<AuditLog> captor1 = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(captor1.capture());
        AuditLog entry1 = captor1.getValue();

        // Entry 2: chains from entry1's hash
        reset(auditLogRepository);
        when(auditLogRepository.findLatestForUpdate()).thenReturn(Optional.of(entry1));
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> {
            AuditLog log = inv.getArgument(0);
            log.setId(UUID.randomUUID());
            return log;
        });

        auditService.log("user1", AuditEventType.PAYMENT_INITIATED, "payments", "TX-2", "Payment", "1.1.1.1");

        ArgumentCaptor<AuditLog> captor2 = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(captor2.capture());
        AuditLog entry2 = captor2.getValue();

        // Entry 3: chains from entry2's hash
        reset(auditLogRepository);
        when(auditLogRepository.findLatestForUpdate()).thenReturn(Optional.of(entry2));
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> {
            AuditLog log = inv.getArgument(0);
            log.setId(UUID.randomUUID());
            return log;
        });

        auditService.log("admin", AuditEventType.CONFIG_CHANGE, "settings", null, "Updated", "2.2.2.2");

        ArgumentCaptor<AuditLog> captor3 = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(captor3.capture());
        AuditLog entry3 = captor3.getValue();

        // Now verify the full chain
        String hash1 = HashUtils.chainedHash(HashUtils.GENESIS_HASH, buildEntryData(entry1));
        assertThat(entry1.getHash()).isEqualTo(hash1);

        String hash2 = HashUtils.chainedHash(entry1.getHash(), buildEntryData(entry2));
        assertThat(entry2.getHash()).isEqualTo(hash2);

        String hash3 = HashUtils.chainedHash(entry2.getHash(), buildEntryData(entry3));
        assertThat(entry3.getHash()).isEqualTo(hash3);
    }

    /**
     * Reconstructs the entry data string exactly as AuditService builds it,
     * for hash verification purposes.
     */
    private String buildEntryData(AuditLog entry) {
        return String.format("%s|%s|%s|%s|%s|%s|%d",
                entry.getUsername(),
                entry.getAction(),
                entry.getResource() != null ? entry.getResource() : "",
                entry.getResourceId() != null ? entry.getResourceId() : "",
                entry.getDetails() != null ? entry.getDetails() : "",
                entry.getIpAddress() != null ? entry.getIpAddress() : "",
                entry.getTimestamp().toEpochMilli()
        );
    }
}
