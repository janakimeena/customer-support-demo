package com.example.support.assistant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code support.assistant.*}.
 *
 * @param companyName       filled into the system prompt template
 * @param maxQuestionTokens questions above this (estimated) size are rejected before any model call: the
 *                          context window is large, but every token is paid for and slows the answer down
 * @param effort            Claude's effort level (low, medium, high, xhigh, max). Claude Opus 5.5 defaults to
 *                          medium, so it is set explicitly here; ignored when another chat model is active
 */
@Validated
@ConfigurationProperties(prefix = "support.assistant")
public record AssistantProperties(
        @NotBlank @DefaultValue("Acme Support") String companyName,
        @Min(1) @Max(100_000) @DefaultValue("2000") int maxQuestionTokens,
        @NotBlank @DefaultValue("medium") String effort) {
}
