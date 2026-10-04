package com.example.support.actuator;

import com.example.support.config.CustomerProperties;
import com.example.support.repository.CustomerRepository;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Appears as the {@code customerStore} component of {@code /actuator/health}. Reports
 * OUT_OF_SERVICE when the store is full, because new customers can no longer be created.
 */
@Component
public class CustomerStoreHealthIndicator implements HealthIndicator {

    private final CustomerRepository repository;
    private final CustomerProperties properties;

    public CustomerStoreHealthIndicator(CustomerRepository repository, CustomerProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Override
    public Health health() {
        long count = repository.count();
        Health.Builder builder = count < properties.maxCustomers() ? Health.up() : Health.outOfService();
        return builder
                .withDetail("customers", count)
                .withDetail("capacity", properties.maxCustomers())
                .build();
    }
}
