package com.example.booknest.host;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the host, which launches the real server jar over stdio, and calls the tools the way
 * Spring AI does for the model. No API key needed. Skipped until the server jar is built.
 */
@SpringBootTest
@EnabledIf("serverJarBuilt")
class McpToolCallbacksTest {

    @Autowired
    ToolCallbackProvider mcpTools;

    static boolean serverJarBuilt() {
        return Files.isRegularFile(Path.of("../booknest-mcp-server/target/booknest-mcp.jar"));
    }

    @Test
    void serverToolsBecomeToolCallbacks() {
        assertThat(Arrays.stream(mcpTools.getToolCallbacks()).map(t -> t.getToolDefinition().name()))
                .containsExactlyInAnyOrder("search_books", "get_book_price", "books_by_max_price");
    }

    @Test
    void callingACallbackSendsToolsCallToTheServer() {
        assertThat(tool("get_book_price").call("{\"bookId\":\"B3\"}")).contains("Effective Java costs Rs. 720.00");
        assertThat(tool("search_books").call("{\"query\":\"kleppmann\"}")).contains("B5");
    }

    private ToolCallback tool(String name) {
        return Arrays.stream(mcpTools.getToolCallbacks())
                .filter(t -> t.getToolDefinition().name().equals(name))
                .findFirst().orElseThrow();
    }
}
