package com.example.support.assistant;

import org.springframework.ai.chat.metadata.Usage;

/**
 * What one call cost. {@code inputTokens}/{@code outputTokens} are the provider's exact counts (what you are
 * billed for); {@code estimatedQuestionTokens} is our local estimate of the question alone, made before the call.
 * Compare them: the input is much bigger than the question because the system prompt (and, for tool calls, the
 * tool definitions and results) are sent on every request.
 */
public record TokenUsage(Integer inputTokens, Integer outputTokens, Integer totalTokens, int estimatedQuestionTokens) {

    static TokenUsage of(Usage usage, int estimatedQuestionTokens) {
        if (usage == null) {
            return new TokenUsage(null, null, null, estimatedQuestionTokens);
        }
        return new TokenUsage(usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens(),
                estimatedQuestionTokens);
    }
}
