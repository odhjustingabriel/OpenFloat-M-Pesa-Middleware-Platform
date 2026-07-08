package com.openfloat.mpesa.security;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
public class RateLimitConfig {

    @Value("${openfloat.rate-limit.requests-per-minute:100}")
    private int requestsPerMinute;

    @Value("${openfloat.rate-limit.window-seconds:60}")
    private int windowSeconds;
}
