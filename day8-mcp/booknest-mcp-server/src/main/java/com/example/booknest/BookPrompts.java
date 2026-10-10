package com.example.booknest;

import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MCP <b>prompts</b>: reusable instruction templates a user picks in the host (notes §4.3, §7.7).
 * The server only fills in the text. Whether the model follows it is up to the model.
 */
@Component
public class BookPrompts {

    @McpPrompt(name = "recommend-books",
            description = "Ask for up to three BookNest recommendations for a learner")
    public GetPromptResult recommendBooks(
            @McpArg(name = "topic", description = "What the learner is studying, for example concurrency", required = true)
            String topic,
            @McpArg(name = "level", description = "beginner, intermediate or advanced. Defaults to beginner.")
            String level) {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic is required");
        }
        String levelToUse = (level == null || level.isBlank()) ? "beginner" : level.strip();

        String text = """
                I am a %s learner studying %s.
                Recommend at most three suitable books from the BookNest catalogue.
                Use the search_books and get_book_price tools to check titles and prices.
                For each recommendation, explain briefly why it fits.
                """.formatted(levelToUse, topic.strip());

        return new GetPromptResult("Book recommendations for " + topic.strip(),
                List.of(new PromptMessage(Role.USER, new TextContent(text))));
    }
}
