package com.example.support.assistant;

/** The question is over the configured token budget; rejected before any (paid) model call. */
public class QuestionTooLargeException extends RuntimeException {

    public QuestionTooLargeException(int estimatedTokens, int maxTokens) {
        super("The question is about %d tokens; the limit is %d".formatted(estimatedTokens, maxTokens));
    }
}
