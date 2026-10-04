package com.example.support.assistant;

import com.example.support.domain.TicketStatus;
import com.example.support.service.CustomerService;
import com.example.support.service.NotFoundException;
import com.example.support.service.TicketService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * <b>Tool calling</b>: methods the model may ask us to run. The model never touches the database; it sends back
 * "call {@code getTicket} with 3", Spring AI runs the method, sends the result back, and the model writes its
 * answer from that. The {@code description} texts are part of the prompt: they are how the model decides which
 * tool fits the question, so they are written for the model.
 *
 * <p>The tools reuse the Day 3 services, so they return the same DTOs as the REST API and nothing internal.
 * All of them are read-only on purpose: a model that can only read can't do damage by misunderstanding a
 * question. (Who may see which customer's data is Day 12 and Day 14 work.)
 *
 * <p>One instance per request, so {@link #calls()} lists exactly the tools used for one answer.
 */
public class SupportTools {

    private static final int MAX_TICKETS = 20;

    private final CustomerService customers;
    private final TicketService tickets;
    private final List<String> calls = new ArrayList<>();

    public SupportTools(CustomerService customers, TicketService tickets) {
        this.customers = customers;
        this.tickets = tickets;
    }

    @Tool(description = "Find a customer by email address. Returns the customer's id (like C-1), name and tier.")
    public Object findCustomerByEmail(@ToolParam(description = "The customer's email address") String email) {
        record("findCustomerByEmail(" + email + ")");
        return customers.findByEmail(email)
                .<Object>map(customer -> customer)
                .orElseGet(() -> notFound("No customer with email " + email));
    }

    @Tool(description = "List a customer's support tickets, newest first (at most 20).")
    public Object listTickets(
            @ToolParam(description = "Customer id, like C-1") String customerId,
            @ToolParam(description = "Only tickets with this status; omit for all", required = false)
            TicketStatus status) {
        record("listTickets(" + customerId + (status == null ? "" : ", " + status) + ")");
        try {
            return tickets.search(status, customerId,
                    PageRequest.of(0, MAX_TICKETS, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();
        } catch (NotFoundException ex) {
            return notFound(ex.getMessage());
        }
    }

    @Tool(description = "Get one support ticket by its numeric id, including its status and priority.")
    public Object getTicket(@ToolParam(description = "Ticket id, a number like 3") long ticketId) {
        record("getTicket(" + ticketId + ")");
        try {
            return tickets.findById(ticketId);
        } catch (NotFoundException ex) {
            return notFound(ex.getMessage());
        }
    }

    public List<String> calls() {
        return List.copyOf(calls);
    }

    private void record(String call) {
        synchronized (calls) {
            calls.add(call);
        }
    }

    /** A plain result the model can read and repeat, instead of an exception it can't see. */
    private static Map<String, String> notFound(String message) {
        return Map.of("error", message);
    }
}
