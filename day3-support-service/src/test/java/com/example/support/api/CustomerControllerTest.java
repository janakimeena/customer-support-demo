package com.example.support.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.support.domain.Customer;
import com.example.support.dto.CustomerRequest;
import com.example.support.dto.CustomerResponse;
import com.example.support.service.CustomerNotFoundException;
import com.example.support.service.CustomerService;
import com.example.support.service.DuplicateEmailException;
import com.example.support.service.TicketService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Web-layer slice: MVC infrastructure + this controller; services are mocks, no database is started. */
@WebMvcTest(CustomerController.class)
class CustomerControllerTest {

    private static final Instant T0 = Instant.parse("2026-10-01T09:00:00Z");
    private static final CustomerResponse ASHA =
            new CustomerResponse("C-100", "Asha Rao", "asha@example.com", Customer.Tier.PREMIUM, T0, T0);

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CustomerService customerService;

    @MockitoBean
    TicketService ticketService;

    @Test
    void listBindsPageSizeAndSortAndReturnsPageEnvelope() throws Exception {
        when(customerService.search(eq("asha"), any()))
                .thenReturn(new PageImpl<>(List.of(ASHA), PageRequest.of(1, 1), 3));

        mockMvc.perform(get("/api/customers").param("q", "asha")
                        .param("page", "1").param("size", "1").param("sort", "name,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("C-100"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.hasNext").value(true));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(customerService).search(eq("asha"), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "name"));
    }

    @Test
    void listUsesDefaultsAndCapsPageSize() throws Exception {
        when(customerService.search(isNull(), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/customers").param("size", "5000")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(customerService).search(isNull(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);  // spring.data.web.pageable.max-page-size
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by("id"));
    }

    @Test
    void sortingByUnlistedPropertyIs400() throws Exception {
        mockMvc.perform(get("/api/customers").param("sort", "version"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid sort"));

        verifyNoInteractions(customerService);
    }

    @Test
    void malformedIdIs400FromParameterValidation() throws Exception {
        mockMvc.perform(get("/api/customers/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.id").value("must be a customer id such as C-100"));

        verifyNoInteractions(customerService);
    }

    @Test
    void unknownIdIs404Problem() throws Exception {
        when(customerService.findById("C-999")).thenThrow(new CustomerNotFoundException("C-999"));

        mockMvc.perform(get("/api/customers/C-999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Customer not found"));
    }

    @Test
    void postCreatesCustomerWithLocationHeader() throws Exception {
        when(customerService.create(new CustomerRequest("Asha Rao", "asha@example.com", Customer.Tier.PREMIUM)))
                .thenReturn(ASHA);

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
    void postInvalidBodyIs400WithFieldErrors() throws Exception {
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
    void duplicateEmailIs409() throws Exception {
        when(customerService.create(any())).thenThrow(new DuplicateEmailException("asha@example.com"));

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Asha", "email": "asha@example.com"}
                                """))
                .andExpect(status().isConflict());
    }
}
