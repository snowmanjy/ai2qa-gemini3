package com.ai2qa.infra.jpa;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Test configuration for JPA tests.
 * Uses H2 in-memory database.
 */
@Configuration
@EnableAutoConfiguration
@EntityScan(basePackages = "com.ai2qa.infra.jpa")
@EnableJpaRepositories(basePackages = "com.ai2qa.infra.jpa")
@ComponentScan(basePackages = "com.ai2qa.infra.jpa")
public class JpaTestConfiguration {
}
