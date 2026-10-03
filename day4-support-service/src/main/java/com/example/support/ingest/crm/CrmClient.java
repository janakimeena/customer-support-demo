package com.example.support.ingest.crm;

import org.springframework.web.client.RestClient;

/**
 * Thin client for the partner CRM API: it speaks the CRM's language ({@link CrmContact}, pages) and knows
 * nothing about our customers. In the <b>Adapter</b> pattern this is the "adaptee", the existing interface
 * we can't (or don't want to) change; think of it as a vendor SDK. {@link CrmCustomerSource} adapts it.
 */
public class CrmClient {

    private final RestClient restClient;

    /** @param restClient already configured with the CRM base URL */
    public CrmClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public CrmPage fetchContacts(int page, int size) {
        return restClient.get()
                .uri("/contacts?page={page}&size={size}", page, size)
                .retrieve()
                .body(CrmPage.class);
    }
}
