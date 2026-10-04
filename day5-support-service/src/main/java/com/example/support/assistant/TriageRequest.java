package com.example.support.assistant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A raw customer message, e.g. the body of an incoming email. */
public record TriageRequest(@NotBlank @Size(max = 20_000) String message) {
}
