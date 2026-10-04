package com.example.support.assistant;

import com.example.support.service.CustomerService;
import com.example.support.service.TicketService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * The Day 5 GenAI features, one method each:
 * <ul>
 *   <li>{@link #chat}: a plain prompt, with the token usage the provider reports;</li>
 *   <li>{@link #stream}: the same, streamed token by token;</li>
 *   <li>{@link #triage}: structured output, the reply parsed into a {@link TicketTriage};</li>
 *   <li>{@link #ask}: tool calling, the model looks customers and tickets up through {@link SupportTools};</li>
 *   <li>{@link #similarity}: embeddings, meaning compared as vectors.</li>
 * </ul>
 * The {@link ChatClient} (system prompt, provider options) is built once in {@link AssistantConfig}.
 */
@Service
public class SupportAssistant {

    static final String REFUSAL_ANSWER = "The model declined to answer this request.";

    private static final Logger log = LoggerFactory.getLogger(SupportAssistant.class);

    private final ChatClient chatClient;
    private final EmbeddingModel embeddingModel;
    private final TokenCountEstimator tokenEstimator;
    private final AssistantProperties properties;
    private final CustomerService customers;
    private final TicketService tickets;
    private final Resource triagePrompt;

    public SupportAssistant(ChatClient chatClient, EmbeddingModel embeddingModel, TokenCountEstimator tokenEstimator,
            AssistantProperties properties, CustomerService customers, TicketService tickets,
            @Value("classpath:prompts/triage.st") Resource triagePrompt) {
        this.chatClient = chatClient;
        this.embeddingModel = embeddingModel;
        this.tokenEstimator = tokenEstimator;
        this.properties = properties;
        this.customers = customers;
        this.tickets = tickets;
        this.triagePrompt = triagePrompt;
    }

    public ChatAnswer chat(String question) {
        int estimated = checkBudget(question);
        ChatResponse response = chatClient.prompt().user(question).call().chatResponse();
        String finishReason = finishReason(response);
        log.info("chat: finish={} usage={}", finishReason, response.getMetadata().getUsage());
        return new ChatAnswer(answerText(response, finishReason), response.getMetadata().getModel(), finishReason,
                TokenUsage.of(response.getMetadata().getUsage(), estimated));
    }

    /** Same prompt as {@link #chat}, but each piece of text is passed on as soon as the model produces it. */
    public Flux<String> stream(String question) {
        checkBudget(question);
        return chatClient.prompt().user(question).stream().content();
    }

    public TicketTriage triage(String message) {
        checkBudget(message);
        return chatClient.prompt()
                .user(user -> user.text(triagePrompt).param("message", message))
                .call()
                .entity(TicketTriage.class);
    }

    public AskAnswer ask(String question) {
        int estimated = checkBudget(question);
        SupportTools tools = new SupportTools(customers, tickets);
        ChatResponse response = chatClient.prompt().user(question).tools(tools).call().chatResponse();
        String finishReason = finishReason(response);
        log.info("ask: tools={} finish={} usage={}", tools.calls(), finishReason, response.getMetadata().getUsage());
        return new AskAnswer(answerText(response, finishReason), tools.calls(), finishReason,
                TokenUsage.of(response.getMetadata().getUsage(), estimated));
    }

    public SimilarityResult similarity(String query, List<String> candidates) {
        List<String> texts = new ArrayList<>(candidates.size() + 1);
        texts.add(query);
        texts.addAll(candidates);
        // One request for all texts: the embedding model turns each into a vector of the same length.
        List<float[]> vectors = embeddingModel.embed(texts);
        float[] queryVector = vectors.getFirst();
        List<SimilarityResult.Match> ranking = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            ranking.add(new SimilarityResult.Match(candidates.get(i), cosine(queryVector, vectors.get(i + 1))));
        }
        ranking.sort(Comparator.comparingDouble(SimilarityResult.Match::score).reversed());
        return new SimilarityResult(query, queryVector.length, ranking);
    }

    /**
     * Cosine similarity: the angle between two vectors, ignoring their length. 1 means the same direction (same
     * meaning), around 0 means unrelated. Day 6's pgvector computes exactly this inside the database.
     */
    static double cosine(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vectors differ in length: " + a.length + " vs " + b.length);
        }
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return normA == 0 || normB == 0 ? 0 : dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /** Estimates the size locally and rejects oversized input before anything is sent (or paid for). */
    private int checkBudget(String text) {
        int estimated = tokenEstimator.estimate(text);
        if (estimated > properties.maxQuestionTokens()) {
            throw new QuestionTooLargeException(estimated, properties.maxQuestionTokens());
        }
        return estimated;
    }

    private static String finishReason(ChatResponse response) {
        Generation result = response.getResult();
        return result == null ? null : result.getMetadata().getFinishReason();
    }

    /**
     * Always check why the model stopped before using its text: a refusal may come back with no text at all, and
     * "max_tokens" means the text stops mid-sentence.
     */
    private static String answerText(ChatResponse response, String finishReason) {
        if (finishReason != null && finishReason.toLowerCase(Locale.ROOT).contains("refusal")) {
            return REFUSAL_ANSWER;
        }
        Generation result = response.getResult();
        return result == null ? "" : result.getOutput().getText();
    }
}
