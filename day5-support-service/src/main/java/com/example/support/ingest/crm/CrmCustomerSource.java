package com.example.support.ingest.crm;

import com.example.support.ingest.CustomerSource;
import com.example.support.ingest.RawCustomer;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * <b>Adapter</b>: makes the partner CRM look like any other {@link CustomerSource}.
 *
 * <ul>
 *   <li>Interface: pages of contacts → one lazy stream of customers. The next page is only requested when
 *       the previous one has been consumed.</li>
 *   <li>Data: {@code "Carter, Ben"} → {@code "Ben Carter"}, {@code emailAddress} → {@code email},
 *       plan → tier.</li>
 * </ul>
 *
 * Unknown plans are passed through unchanged, so the normalizer rejects them with a clear reason instead of
 * this class guessing.
 */
public class CrmCustomerSource implements CustomerSource {

    static final int PAGE_SIZE = 3;

    private final CrmClient client;

    public CrmCustomerSource(CrmClient client) {
        this.client = client;
    }

    @Override
    public Stream<RawCustomer> read() {
        return Stream.iterate(client.fetchContacts(0, PAGE_SIZE), Objects::nonNull,
                        page -> page.last() ? null : client.fetchContacts(page.page() + 1, PAGE_SIZE))
                .flatMap(page -> page.contacts().stream())
                .map(CrmCustomerSource::adapt);
    }

    static RawCustomer adapt(CrmContact contact) {
        return new RawCustomer("crm contact " + contact.contactId(),
                firstNameFirst(contact.fullName()), contact.emailAddress(), tierFor(contact.plan()));
    }

    /** {@code "Carter, Ben"} → {@code "Ben Carter"}; names without a comma are kept as they are. */
    static String firstNameFirst(String fullName) {
        if (fullName == null || !fullName.contains(",")) {
            return fullName;
        }
        String[] parts = fullName.split(",", 2);
        return (parts[1].trim() + " " + parts[0].trim()).trim();
    }

    static String tierFor(String plan) {
        if (plan == null) {
            return null;
        }
        return switch (plan.trim().toUpperCase(Locale.ROOT)) {
            case "GOLD" -> "PREMIUM";
            case "SILVER", "FREE" -> "STANDARD";
            default -> plan;
        };
    }
}
