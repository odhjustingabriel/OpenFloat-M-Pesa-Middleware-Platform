package com.openfloat.mpesa.auth.repository;

import com.openfloat.mpesa.auth.entity.ApiClient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiClientRepository extends JpaRepository<ApiClient, UUID> {
    Optional<ApiClient> findByClientId(String clientId);
    boolean existsByClientId(String clientId);
}
