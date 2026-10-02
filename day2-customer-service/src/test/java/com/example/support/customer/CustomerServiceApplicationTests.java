package com.example.support.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.support.customer.api.CustomerController;
import com.example.support.customer.config.CustomerProperties;
import com.example.support.customer.config.DevDataLoader;
import com.example.support.customer.repository.CustomerRepository;
import com.example.support.customer.repository.InMemoryCustomerRepository;
import com.example.support.customer.service.CustomerService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Full application context, once per profile. */
class CustomerServiceApplicationTests {

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("dev")
    class DevProfile {

        @Autowired
        ApplicationContext context;

        @Autowired
        MockMvc mockMvc;

        @Autowired
        CustomerProperties properties;

        @Test
        void containerWiresTheLayersTogether() {
            assertThat(context.getBean(CustomerController.class)).isNotNull();
            assertThat(context.getBean(CustomerService.class)).isNotNull();
            assertThat(context.getBean(CustomerRepository.class)).isInstanceOf(InMemoryCustomerRepository.class);
            // Singleton scope: the container hands out the same instance every time.
            assertThat(context.getBean(CustomerService.class)).isSameAs(context.getBean(CustomerService.class));
        }

        @Test
        void devProfileOverridesPropertiesAndSeedsData() throws Exception {
            assertThat(properties.maxCustomers()).isEqualTo(50);
            assertThat(context.getBeansOfType(DevDataLoader.class)).hasSize(1);

            mockMvc.perform(get("/api/customers/C-100"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Asha Rao"));
        }

        @Test
        void actuatorHealthIncludesCustomStoreIndicator() throws Exception {
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"))
                    .andExpect(jsonPath("$.components.customerStore.details.capacity").value(50));
        }

        @Test
        void actuatorExposesInfoMetricsAndBeansInDev() throws Exception {
            mockMvc.perform(get("/actuator/info"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.app.name").value("customer-service"))
                    .andExpect(jsonPath("$.profiles", hasItem("dev")));
            mockMvc.perform(get("/actuator/metrics/support.customers.count"))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/actuator/beans"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("prod")
    class ProdProfile {

        @Autowired
        ApplicationContext context;

        @Autowired
        MockMvc mockMvc;

        @Test
        void prodProfileHasNoSampleDataAndRestrictsActuator() throws Exception {
            assertThat(context.getBeansOfType(DevDataLoader.class)).isEmpty();

            mockMvc.perform(get("/api/customers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.components").doesNotExist());
            mockMvc.perform(get("/actuator/beans"))
                    .andExpect(status().isNotFound());
        }
    }
}
