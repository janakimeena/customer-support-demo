package com.example.booknest.host;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;

/**
 * Wraps an MCP-backed tool so the host can show what the model asked for.
 * The model only <i>requests</i> a call; this host-side code is what actually sends
 * {@code tools/call} to the server (notes §3, "important nuance").
 */
class TracingToolCallback implements ToolCallback {

    record ToolCall(String tool, String arguments, String result) {}

    private final ToolCallback delegate;
    private final List<ToolCall> trace;

    TracingToolCallback(ToolCallback delegate, List<ToolCall> trace) {
        this.delegate = delegate;
        this.trace = trace;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String result = delegate.call(toolInput, toolContext);
        trace.add(new ToolCall(getToolDefinition().name(), toolInput, result));
        return result;
    }
}
