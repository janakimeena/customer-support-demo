package com.example.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Real pipeline, real SQL (H2 in PostgreSQL mode, dev sample data, chunk size 3). {@code @Transactional}
 * rolls everything back after each test, so the other tests sharing this context still see 5 customers.
 * (The partner-crm source needs a running server; its pieces are covered by CrmClientTest and
 * CrmCustomerSourceTest.)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
@Transactional
class CustomerIngestionIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcClient jdbc;

    @Test
    void listsConfiguredSources() throws Exception {
        mockMvc.perform(get("/api/ingestion/sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("east-csv", "legacy-db", "partner-crm")));
    }

    @Test
    void csvImportUpsertsAndReportsRejections() throws Exception {
        mockMvc.perform(post("/api/ingestion/sources/east-csv/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.read").value(8))
                .andExpect(jsonPath("$.inserted").value(2))   // Hiro Tanaka, "Singh, Kara"
                .andExpect(jsonPath("$.updated").value(1))    // Ben Carter: STANDARD -> PREMIUM
                .andExpect(jsonPath("$.unchanged").value(1))  // Asha Rao
                .andExpect(jsonPath("$.rejected").value(4))
                .andExpect(jsonPath("$.rejections[*].origin", containsInAnyOrder(
                        "customers-east.csv:5", "customers-east.csv:6", "customers-east.csv:7", "customers-east.csv:9")));

        assertThat(customer("ben.carter@example.com"))
                .containsEntry("name", "Ben Carter").containsEntry("tier", "PREMIUM").containsEntry("version", 1L);
        assertThat(customer("hiro.tanaka@example.com")).containsEntry("tier", "STANDARD");
    }

    @Test
    void secondRunIsANoOp() throws Exception {
        mockMvc.perform(post("/api/ingestion/sources/legacy-db/runs"))
                .andExpect(jsonPath("$.read").value(6))       // closed account L-0004 is filtered in SQL
                .andExpect(jsonPath("$.inserted").value(2))   // Riya Das, Mary-Jane O'Brien
                .andExpect(jsonPath("$.updated").value(1))    // Eve Kim: STANDARD -> PREMIUM
                .andExpect(jsonPath("$.unchanged").value(1))  // Chen Wei
                .andExpect(jsonPath("$.rejected").value(2));  // no email; unknown plan code

        mockMvc.perform(post("/api/ingestion/sources/legacy-db/runs"))
                .andExpect(jsonPath("$.inserted").value(0))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.unchanged").value(4));

        // Updated exactly once, so the optimistic-locking version moved exactly once.
        assertThat(customer("eve.kim@example.com")).containsEntry("version", 1L);
        assertThat(customer("mj.obrien@example.com")).containsEntry("name", "Mary-Jane O'Brien");
    }

    @Test
    void dryRunPredictsButWritesNothing() throws Exception {
        mockMvc.perform(post("/api/ingestion/sources/legacy-db/runs").param("dryRun", "true"))
                .andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.inserted").value(2))
                .andExpect(jsonPath("$.updated").value(1));

        assertThat(jdbc.sql("SELECT count(*) FROM customers").query(Long.class).single()).isEqualTo(5);
        assertThat(customer("eve.kim@example.com")).containsEntry("tier", "STANDARD");
    }

    @Test
    void unknownSourceIs404() throws Exception {
        mockMvc.perform(post("/api/ingestion/sources/nope/runs"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Ingestion source not found"));
    }

    private Map<String, Object> customer(String email) {
        return jdbc.sql("SELECT name, tier, version FROM customers WHERE email = ?").param(email).query().singleRow();
    }
}
