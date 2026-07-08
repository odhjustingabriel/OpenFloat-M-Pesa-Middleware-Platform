package com.openfloat.mpesa.erp.repository;

import com.openfloat.mpesa.erp.entity.ERPSyncRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ERPSyncRecordRepository extends JpaRepository<ERPSyncRecord, UUID> {
    Optional<ERPSyncRecord> findByTransactionId(String transactionId);
    List<ERPSyncRecord> findBySyncStatus(String syncStatus);
}
