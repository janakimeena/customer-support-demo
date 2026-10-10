package com.example.booknest.host;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.GetPromptRequest;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The MCP connection with no model involved (notes §8.2: "test the MCP connection separately
 * from the model call"). Each endpoint is one MCP request made through the SDK client.
 *
 * <p>There is one {@link McpSyncClient} per configured server: one host, N clients, N servers.
 */
@RestController
@RequestMapping("/api/mcp")
class McpConnectionController {

    private final List<McpSyncClient> clients;

    McpConnectionController(List<McpSyncClient> clients) {
        this.clients = clients;
    }

    /** What each server said in its initialize response. */
    @GetMapping("/servers")
    List<Map<String, Object>> servers() {
        return clients.stream().map(client -> {
            Map<String, Object> server = new LinkedHashMap<>();
            server.put("serverInfo", client.getServerInfo());
            server.put("capabilities", client.getServerCapabilities());
            server.put("instructions", client.getServerInstructions());
            server.put("clientInfo", client.getClientInfo());
            return server;
        }).toList();
    }

    @GetMapping("/tools")
    Object tools() {
        return booknest().listTools().tools();
    }

    @GetMapping("/resources")
    Map<String, Object> resources() {
        return Map.of(
                "resources", booknest().listResources().resources(),
                "resourceTemplates", booknest().listResourceTemplates().resourceTemplates());
    }

    @GetMapping("/resources/read")
    ReadResourceResult readResource(@RequestParam String uri) {
        return booknest().readResource(new ReadResourceRequest(uri));
    }

    @GetMapping("/prompts")
    Object prompts() {
        return booknest().listPrompts().prompts();
    }

    /** Every query parameter becomes a prompt argument, e.g. ?topic=java&level=beginner */
    @GetMapping("/prompts/{name}")
    GetPromptResult getPrompt(@PathVariable String name, @RequestParam Map<String, String> arguments) {
        return booknest().getPrompt(new GetPromptRequest(name, new HashMap<>(arguments)));
    }

    private McpSyncClient booknest() {
        if (clients.isEmpty()) {
            throw new IllegalStateException("No MCP server connected. Check spring.ai.mcp.client.stdio.connections");
        }
        return clients.getFirst();
    }
}
