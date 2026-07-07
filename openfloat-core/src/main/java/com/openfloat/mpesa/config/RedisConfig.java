package com.openfloat.mpesa.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis configuration for caching and rate limiting.
 * <p>
 * Used for:
 * <ul>
 *   <li>Daraja OAuth token caching</li>
 *   <li>Per-client rate limiting counters</li>
 *   <li>Idempotency key storage</li>
 * </ul>
 * </p>
 */
@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    /**
     * Default TTL for cached items.
     */
    public static final Duration DEFAULT_CACHE_TTL = Duration.ofMinutes(30);

    /**
     * TTL for Daraja OAuth tokens (slightly less than actual expiry).
     */
    public static final Duration DARAJA_TOKEN_TTL = Duration.ofMinutes(55);

    /**
     * TTL for rate limiting window.
     */
    public static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(1);
}
