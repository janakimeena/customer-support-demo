package com.example.support;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Day 3 entry point. Same layering as Day 2 (controller → service → repository), now backed by PostgreSQL:
 * Spring Data JPA repositories, Flyway migrations and transactional services.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SupportServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SupportServiceApplication.class, args);
    }
}
