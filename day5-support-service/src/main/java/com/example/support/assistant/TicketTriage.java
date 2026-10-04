package com.example.support.assistant;

import com.example.support.domain.TicketPriority;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * <b>Structured output</b>: the model fills in this record instead of writing free text. Spring AI turns the
 * record into a JSON schema (field names, enum values, the descriptions below), asks the model to answer in that
 * shape, and parses the reply back into a typed object. Enums restrict the model to values our code understands:
 * {@code priority} reuses the Day 3 {@link TicketPriority}.
 */
public record TicketTriage(
        @JsonPropertyDescription("What the customer needs help with") Category category,
        @JsonPropertyDescription("How urgently support should respond") TicketPriority priority,
        @JsonPropertyDescription("The customer's mood") Sentiment sentiment,
        @JsonPropertyDescription("One sentence, at most 20 words, for the ticket subject line") String summary,
        @JsonPropertyDescription("A short, friendly first reply to send to the customer") String suggestedReply) {

    public enum Category { BILLING, REFUND, DELIVERY, ACCOUNT_ACCESS, PRODUCT_QUESTION, OTHER }

    public enum Sentiment { POSITIVE, NEUTRAL, NEGATIVE, ANGRY }
}
