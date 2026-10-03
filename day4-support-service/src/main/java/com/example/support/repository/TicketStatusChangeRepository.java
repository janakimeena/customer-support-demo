package com.example.support.repository;

import com.example.support.domain.TicketStatusChange;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketStatusChangeRepository extends JpaRepository<TicketStatusChange, Long> {

    /** Served by index ix_status_changes_ticket (ticket_id, changed_at). */
    List<TicketStatusChange> findByTicketIdOrderByChangedAtAscIdAsc(Long ticketId);
}
