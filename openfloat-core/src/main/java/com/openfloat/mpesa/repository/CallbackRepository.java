package com.openfloat.mpesa.repository;

import com.openfloat.mpesa.entity.Callback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for Callback entity.
 */
@Repository
public interface CallbackRepository extends JpaRepository<Callback, UUID> {

    List<Callback> findByTransactionId(UUID transactionId);

    List<Callback> findByCallbackType(String callbackType);

    boolean existsByTransactionIdAndCallbackType(UUID transactionId, String callbackType);
}
