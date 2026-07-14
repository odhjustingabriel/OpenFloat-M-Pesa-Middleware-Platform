package com.openfloat.mpesa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the OpenFloat M-Pesa Middleware Core Service.
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
@EnableAsync
public class OpenFloatCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenFloatCoreApplication.class, args);
    }
}
