package com.openfloat.mpesa.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication(scanBasePackages = "com.openfloat.mpesa.auth")
@EnableJpaAuditing
public class OpenFloatAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenFloatAuthApplication.class, args);
    }
}
