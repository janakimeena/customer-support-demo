package com.example.support;

import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Whole application (controllers → services → repositories → H2 with the real Flyway migrations and the dev
 * sample data), driven through MockMvc. {@code @Transactional} rolls back each test's changes; MockMvc runs
 * the request on the test's thread, so the request joins the test transaction.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
@Transactional
class SupportApiIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void customersArePagedAndSorted() throws Exception {
        mockMvc.perform(get("/api/customers").param("size", "2").param("sort", "name,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].name").value("Eve Kim"))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));

        mockMvc.perform(get("/api/customers").param("q", "chen"))
                .andExpect(jsonPath("$.content[0].id").value("C-102"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void ticketListJoinsCustomerNameAndFiltersByStatus() throws Exception {
        mockMvc.perform(get("/api/tickets").param("status", "OPEN").param("sort", "createdAt,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[0].id").value(1001))
                .andExpect(jsonPath("$.content[0].customerId").value("C-100"))
                .andExpect(jsonPath("$.content[0].customerName").value("Asha Rao"));

        mockMvc.perform(get("/api/customers/C-100/tickets").param("status", "OPEN"))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void ticketSummaryCountsPerCustomer() throws Exception {
        mockMvc.perform(get("/api/customers/ticket-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].customerId").value("C-100"))
                .andExpect(jsonPath("$.content[0].totalTickets").value(3))
                .andExpect(jsonPath("$.content[0].openTickets").value(2))
                .andExpect(jsonPath("$.content[4].customerId").value("C-104"))
                .andExpect(jsonPath("$.content[4].totalTickets").value(0));
    }

    @Test
    void ticketLifecycleWritesHistory() throws Exception {
        String body = mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "C-104", "subject": "Where is my order?"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/tickets/1008")))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.priority").value("NORMAL"))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(body, "$.id");

        mockMvc.perform(patch("/api/tickets/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "CLOSED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        mockMvc.perform(get("/api/tickets/{id}/history", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].fromStatus").doesNotExist())
                .andExpect(jsonPath("$[0].toStatus").value("OPEN"))
                .andExpect(jsonPath("$[1].fromStatus").value("OPEN"))
                .andExpect(jsonPath("$[1].toStatus").value("CLOSED"));
    }

    @Test
    void customerCrudAndBusinessRules() throws Exception {
        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Farah Ali", "email": "Farah@Example.com"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("C-105"))
                .andExpect(jsonPath("$.email").value("farah@example.com"));

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Copy", "email": "FARAH@example.com"}
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/customers/C-100"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Customer C-100 still has tickets and cannot be deleted"));

        mockMvc.perform(delete("/api/customers/C-105")).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/customers/C-105")).andExpect(status().isNotFound());
    }

    @Test
    void ticketForUnknownCustomerIs404() throws Exception {
        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "C-999", "subject": "Hello"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Customer not found"));
    }
}
