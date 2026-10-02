package com.example.support.customer.repository;

import com.example.support.customer.domain.Customer;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Repository;

/** Thread-safe in-memory store. A singleton bean, so all requests share the same map. */
@Repository
public class InMemoryCustomerRepository implements CustomerRepository {

    private static final long FIRST_ID = 100;

    private final Map<String, Customer> customers = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(FIRST_ID);

    @Override
    public String nextId() {
        return "C-" + sequence.getAndIncrement();
    }

    @Override
    public List<Customer> findAll() {
        return customers.values().stream()
                .sorted(Comparator.comparingLong(InMemoryCustomerRepository::numericId))
                .toList();
    }

    @Override
    public Optional<Customer> findById(String id) {
        return Optional.ofNullable(customers.get(id));
    }

    @Override
    public Optional<Customer> findByEmail(String email) {
        String normalized = Customer.normalizeEmail(email);
        return customers.values().stream()
                .filter(customer -> customer.email().equals(normalized))
                .findFirst();
    }

    @Override
    public Customer save(Customer customer) {
        customers.put(customer.id(), customer);
        return customer;
    }

    @Override
    public boolean deleteById(String id) {
        return customers.remove(id) != null;
    }

    @Override
    public long count() {
        return customers.size();
    }

    private static long numericId(Customer customer) {
        try {
            return Long.parseLong(customer.id().substring(2));
        } catch (RuntimeException e) {
            return Long.MAX_VALUE;
        }
    }
}
