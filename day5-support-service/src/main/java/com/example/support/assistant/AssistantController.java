package com.example.support.assistant;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@Tag(name = "Assistant", description = "Day 5: the first GenAI endpoints (needs a model: see the README)")
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private final SupportAssistant assistant;

    public AssistantController(SupportAssistant assistant) {
        this.assistant = assistant;
    }

    @Operation(summary = "Ask the assistant a question; the answer includes token usage")
    @PostMapping("/chat")
    public ChatAnswer chat(@Valid @RequestBody QuestionRequest request) {
        return assistant.chat(request.question());
    }

    /**
     * Server-Sent Events: one {@code data:} event per piece of text, as the model writes it. Spring MVC subscribes
     * to the {@link Flux} and writes each element to the open response. Try {@code curl -N}.
     */
    @Operation(summary = "Same as /chat, streamed as Server-Sent Events")
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@Valid @RequestBody QuestionRequest request) {
        return assistant.stream(request.question());
    }

    @Operation(summary = "Classify a customer message into category, priority and sentiment (structured output)")
    @PostMapping("/triage")
    public TicketTriage triage(@Valid @RequestBody TriageRequest request) {
        return assistant.triage(request.message());
    }

    @Operation(summary = "Ask about customers and tickets; the model looks the data up with tools")
    @PostMapping("/ask")
    public AskAnswer ask(@Valid @RequestBody QuestionRequest request) {
        return assistant.ask(request.question());
    }

    @Operation(summary = "Rank texts by meaning, using embeddings (needs Ollama with nomic-embed-text)")
    @PostMapping("/similarity")
    public SimilarityResult similarity(@Valid @RequestBody SimilarityRequest request) {
        return assistant.similarity(request.query(), request.candidates());
    }
}
