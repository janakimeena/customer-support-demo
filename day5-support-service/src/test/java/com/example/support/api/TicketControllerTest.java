package com.example.support.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.support.domain.TicketStatus;
import com.example.support.service.InvalidStatusChangeException;
import com.example.support.service.TicketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TicketController.class)
class TicketControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    TicketService ticketService;

    @Test
    void createValidatesCustomerIdFormatAndSubject() throws Exception {
        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "100", "subject": "  "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.customerId").value("must be a customer id such as C-100"))
                .andExpect(jsonPath("$.errors.subject").exists());

        verifyNoInteractions(ticketService);
    }

    @Test
    void unknownStatusFilterIs400() throws Exception {
        mockMvc.perform(get("/api/tickets").param("status", "SOLVED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.status").exists());
    }

    @Test
    void changingToCurrentStatusIs409() throws Exception {
        when(ticketService.changeStatus(eq(1001L), any()))
                .thenThrow(new InvalidStatusChangeException(1001L, TicketStatus.CLOSED));

        mockMvc.perform(patch("/api/tickets/1001/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "CLOSED"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ticket 1001 is already CLOSED"));
    }
}
