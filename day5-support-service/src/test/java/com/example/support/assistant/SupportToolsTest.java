package com.example.support.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.support.domain.Customer;
import com.example.support.dto.CustomerResponse;
import com.example.support.service.CustomerNotFoundException;
import com.example.support.service.CustomerService;
import com.example.support.service.TicketService;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** The tools are plain Java methods, so they are tested like any other code, without a model. */
@ExtendWith(MockitoExtension.class)
class SupportToolsTest {

    @Mock
    CustomerService customers;

    @Mock
    TicketService tickets;

    @Test
    void findCustomerByEmailReturnsTheApiView() {
        CustomerResponse asha = new CustomerResponse("C-1", "Asha Rao", "asha.rao@example.com",
                Customer.Tier.PREMIUM, Instant.EPOCH, Instant.EPOCH);
        when(customers.findByEmail("Asha.Rao@example.com")).thenReturn(Optional.of(asha));
        SupportTools tools = new SupportTools(customers, tickets);

        assertThat(tools.findCustomerByEmail("Asha.Rao@example.com")).isEqualTo(asha);
        assertThat(tools.calls()).containsExactly("findCustomerByEmail(Asha.Rao@example.com)");
    }

    @Test
    void lookupsThatFindNothingReturnAnErrorTheModelCanRead() {
        when(customers.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        when(tickets.search(eq(null), eq("C-404"), any())).thenThrow(new CustomerNotFoundException("C-404"));
        SupportTools tools = new SupportTools(customers, tickets);

        assertThat(tools.findCustomerByEmail("nobody@example.com"))
                .isEqualTo(Map.of("error", "No customer with email nobody@example.com"));
        assertThat(tools.listTickets("C-404", null)).isInstanceOf(Map.class).asString().contains("C-404");
        assertThat(tools.calls()).containsExactly("findCustomerByEmail(nobody@example.com)", "listTickets(C-404)");
    }
}
