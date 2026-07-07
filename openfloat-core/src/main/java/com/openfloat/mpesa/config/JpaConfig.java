package com.openfloat.mpesa.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * JPA configuration for auditing, transaction management,
 * and repository scanning.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.openfloat.mpesa.repository")
@EnableTransactionManagement
public class JpaConfig {
    // JPA Auditing is enabled via @EnableJpaAuditing on the main application class.
    // Custom AttributeConverters are auto-detected from the entity scan path.
}
