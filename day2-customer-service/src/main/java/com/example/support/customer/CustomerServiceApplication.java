package com.example.support.customer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Day 2 entry point.
 *
 * <p>{@code @SpringBootApplication} turns on component scanning for this package and below, so every
 * {@code @RestController}, {@code @Service}, {@code @Repository}, {@code @Component} and
 * {@code @Configuration} class becomes a bean managed by the IoC container. {@code @ConfigurationPropertiesScan}
 * registers the {@code @ConfigurationProperties} records.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CustomerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomerServiceApplication.class, args);
    }
}
