package com.openfloat.mpesa.erp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.openfloat.mpesa.erp")
@EnableJpaAuditing
@EnableScheduling
public class ErpConnectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ErpConnectorApplication.class, args);
    }
}
