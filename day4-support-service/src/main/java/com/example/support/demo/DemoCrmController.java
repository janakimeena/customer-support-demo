package com.example.support.demo;

import com.example.support.ingest.crm.CrmContact;
import com.example.support.ingest.crm.CrmPage;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stand-in for the partner CRM, so the {@code partner-crm} source has something to call without another
 * service or the internet. Dev profile only. Its JSON deliberately looks nothing like our API: that is the
 * point of the adapter.
 */
@Profile("dev")
@Tag(name = "Demo partner CRM", description = "Fake external API used by the partner-crm ingestion source (dev only)")
@RestController
@RequestMapping("/demo/crm")
public class DemoCrmController {

    private static final List<CrmContact> CONTACTS = List.of(
            new CrmContact(7001, "Carter, Ben", "ben.carter@example.com", "GOLD"),
            new CrmContact(7002, "Lopez, Dana", "dana.lopez@example.com", "GOLD"),
            new CrmContact(7003, "Mensah, Leo", "leo.mensah@example.com", "SILVER"),
            new CrmContact(7004, "Nair, Maya", "Maya.Nair@Example.com", "FREE"),
            new CrmContact(7005, "Ortiz, Noah", "", "GOLD"),
            new CrmContact(7006, "Patel, Olivia", "olivia.patel@example.com", "PLATINUM"),
            new CrmContact(7007, "Quinn, Pat", "pat.quinn@example.com", "GOLD"));

    @GetMapping("/contacts")
    public CrmPage contacts(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "3") @Min(1) @Max(100) int size) {
        int from = Math.min(page * size, CONTACTS.size());
        int to = Math.min(from + size, CONTACTS.size());
        return new CrmPage(CONTACTS.subList(from, to), page, to >= CONTACTS.size());
    }
}
