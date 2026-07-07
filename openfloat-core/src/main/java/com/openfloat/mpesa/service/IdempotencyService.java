package com.openfloat.mpesa.service;

import com.openfloat.mpesa.common.exception.DuplicateResourceException;
import com.openfloat.mpesa.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String REDIS_IDEMPOTENCY_PREFIX = "idempotency:";
    private final RedisTemplate<String, Object> redisTemplate;
    private final TransactionRepository transactionRepository;

    /**
     * Checks if a request with the given idempotency key has already been processed.
     * Uses Redis for fast checking and fallback to Postgres DB.
     *
     * @param key unique idempotency key
     * @throws DuplicateResourceException if already processed
     */
    public void checkIdempotency(String key) {
        if (key == null || key.isBlank()) {
            return;
        }

        String redisKey = REDIS_IDEMPOTENCY_PREFIX + key;
        
        // 1. Check Redis
        Boolean existsInRedis = redisTemplate.hasKey(redisKey);
        if (Boolean.TRUE.equals(existsInRedis)) {
            log.warn("Idempotency violation detected in Redis for key: {}", key);
            throw new DuplicateResourceException("Request already processed");
        }

        // 2. Check Postgres DB
        boolean existsInDb = transactionRepository.existsByIdempotencyKey(key);
        if (existsInDb) {
            log.warn("Idempotency violation detected in Database for key: {}", key);
            
            // Re-populate Redis cache just in case
            try {
                redisTemplate.opsForValue().set(redisKey, "PROCESSED", Duration.ofHours(24));
            } catch (Exception e) {
                log.warn("Failed to populate Redis with key: {}", key, e);
            }
            
            throw new DuplicateResourceException("Request already processed");
        }
    }

    /**
     * Saves the idempotency key to Redis to prevent concurrent processing.
     *
     * @param key unique idempotency key
     */
    public void saveIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        
        String redisKey = REDIS_IDEMPOTENCY_PREFIX + key;
        try {
            redisTemplate.opsForValue().set(redisKey, "PROCESSED", Duration.ofHours(24));
            log.debug("Idempotency key saved: {}", key);
        } catch (Exception e) {
            log.warn("Failed to save idempotency key to Redis: {}", e.getMessage());
        }
    }

    /**
     * Generates a unique idempotency key based on parameters.
     */
    public String generateIdempotencyKey(String... components) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            for (String comp : components) {
                if (comp != null) {
                    sb.append(comp).append("|");
                }
            }
            byte[] hash = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to generate idempotency key", e);
        }
    }
}
