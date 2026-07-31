package com.openfloat.mpesa.repository;

import com.openfloat.mpesa.entity.ClientApp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link ClientApp} — registered external client systems.
 */
@Repository
public interface ClientAppRepository extends JpaRepository<ClientApp, UUID> {

    /** Find a client app by its unique account prefix (case-insensitive). */
    Optional<ClientApp> findByAccountPrefixIgnoreCase(String accountPrefix);

    /** Find a client app by its hashed API key. */
    Optional<ClientApp> findByApiKeyHash(String apiKeyHash);

    /** Check whether an account prefix is already taken. */
    boolean existsByAccountPrefixIgnoreCase(String accountPrefix);

    /** Retrieve all active client apps. */
    List<ClientApp> findAllByStatus(String status);

    /** Retrieve all client apps registered by a specific admin or manager. */
    List<ClientApp> findAllByRegisteredBy(String registeredBy);

    /** Full-text-friendly listing ordered by registration date descending. */
    @Query("SELECT c FROM ClientApp c ORDER BY c.createdAt DESC")
    List<ClientApp> findAllOrderByCreatedAtDesc();
}
