package com.example.support.repository;

import com.example.support.domain.Ticket;
import com.example.support.domain.TicketStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    /**
     * Explicit {@code join fetch}: ticket and customer arrive in one SQL statement (an inner join), so the
     * response can show the customer's name without a second query.
     */
    @Query("select t from Ticket t join fetch t.customer where t.id = :id")
    Optional<Ticket> findWithCustomerById(@Param("id") Long id);

    /**
     * Paged search with optional filters ({@code null} = no filter).
     *
     * <p>{@code @EntityGraph} has the same effect as {@code join fetch} but leaves the query text alone, which
     * keeps the count query that Spring Data derives for pagination simple. Without it, mapping a page of 20
     * tickets would run 1 query for the page + up to 20 for their customers: the classic N+1 problem.
     */
    @EntityGraph(attributePaths = "customer")
    @Query("""
            select t from Ticket t
            where (:status is null or t.status = :status)
              and (:customerId is null or t.customer.id = :customerId)
            """)
    Page<Ticket> search(
            @Param("status") TicketStatus status,
            @Param("customerId") Long customerId,
            Pageable pageable);

    /** {@code t.customer.id} needs no join: the foreign key column is on the tickets table. */
    boolean existsByCustomerId(Long customerId);
}
