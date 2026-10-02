package com.example.support.customer.service;

import com.example.support.customer.config.CustomerProperties;
import com.example.support.customer.domain.Customer;
import com.example.support.customer.repository.CustomerRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Business rules for customers.
 *
 * <p>All collaborators arrive through the constructor (constructor injection): the class never calls
 * {@code new} on its dependencies, so tests can pass in a fixed {@link Clock} or a different repository.
 * With a single constructor, Spring needs no {@code @Autowired} annotation.
 */
@Service
public class CustomerService {

    private final CustomerRepository repository;
    private final CustomerProperties properties;
    private final Clock clock;

    public CustomerService(CustomerRepository repository, CustomerProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    public List<Customer> findAll() {
        return repository.findAll();
    }

    public Customer findById(String id) {
        return repository.findById(id).orElseThrow(() -> new CustomerNotFoundException(id));
    }

    /** Creates a customer; a {@code null} tier falls back to the configured default tier. */
    public synchronized Customer create(String name, String email, Customer.Tier tier) {
        if (repository.count() >= properties.maxCustomers()) {
            throw new CustomerLimitExceededException(properties.maxCustomers());
        }
        ensureEmailAvailable(email, null);
        Customer.Tier effectiveTier = tier != null ? tier : properties.defaultTier();
        return repository.save(new Customer(repository.nextId(), name, email, effectiveTier, clock.instant()));
    }

    /** Replaces name and email; a {@code null} tier keeps the customer's current tier. */
    public synchronized Customer update(String id, String name, String email, Customer.Tier tier) {
        Customer existing = findById(id);
        ensureEmailAvailable(email, id);
        Customer.Tier effectiveTier = tier != null ? tier : existing.tier();
        return repository.save(existing.withDetails(name, email, effectiveTier));
    }

    public void delete(String id) {
        if (!repository.deleteById(id)) {
            throw new CustomerNotFoundException(id);
        }
    }

    private void ensureEmailAvailable(String email, String ownerId) {
        repository.findByEmail(email)
                .filter(other -> !other.id().equals(ownerId))
                .ifPresent(other -> {
                    throw new DuplicateEmailException(other.email());
                });
    }
}
