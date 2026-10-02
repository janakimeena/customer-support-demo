package com.example.support.customer.api;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.support.customer.domain.Customer;
import com.example.support.customer.service.CustomerNotFoundException;
import com.example.support.customer.service.CustomerService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Web-layer slice: only MVC beans are created and the service bean is replaced with a mock. */
@WebMvcTest(CustomerController.class)
class CustomerControllerTest {

    private static final Customer ASHA = new Customer(
            "C-100", "Asha Rao", "asha@example.com", Customer.Tier.PREMIUM, Instant.parse("2026-10-01T09:00:00Z"));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerService customerService;

    @Test
    void getByIdReturnsCustomer() throws Exception {
        when(customerService.findById("C-100")).thenReturn(ASHA);

        mockMvc.perform(get("/api/customers/C-100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("C-100"))
                .andExpect(jsonPath("$.tier").value("PREMIUM"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-01T09:00:00Z"));
    }

    @Test
    void getUnknownIdReturnsProblem404() throws Exception {
        when(customerService.findById("C-999")).thenThrow(new CustomerNotFoundException("C-999"));

        mockMvc.perform(get("/api/customers/C-999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Customer not found"));
    }

    @Test
    void postCreatesCustomerWithLocationHeader() throws Exception {
        when(customerService.create(eq("Asha Rao"), eq("asha@example.com"), any())).thenReturn(ASHA);

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Asha Rao", "email": "asha@example.com", "tier": "PREMIUM"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/customers/C-100")))
                .andExpect(jsonPath("$.id").value("C-100"));
    }

    @Test
    void postInvalidBodyReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "", "email": "not-an-email"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.email").exists());

        verifyNoInteractions(customerService);
    }

    @Test
    void postUnknownTierReturns400Problem() throws Exception {
        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Asha", "email": "asha@example.com", "tier": "GOLD"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"));

        verifyNoInteractions(customerService);
    }
}
