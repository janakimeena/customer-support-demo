package com.example.support.api;

import com.example.support.domain.TicketStatus;
import com.example.support.dto.CustomerRequest;
import com.example.support.dto.CustomerResponse;
import com.example.support.dto.CustomerTicketSummary;
import com.example.support.dto.PageResponse;
import com.example.support.dto.TicketResponse;
import com.example.support.dto.ValidCustomerId;
import com.example.support.service.CustomerService;
import com.example.support.service.TicketService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * HTTP layer only. {@code @Valid} validates request bodies; constraint annotations directly on
 * {@code @PathVariable}/{@code @RequestParam} parameters are validated by Spring MVC's built-in method
 * validation. Paging comes from {@code ?page=0&size=20&sort=name,asc}, resolved into a {@link Pageable}.
 * Note: {@code @PageableDefault} has its own default size (10) that wins over
 * {@code spring.data.web.pageable.default-page-size}, so it is set explicitly.
 */
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private static final Set<String> SORTABLE = Set.of("id", "name", "email", "tier", "createdAt");
    private static final Set<String> TICKET_SORTABLE = Set.of("id", "status", "createdAt", "updatedAt");

    private final CustomerService customerService;
    private final TicketService ticketService;

    public CustomerController(CustomerService customerService, TicketService ticketService) {
        this.customerService = customerService;
        this.ticketService = ticketService;
    }

    @GetMapping
    public PageResponse<CustomerResponse> search(
            @RequestParam(required = false) @Size(max = 100) String q,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return PageResponse.from(customerService.search(q, SortGuard.requireSortable(pageable, SORTABLE)));
    }

    /** Customers with their total and open ticket counts, most open tickets first (fixed order). */
    @GetMapping("/ticket-summary")
    public PageResponse<CustomerTicketSummary> ticketSummary(Pageable pageable) {
        return PageResponse.from(customerService.ticketSummary(SortGuard.requireSortable(pageable, Set.of())));
    }

    @GetMapping("/{id}")
    public CustomerResponse findById(@PathVariable @ValidCustomerId String id) {
        return customerService.findById(id);
    }

    /** Sub-resource: one customer's tickets, newest first by default. */
    @GetMapping("/{id}/tickets")
    public PageResponse<TicketResponse> tickets(
            @PathVariable @ValidCustomerId String id,
            @RequestParam(required = false) TicketStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(
                ticketService.search(status, id, SortGuard.requireSortable(pageable, TICKET_SORTABLE)));
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CustomerRequest request) {
        CustomerResponse created = customerService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public CustomerResponse update(
            @PathVariable @ValidCustomerId String id, @Valid @RequestBody CustomerRequest request) {
        return customerService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @ValidCustomerId String id) {
        customerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
