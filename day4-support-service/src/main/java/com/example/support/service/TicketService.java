package com.example.support.service;

import com.example.support.domain.Customer;
import com.example.support.domain.CustomerIds;
import com.example.support.domain.Ticket;
import com.example.support.domain.TicketPriority;
import com.example.support.domain.TicketStatus;
import com.example.support.domain.TicketStatusChange;
import com.example.support.dto.TicketRequest;
import com.example.support.dto.TicketResponse;
import com.example.support.dto.TicketStatusChangeResponse;
import com.example.support.repository.CustomerRepository;
import com.example.support.repository.TicketRepository;
import com.example.support.repository.TicketStatusChangeRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ticket workflow. Every status change and its audit row are written in one transaction. */
@Service
@Transactional(readOnly = true)
public class TicketService {

    private final TicketRepository tickets;
    private final TicketStatusChangeRepository statusChanges;
    private final CustomerRepository customers;
    private final Clock clock;

    public TicketService(TicketRepository tickets, TicketStatusChangeRepository statusChanges,
            CustomerRepository customers, Clock clock) {
        this.tickets = tickets;
        this.statusChanges = statusChanges;
        this.customers = customers;
        this.clock = clock;
    }

    /**
     * Paged search; {@code null} filters are ignored. An unknown {@code customerId} is a 404 rather than an
     * empty page, so a typo in the id is not mistaken for "this customer has no tickets".
     */
    public Page<TicketResponse> search(TicketStatus status, String customerId, Pageable pageable) {
        Long customerKey = customerId == null ? null : loadCustomer(customerId).getId();
        return tickets.search(status, customerKey, pageable).map(TicketResponse::from);
    }

    public TicketResponse findById(long id) {
        return TicketResponse.from(loadWithCustomer(id));
    }

    public List<TicketStatusChangeResponse> history(long id) {
        if (!tickets.existsById(id)) {
            throw new TicketNotFoundException(id);
        }
        return statusChanges.findByTicketIdOrderByChangedAtAscIdAsc(id).stream()
                .map(TicketStatusChangeResponse::from)
                .toList();
    }

    @Transactional
    public TicketResponse create(TicketRequest request) {
        Customer customer = loadCustomer(request.customerId());
        TicketPriority priority = request.priority() != null ? request.priority() : TicketPriority.NORMAL;
        Instant now = clock.instant();
        Ticket ticket = tickets.save(new Ticket(customer, request.subject(), priority, now));
        statusChanges.save(new TicketStatusChange(ticket, null, ticket.getStatus(), now));
        return TicketResponse.from(ticket);
    }

    /**
     * Two writes, one unit of work: the ticket UPDATE and the history INSERT. If the history insert fails,
     * the exception propagates out of this method, the transaction rolls back and the ticket keeps its old
     * status. ({@code TicketStatusTransactionTest} proves it.)
     *
     * <p>{@code @Version} on {@link Ticket} adds optimistic locking: if another transaction changed the
     * ticket after we read it, the UPDATE matches no row and Spring throws
     * {@code ObjectOptimisticLockingFailureException} (a 409) instead of silently losing that change.
     */
    @Transactional
    public TicketResponse changeStatus(long id, TicketStatus newStatus) {
        Ticket ticket = loadWithCustomer(id);
        if (ticket.getStatus() == newStatus) {
            throw new InvalidStatusChangeException(id, newStatus);
        }
        Instant now = clock.instant();
        TicketStatus previous = ticket.changeStatus(newStatus, now);
        statusChanges.save(new TicketStatusChange(ticket, previous, newStatus, now));
        return TicketResponse.from(ticket);
    }

    private Ticket loadWithCustomer(long id) {
        return tickets.findWithCustomerById(id).orElseThrow(() -> new TicketNotFoundException(id));
    }

    private Customer loadCustomer(String customerId) {
        return CustomerIds.parse(customerId)
                .flatMap(customers::findById)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
    }
}
