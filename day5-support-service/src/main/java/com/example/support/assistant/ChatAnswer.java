package com.example.support.assistant;

/**
 * @param finishReason why the model stopped: normally the end of its turn; "max_tokens" means the answer was cut
 *                     off, "refusal" means the model declined
 */
public record ChatAnswer(String answer, String model, String finishReason, TokenUsage usage) {
}
