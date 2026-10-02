package com.example.support.customer.repository;

import com.example.support.customer.domain.Customer;
import java.util.List;
import java.util.Optional;

/**
 * Persistence abstraction. The service depends on this interface, not on an implementation, so the
 * container can inject any bean that implements it (in-memory today, JPA later).
 */
public interface CustomerRepository {

    String nextId();

    List<Customer> findAll();

    Optional<Customer> findById(String id);

    Optional<Customer> findByEmail(String email);

    Customer save(Customer customer);

    boolean deleteById(String id);

    long count();
}
