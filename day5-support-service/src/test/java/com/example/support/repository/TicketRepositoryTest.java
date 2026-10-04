package com.example.support.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.support.domain.Customer;
import com.example.support.domain.Ticket;
import com.example.support.domain.TicketPriority;
import com.example.support.domain.TicketStatus;
import java.time.Instant;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

/**
 * JPA slice: only the DataSource, Flyway, Hibernate and the repositories are started. Each test runs in a
 * transaction that is rolled back afterwards, so tests don't see each other's data.
 *
 * <p>{@code replace = NONE} keeps the H2-in-PostgreSQL-mode database from application-test.yml instead of
 * swapping in a plain embedded one. Flyway runs the real migrations (without dev sample data).
 */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class TicketRepositoryTest {

    private static final Instant T0 = Instant.parse("2026-10-01T09:00:00Z");

    @Autowired
    TestEntityManager entityManager;

    @Autowired
    TicketRepository tickets;

    @Autowired
    CustomerRepository customers;

    private Customer asha;
    private Customer ben;
    private Customer chen;

    @BeforeEach
    void setUp() {
        asha = entityManager.persist(new Customer("Asha Rao", "asha@example.com", Customer.Tier.PREMIUM, T0));
        ben = entityManager.persist(new Customer("Ben Carter", "ben@example.com", Customer.Tier.STANDARD, T0));
        chen = entityManager.persist(new Customer("Chen Wei", "chen@example.com", Customer.Tier.STANDARD, T0));

        ticket(asha, "Refund status", TicketStatus.OPEN, 1);
        ticket(asha, "Cannot sign in", TicketStatus.OPEN, 2);
        ticket(asha, "Duplicate charge", TicketStatus.CLOSED, 3);
        ticket(ben, "Damaged package", TicketStatus.OPEN, 4);
        ticket(ben, "Return label", TicketStatus.PENDING, 5);
        // chen has no tickets.

        // Write everything to the database and empty the persistence context, so the tests below really
        // query the database instead of getting objects back from Hibernate's first-level cache.
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void searchFiltersByStatusAndCustomerAndPages() {
        Page<Ticket> openTickets = tickets.search(TicketStatus.OPEN, null,
                PageRequest.of(0, 2, Sort.by("createdAt")));

        assertThat(openTickets.getTotalElements()).isEqualTo(3);
        assertThat(openTickets.getTotalPages()).isEqualTo(2);
        assertThat(openTickets.getContent()).extracting(Ticket::getSubject)
                .containsExactly("Refund status", "Cannot sign in");

        Page<Ticket> ashaOpen = tickets.search(TicketStatus.OPEN, asha.getId(), PageRequest.of(0, 10));
        assertThat(ashaOpen.getContent()).extracting(Ticket::getSubject)
                .containsExactlyInAnyOrder("Refund status", "Cannot sign in");

        Page<Ticket> all = tickets.search(null, null, PageRequest.of(0, 10));
        assertThat(all.getTotalElements()).isEqualTo(5);
    }

    @Test
    void ticketCountsUseLeftJoinSoCustomersWithoutTicketsAppear() {
        List<CustomerTicketCounts> rows = customers.findTicketCounts(PageRequest.of(0, 10)).getContent();

        assertThat(rows).containsExactly(
                new CustomerTicketCounts(asha.getId(), "Asha Rao", Customer.Tier.PREMIUM, 3L, 2L),
                new CustomerTicketCounts(ben.getId(), "Ben Carter", Customer.Tier.STANDARD, 2L, 1L),
                new CustomerTicketCounts(chen.getId(), "Chen Wei", Customer.Tier.STANDARD, 0L, 0L));
    }

    @Test
    void entityGraphLoadsCustomersInTheSameQuery() {
        Statistics stats = statistics();

        Page<Ticket> page = tickets.search(null, null, PageRequest.of(0, 10));
        long afterQuery = stats.getPrepareStatementCount();
        page.forEach(ticket -> ticket.getCustomer().getName());

        // Touching every ticket's customer ran no extra SQL: they were joined into the page query.
        assertThat(stats.getPrepareStatementCount()).isEqualTo(afterQuery);
    }

    @Test
    void withoutFetchPlanEachCustomerCostsAnExtraQuery() {
        Statistics stats = statistics();

        List<Ticket> all = tickets.findAll();
        long afterQuery = stats.getPrepareStatementCount();
        all.forEach(ticket -> ticket.getCustomer().getName());

        // The N+1 problem: 1 query for the tickets, then 1 per distinct lazy customer (asha, ben).
        assertThat(stats.getPrepareStatementCount() - afterQuery).isEqualTo(2);
    }

    @Test
    void databaseEnforcesUniqueEmail() {
        assertThatThrownBy(() -> customers.saveAndFlush(
                new Customer("Someone Else", "asha@example.com", Customer.Tier.STANDARD, T0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void foreignKeyPreventsDeletingCustomerWithTickets() {
        assertThat(tickets.existsByCustomerId(asha.getId())).isTrue();
        assertThat(tickets.existsByCustomerId(chen.getId())).isFalse();

        assertThatThrownBy(() -> {
            customers.deleteById(asha.getId());
            customers.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void ticket(Customer customer, String subject, TicketStatus status, int minutes) {
        Instant created = T0.plusSeconds(60L * minutes);
        Ticket ticket = new Ticket(customer, subject, TicketPriority.NORMAL, created);
        if (status != TicketStatus.OPEN) {
            ticket.changeStatus(status, created);
        }
        entityManager.persist(ticket);
    }

    private Statistics statistics() {
        Statistics stats = entityManager.getEntityManager().getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        return stats;
    }
}
