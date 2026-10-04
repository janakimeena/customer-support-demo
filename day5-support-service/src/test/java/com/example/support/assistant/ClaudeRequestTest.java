package com.example.support.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * What actually goes over the wire to Claude (the optional "anthropic" profile). The whole app runs with the real Spring AI Anthropic model and the
 * official Anthropic Java SDK, but {@code spring.ai.anthropic.base-url} points at a tiny local HTTP server that
 * records each request and answers like the Messages API would. No API key, no network, no cost.
 *
 * <p>This checks our configuration end to end: model id, max_tokens and effort from application.yml, the system
 * prompt, the tool definitions, and no sampling parameters (Claude Opus 5.5 rejects {@code temperature}).
 */
@SpringBootTest
@ActiveProfiles({"dev", "test", "anthropic"})
class ClaudeRequestTest {

    private static final HttpServer FAKE_CLAUDE = start();
    private static final List<String> REQUESTS = new CopyOnWriteArrayList<>();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @DynamicPropertySource
    static void pointAnthropicAtFakeServer(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.anthropic.base-url", () -> "http://localhost:" + FAKE_CLAUDE.getAddress().getPort());
        registry.add("spring.ai.anthropic.api-key", () -> "test-key");
    }

    @Autowired
    SupportAssistant assistant;

    @BeforeEach
    void clear() {
        REQUESTS.clear();
    }

    @AfterAll
    static void stop() {
        FAKE_CLAUDE.stop(0);
    }

    @Test
    void chatSendsConfiguredModelEffortAndSystemPrompt() {
        ChatAnswer answer = assistant.chat("Hello?");

        assertThat(answer.answer()).isEqualTo("Hi from the fake Claude");
        assertThat(answer.usage().inputTokens()).isEqualTo(42);
        assertThat(answer.usage().outputTokens()).isEqualTo(7);

        JsonNode request = JSON.readTree(REQUESTS.getFirst());
        assertThat(request.path("model").asString()).isEqualTo("claude-opus-5-5");
        assertThat(request.path("max_tokens").asInt()).isEqualTo(16000);
        assertThat(request.path("output_config").path("effort").asString()).isEqualTo("medium");
        assertThat(request.has("temperature")).as("no sampling parameters for Opus 5.5").isFalse();
        assertThat(request.path("system").toString()).contains("support assistant for Acme Support");
        assertThat(request.path("messages").get(0).path("role").asString()).isEqualTo("user");
        assertThat(request.path("messages").get(0).toString()).contains("Hello?");
    }

    @Test
    void askSendsToolDefinitionsWithDescriptionsAndSchemas() {
        assistant.ask("What is the status of ticket 1?");

        JsonNode tools = JSON.readTree(REQUESTS.getFirst()).path("tools");
        assertThat(tools.valueStream().map(tool -> tool.path("name").asString()))
                .containsExactlyInAnyOrder("findCustomerByEmail", "listTickets", "getTicket");
        JsonNode getTicket = tools.valueStream()
                .filter(tool -> tool.path("name").asString().equals("getTicket")).findFirst().orElseThrow();
        assertThat(getTicket.path("description").asString()).startsWith("Get one support ticket");
        assertThat(getTicket.path("input_schema").path("properties").has("ticketId")).isTrue();
    }

    private static HttpServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/v1/messages", exchange -> {
                REQUESTS.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] body = """
                        {"id": "msg_test", "type": "message", "role": "assistant", "model": "claude-opus-5-5",
                         "content": [{"type": "text", "text": "Hi from the fake Claude"}],
                         "stop_reason": "end_turn", "stop_sequence": null,
                         "usage": {"input_tokens": 42, "output_tokens": 7}}
                        """.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
