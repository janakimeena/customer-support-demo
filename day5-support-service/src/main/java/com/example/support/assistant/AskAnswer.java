package com.example.support.assistant;

import java.util.List;

/**
 * Answer to a question the model may have answered by calling our tools.
 *
 * @param toolCalls the tools the model asked us to run, in order, e.g. {@code findCustomerByEmail(asha@...)}
 */
public record AskAnswer(String answer, List<String> toolCalls, String finishReason, TokenUsage usage) {
}
