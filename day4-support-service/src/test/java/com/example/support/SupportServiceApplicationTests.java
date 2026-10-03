package com.example.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.support.config.CustomerProperties;
import com.example.support.repository.CustomerRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Full application context, once per profile. The "test" profile swaps PostgreSQL for H2. */
class SupportServiceApplicationTests {

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles({"dev", "test"})
    class DevProfile {

        @Autowired
        MockMvc mockMvc;

        @Autowired
        CustomerProperties properties;

        @Autowired
        CustomerRepository customerRepository;

        @Test
        void devProfileLoadsSampleDataThroughFlyway() {
            assertThat(properties.maxCustomers()).isEqualTo(50);
            assertThat(customerRepository.count()).isEqualTo(5);
        }

        @Test
        void actuatorReportsDatabaseAndMigrations() throws Exception {
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.components.db.status").value("UP"))
                    .andExpect(jsonPath("$.components.customerStore.details.customers").value(5));

            mockMvc.perform(get("/actuator/flyway"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contexts.*.flywayBeans.flyway.migrations[*].version", hasItems("3.1", "3.2")));

            mockMvc.perform(get("/actuator/info"))
                    .andExpect(jsonPath("$.profiles", hasItem("dev")));
        }
    }

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles({"prod", "test"})
    class ProdProfile {

        @Autowired
        MockMvc mockMvc;

        @Test
        void prodProfileHasSchemaButNoSampleDataAndRestrictsActuator() throws Exception {
            mockMvc.perform(get("/api/customers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0));
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.components").doesNotExist());
            mockMvc.perform(get("/actuator/flyway"))
                    .andExpect(status().isNotFound());
        }
    }
}
