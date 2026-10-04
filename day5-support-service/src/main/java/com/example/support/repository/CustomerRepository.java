package com.example.support.repository;

import com.example.support.domain.Customer;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Spring Data generates the implementation at startup. {@link JpaRepository} already provides
 * {@code findById}, {@code findAll(Pageable)}, {@code save}, {@code delete}, {@code count}, ...
 *
 * <p>Day 2's hand-written {@code CustomerRepository} interface is replaced by this one; the service still
 * depends only on an interface.
 */
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /** Derived query: Spring Data builds the JPQL from the method name. */
    Optional<Customer> findByEmail(String email);

    /** {@code ... where upper(name) like upper(?)} with {@code %q%}, paged. */
    Page<Customer> findByNameContainingIgnoreCase(String name, Pageable pageable);

    /**
     * Join + aggregate: ticket counts per customer, busiest customers first.
     *
     * <p>LEFT JOIN keeps customers with no tickets (they get 0). The {@code select new} constructor expression
     * maps each row straight into a read-only projection instead of loading entities. A GROUP BY query can't
     * be counted by wrapping it, so the {@code countQuery} for pagination is given explicitly.
     */
    @Query(value = """
            select new com.example.support.repository.CustomerTicketCounts(
                c.id, c.name, c.tier,
                count(t.id),
                coalesce(sum(case when t.status = com.example.support.domain.TicketStatus.OPEN then 1 else 0 end), 0))
            from Customer c
            left join Ticket t on t.customer = c
            group by c.id, c.name, c.tier
            order by coalesce(sum(case when t.status = com.example.support.domain.TicketStatus.OPEN then 1 else 0 end), 0) desc,
                     c.id
            """,
            countQuery = "select count(c) from Customer c")
    Page<CustomerTicketCounts> findTicketCounts(Pageable pageable);
}
