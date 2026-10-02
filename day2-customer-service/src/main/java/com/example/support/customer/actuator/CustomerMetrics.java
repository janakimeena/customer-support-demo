package com.example.support.customer.actuator;

import com.example.support.customer.repository.CustomerRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

/** Publishes {@code support.customers.count} at {@code /actuator/metrics/support.customers.count}. */
@Component
public class CustomerMetrics implements MeterBinder {

    private final CustomerRepository repository;

    public CustomerMetrics(CustomerRepository repository) {
        this.repository = repository;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("support.customers.count", repository, CustomerRepository::count)
                .description("Number of customers in the store")
                .register(registry);
    }
}
