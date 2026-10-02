package com.example.support.api;

import com.example.support.domain.TicketStatus;
import com.example.support.dto.PageResponse;
import com.example.support.dto.TicketRequest;
import com.example.support.dto.TicketResponse;
import com.example.support.dto.TicketStatusChangeResponse;
import com.example.support.dto.TicketStatusRequest;
import com.example.support.dto.ValidCustomerId;
import com.example.support.service.TicketService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private static final Set<String> SORTABLE = Set.of("id", "status", "createdAt", "updatedAt", "customer.name");

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    /** {@code GET /api/tickets?status=OPEN&customerId=C-100&page=0&size=20&sort=createdAt,asc} */
    @GetMapping
    public PageResponse<TicketResponse> search(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) @ValidCustomerId String customerId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(
                ticketService.search(status, customerId, SortGuard.requireSortable(pageable, SORTABLE)));
    }

    @GetMapping("/{id}")
    public TicketResponse findById(@PathVariable long id) {
        return ticketService.findById(id);
    }

    @GetMapping("/{id}/history")
    public List<TicketStatusChangeResponse> history(@PathVariable long id) {
        return ticketService.history(id);
    }

    @PostMapping
    public ResponseEntity<TicketResponse> create(@Valid @RequestBody TicketRequest request) {
        TicketResponse created = ticketService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /** PATCH: changes one field of the ticket rather than replacing the whole resource. */
    @PatchMapping("/{id}/status")
    public TicketResponse changeStatus(@PathVariable long id, @Valid @RequestBody TicketStatusRequest request) {
        return ticketService.changeStatus(id, request.status());
    }
}
