package com.example.support.assistant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A support agent's question. The size cap is a cheap first guard; the token budget is checked in the service. */
public record QuestionRequest(@NotBlank @Size(max = 20_000) String question) {
}
