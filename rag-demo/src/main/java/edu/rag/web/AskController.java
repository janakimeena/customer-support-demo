package edu.rag.web;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/** Section 5.3: answer + the retrieved sources, so every answer is auditable. */
@RestController
@RequestMapping("/api")
public class AskController {

    private final Map<String, ChatClient> clients;

    public AskController(@Qualifier("simpleRagClient") ChatClient simple,
                         @Qualifier("modularRagClient") ChatClient modular,
                         @Qualifier("advancedRagClient") ChatClient advanced) {
        this.clients = Map.of("simple", simple, "modular", modular, "advanced", advanced);
    }

    /**
     * @param question       required
     * @param mode           simple | modular (default) | advanced
     * @param conversationId advanced mode only: groups follow-up questions ("how does it handle collisions?")
     * @param filter         optional metadata filter, e.g. "unit == 2" (modular and advanced modes)
     */
    public record AskRequest(String question, String mode, String conversationId, String filter) {}

    public record Source(String source, Object page, double score, String excerpt) {}
    public record Answer(String mode, String answer, List<Source> sources) {}

    @PostMapping("/ask")
    public Answer ask(@RequestBody AskRequest body) {
        if (body.question() == null || body.question().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "question is required");
        }
        String mode = body.mode() == null ? "modular" : body.mode();
        ChatClient chatClient = clients.get(mode);
        if (chatClient == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mode must be one of " + clients.keySet());
        }

        ChatResponse response = chatClient.prompt()
                .user(body.question())
                .advisors(a -> {
                    a.param(ChatMemory.CONVERSATION_ID,
                            body.conversationId() == null ? "default" : body.conversationId());
                    if (body.filter() != null && !body.filter().isBlank()) {
                        a.param(VectorStoreDocumentRetriever.FILTER_EXPRESSION, body.filter());
                    }
                })
                .call()
                .chatResponse();

        return new Answer(mode, response.getResult().getOutput().getText(), sources(response));
    }

    /** RetrievalAugmentationAdvisor and QuestionAnswerAdvisor store the retrieved documents under different keys. */
    @SuppressWarnings("unchecked")
    static List<Source> sources(ChatResponse response) {
        Object docs = response.getMetadata().get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT);
        if (docs == null) {
            docs = response.getMetadata().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);
        }
        if (docs == null) {
            return List.of();
        }
        return ((List<Document>) docs).stream()
                .map(d -> new Source(
                        String.valueOf(d.getMetadata().get("source")),
                        d.getMetadata().get("page_number"),
                        d.getScore() == null ? 0.0 : d.getScore(),
                        d.getText().substring(0, Math.min(200, d.getText().length()))))
                .toList();
    }
}
