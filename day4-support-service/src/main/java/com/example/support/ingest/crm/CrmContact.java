package com.example.support.ingest.crm;

/**
 * A contact as the partner CRM's JSON API returns it. Its vocabulary differs from ours: "Last, First" names,
 * {@code emailAddress} instead of {@code email}, and plans named GOLD / SILVER / FREE instead of tiers.
 */
public record CrmContact(long contactId, String fullName, String emailAddress, String plan) {
}
