package com.example.booknest.host;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.GetPromptRequest;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The host putting the three MCP primitives in front of a model:
 * <ul>
 *   <li><b>tools</b>: offered to the model, which decides whether to call them;</li>
 *   <li><b>resource</b>: read by the host and attached as context when the user ticks the box;</li>
 *   <li><b>prompt</b>: fetched by the host and sent as the user message.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/chat")
class ChatController {

    // Boolean, not boolean: Jackson 3 rejects a request that leaves out a primitive field.
    record ChatRequest(String message, Boolean attachReturnPolicy) {}

    record RecommendRequest(String topic, String level) {}

    record ChatResponse(String userMessage, List<String> toolsOffered, String attachedContext,
                        List<TracingToolCallback.ToolCall> toolCalls, String answer) {}

    private final ChatClient chatClient;
    private final ToolCallbackProvider mcpTools;
    private final List<McpSyncClient> clients;
    private final boolean modelConfigured;

    ChatController(ChatClient.Builder builder, ToolCallbackProvider mcpTools, List<McpSyncClient> clients,
            @Value("${spring.ai.google.genai.api-key:}") String apiKey) {
        this.chatClient = builder
                .defaultSystem("""
                        You are the BookNest shop assistant. Answer only from BookNest tool results and any
                        attached policy text. Prices are in Indian rupees. If a tool says a book is not found,
                        say so; do not guess.
                        """)
                .build();
        this.mcpTools = mcpTools;
        this.clients = clients;
        this.modelConfigured = apiKey != null && !apiKey.isBlank() && !apiKey.equals("missing-gemini-api-key");
    }

    @PostMapping
    ChatResponse chat(@RequestBody ChatRequest request) {
        if (request.message() == null || request.message().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message is required");
        }
        String context = Boolean.TRUE.equals(request.attachReturnPolicy()) ? readResource("booknest://policies/returns") : null;
        return ask(request.message(), context);
    }

    /** Uses the server's recommend-books prompt as the user message. */
    @PostMapping("/recommend")
    ChatResponse recommend(@RequestBody RecommendRequest request) {
        if (request.topic() == null || request.topic().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "topic is required");
        }
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topic", request.topic());
        if (request.level() != null && !request.level().isBlank()) {
            arguments.put("level", request.level());
        }
        GetPromptResult prompt = booknest().getPrompt(new GetPromptRequest("recommend-books", arguments));
        String text = prompt.messages().stream()
                .map(message -> message.content() instanceof TextContent t ? t.text() : "")
                .collect(Collectors.joining("\n"));
        return ask(text, null);
    }

    private ChatResponse ask(String userMessage, String attachedContext) {
        if (!modelConfigured) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Model not configured: set GEMINI_API_KEY. The /api/mcp endpoints work without it.");
        }
        // Fresh wrappers per request, so each response carries only its own tool calls.
        List<TracingToolCallback.ToolCall> trace = new ArrayList<>();
        List<ToolCallback> tools = Arrays.stream(mcpTools.getToolCallbacks())
                .<ToolCallback>map(tool -> new TracingToolCallback(tool, trace))
                .toList();

        ChatClient.ChatClientRequestSpec spec = chatClient.prompt().user(userMessage).toolCallbacks(tools);
        if (attachedContext != null) {
            spec = spec.system(s -> s.text("""
                    You are the BookNest shop assistant. Answer only from BookNest tool results and the
                    policy below. Prices are in Indian rupees.

                    BookNest return policy (MCP resource booknest://policies/returns):
                    {policy}
                    """).param("policy", attachedContext));
        }
        String answer = spec.call().content();

        List<String> offered = tools.stream().map(t -> t.getToolDefinition().name()).toList();
        return new ChatResponse(userMessage, offered, attachedContext, trace, answer);
    }

    private String readResource(String uri) {
        return booknest().readResource(new ReadResourceRequest(uri)).contents().stream()
                .map(c -> c instanceof TextResourceContents t ? t.text() : "")
                .collect(Collectors.joining("\n"));
    }

    private McpSyncClient booknest() {
        return clients.getFirst();
    }
}
