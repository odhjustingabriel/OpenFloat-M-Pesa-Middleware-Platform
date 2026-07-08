package com.openfloat.mpesa.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openfloat.mpesa.common.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitConfig rateLimitConfig;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String REDIS_RATE_LIMIT_PREFIX = "ratelimit:";
    private static final Pattern BEARER_PATTERN = Pattern.compile("^Bearer\\s+(.*)$", Pattern.CASE_INSENSITIVE);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String clientId = resolveClientId(request);
        String redisKey = REDIS_RATE_LIMIT_PREFIX + clientId + ":" + (Instant.now().getEpochSecond() / rateLimitConfig.getWindowSeconds());

        try {
            Long count = redisTemplate.opsForValue().increment(redisKey);
            if (count != null && count == 1) {
                redisTemplate.expire(redisKey, Duration.ofSeconds(rateLimitConfig.getWindowSeconds() * 2));
            }

            if (count != null && count > rateLimitConfig.getRequestsPerMinute()) {
                log.warn("Rate limit exceeded for client: {}. Count: {}", clientId, count);
                writeErrorResponse(request, response, clientId);
                return;
            }
        } catch (Exception e) {
            // Log warning but let request pass so Redis failure doesn't block the API
            log.warn("Redis rate limit check failed, allowing request to bypass limit: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String resolveClientId(HttpServletRequest request) {
        // 1. Try to extract client ID from Authorization Header (Bearer JWT token)
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && !authHeader.isBlank()) {
            Matcher matcher = BEARER_PATTERN.matcher(authHeader);
            if (matcher.matches()) {
                // If it's a JWT, use the hash of the token as identifier or try to parse
                // Since this filter runs before auth validation, we will just use token hash
                return "token:" + com.openfloat.mpesa.common.util.HashUtils.sha256(matcher.group(1));
            }
        }

        // 2. Fallback to client IP address
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return "ip:" + ip;
    }

    private void writeErrorResponse(HttpServletRequest request, HttpServletResponse response, String clientId)
            throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .error("TOO_MANY_REQUESTS")
                .message(String.format("Rate limit exceeded for client: %s. Maximum %d requests per %d seconds.",
                        clientId, rateLimitConfig.getRequestsPerMinute(), rateLimitConfig.getWindowSeconds()))
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build();

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
