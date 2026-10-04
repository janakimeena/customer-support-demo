package com.example.support.ingest.crm;

import java.util.List;

/** One page of the CRM's {@code GET /contacts} response. */
public record CrmPage(List<CrmContact> contacts, int page, boolean last) {

    public CrmPage {
        contacts = contacts == null ? List.of() : List.copyOf(contacts);
    }
}
