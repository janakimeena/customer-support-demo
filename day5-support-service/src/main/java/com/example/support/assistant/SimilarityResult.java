package com.example.support.assistant;

import java.util.List;

/**
 * @param dimensions length of each embedding vector (768 for nomic-embed-text)
 * @param ranking    candidates, most similar first; {@code score} is the cosine similarity, from -1 to 1
 */
public record SimilarityResult(String query, int dimensions, List<Match> ranking) {

    public record Match(String text, double score) {
    }
}
