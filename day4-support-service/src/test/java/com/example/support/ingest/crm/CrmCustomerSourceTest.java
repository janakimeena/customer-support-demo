package com.example.support.ingest.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.example.support.ingest.RawCustomer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The adapter is tested against a Mockito mock of the adaptee ({@link CrmClient}): no HTTP involved, just
 * "given these CRM pages, which customers come out, and which pages were requested?".
 */
@ExtendWith(MockitoExtension.class)
class CrmCustomerSourceTest {

    private static final int SIZE = CrmCustomerSource.PAGE_SIZE;

    @Mock
    CrmClient client;

    @Test
    void walksAllPagesAndTranslatesContacts() {
        when(client.fetchContacts(0, SIZE)).thenReturn(new CrmPage(List.of(
                new CrmContact(7001, "Carter, Ben", "ben@example.com", "GOLD"),
                new CrmContact(7002, "Madonna", "m@example.com", "free")), 0, false));
        when(client.fetchContacts(1, SIZE)).thenReturn(new CrmPage(List.of(
                new CrmContact(7003, "Patel, Olivia", "olivia@example.com", "PLATINUM")), 1, true));

        List<RawCustomer> customers = new CrmCustomerSource(client).read().toList();

        assertThat(customers).containsExactly(
                new RawCustomer("crm contact 7001", "Ben Carter", "ben@example.com", "PREMIUM"),
                new RawCustomer("crm contact 7002", "Madonna", "m@example.com", "STANDARD"),
                // Unknown plan passed through: the normalizer rejects it with a clear reason.
                new RawCustomer("crm contact 7003", "Olivia Patel", "olivia@example.com", "PLATINUM"));
    }

    @Test
    void nextPageIsOnlyRequestedWhenNeeded() {
        when(client.fetchContacts(0, SIZE)).thenReturn(new CrmPage(List.of(
                new CrmContact(7001, "Carter, Ben", "ben@example.com", "GOLD")), 0, false));

        new CrmCustomerSource(client).read().findFirst();

        verify(client).fetchContacts(0, SIZE);
        verifyNoMoreInteractions(client);
    }
}
