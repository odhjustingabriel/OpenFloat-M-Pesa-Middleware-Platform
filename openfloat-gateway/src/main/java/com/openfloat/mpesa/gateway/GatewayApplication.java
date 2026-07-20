package com.openfloat.mpesa.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Mono;

@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            // Resolve rate limit key by remote IP address
            String ipAddress = "unknown";
            if (exchange.getRequest().getRemoteAddress() != null) {
                ipAddress = exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
            }
            return Mono.just(ipAddress);
        };
    }
}
