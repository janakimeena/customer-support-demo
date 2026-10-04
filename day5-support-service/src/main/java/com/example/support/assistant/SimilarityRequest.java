package com.example.support.assistant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Rank {@code candidates} by how close their meaning is to {@code query}. */
public record SimilarityRequest(
        @NotBlank @Size(max = 2_000) String query,
        @NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 2_000) String> candidates) {
}
