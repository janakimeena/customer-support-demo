package com.example.support.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.example.support.domain.TicketStatus;
import com.example.support.repository.TicketStatusChangeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Proves that {@code changeStatus} is all-or-nothing. The history repository is a spy (real bean, one method
 * overridden) that fails on insert, after the ticket's status has already been changed in memory.
 *
 * <p>Deliberately NOT {@code @Transactional}: the service must open and roll back its own transaction, just
 * as it does for a real HTTP request.
 */
@SpringBootTest
@ActiveProfiles({"dev", "test"})
class TicketStatusTransactionTest {

    @Autowired
    TicketService ticketService;

    @MockitoSpyBean
    TicketStatusChangeRepository statusChanges;

    @Test
    void failedHistoryInsertRollsBackTheStatusChange() {
        // Seeded ticket 1001 ("Refund status, please") is OPEN with one history row.
        doThrow(new IllegalStateException("audit store unavailable")).when(statusChanges).save(any());

        assertThatThrownBy(() -> ticketService.changeStatus(1001L, TicketStatus.CLOSED))
                .isInstanceOf(IllegalStateException.class);

        assertThat(ticketService.findById(1001L).status()).isEqualTo(TicketStatus.OPEN);
        assertThat(ticketService.history(1001L)).hasSize(1);
    }
}
