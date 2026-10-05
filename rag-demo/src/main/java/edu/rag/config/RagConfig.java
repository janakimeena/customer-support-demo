package edu.rag.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Three RAG clients over the same vector store, from simplest to most capable (sections 5 and 6). */
@Configuration
public class RagConfig {

    public static final String SYSTEM_PROMPT = """
            You are a teaching assistant for a Java and GenAI course.
            Answer ONLY from the provided context. If the context does not
            contain the answer, say "I don't know based on the course material."
            Keep answers under 150 words.
            """;

    // 5.1 Option A: QuestionAnswerAdvisor (plain similarity search)
    @Bean
    ChatClient simpleRagClient(ChatClient.Builder builder, VectorStore vectorStore) {
        var qaAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder()
                        .topK(5)
                        .similarityThreshold(0.5)
                        .build())
                .build();

        return builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(qaAdvisor, new SimpleLoggerAdvisor())
                .build();
    }

    // 5.2 Option B: RetrievalAugmentationAdvisor (naive modular RAG)
    @Bean
    ChatClient modularRagClient(ChatClient.Builder builder, VectorStore vectorStore) {
        Advisor rag = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        .vectorStore(vectorStore)
                        .similarityThreshold(0.5)
                        .topK(5)
                        .build())
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .allowEmptyContext(false)   // refuse instead of guessing
                        .build())
                .build();

        return builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(rag, new SimpleLoggerAdvisor())
                .build();
    }

    // 6.1 Advanced: compression + rewrite + multi-query + dedupe, with chat memory for follow-ups
    @Bean
    ChatClient advancedRagClient(ChatClient.Builder builder, VectorStore vectorStore,
                                 ChatMemory chatMemory) {

        // Deterministic client for query transformation (low temperature matters).
        // Spring AI 2.0.1 takes the options builder here, not built ChatOptions as in the guide.
        ChatClient.Builder toolBuilder = builder.build().mutate()
                .defaultOptions(ChatOptions.builder().temperature(0.0));

        MultiQueryExpander multiQuery = MultiQueryExpander.builder()
                .chatClientBuilder(toolBuilder)
                .numberOfQueries(3)
                .includeOriginal(true)
                .build();
        // Not in the guide: when the model echoes the original question as one of its variants, the advisor
        // fails with "IllegalStateException: Duplicate key Query[...]" (it maps query -> documents).
        QueryExpander distinctQueries = query -> multiQuery.expand(query).stream().distinct().toList();

        Advisor rag = RetrievalAugmentationAdvisor.builder()
                // PRE-RETRIEVAL
                .queryTransformers(
                        CompressionQueryTransformer.builder().chatClientBuilder(toolBuilder).build(),
                        RewriteQueryTransformer.builder().chatClientBuilder(toolBuilder).build())
                .queryExpander(distinctQueries)
                // RETRIEVAL
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        .vectorStore(vectorStore)
                        .similarityThreshold(0.45)
                        .topK(6)
                        .build())
                // POST-RETRIEVAL
                .documentPostProcessors(new TopNPostProcessor(5))
                // GENERATION
                .queryAugmenter(ContextualQueryAugmenter.builder().allowEmptyContext(false).build())
                .build();

        return builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),   // history first
                        rag,
                        new SimpleLoggerAdvisor())
                .build();
    }

    @Bean
    ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder().maxMessages(10).build();
    }
}
