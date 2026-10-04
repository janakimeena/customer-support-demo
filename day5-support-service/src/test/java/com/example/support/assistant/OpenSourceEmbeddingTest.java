package com.example.support.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.ai.transformers.TransformersEmbeddingModel;

/**
 * The real open-source embedding model, no mocks: sentence-transformers/all-MiniLM-L6-v2 from Hugging Face,
 * running in the JVM. Downloaded once (~90 MB) into ~/.cache/spring-ai-onnx, then offline. Embeddings are
 * deterministic, so exact expectations are safe.
 */
class OpenSourceEmbeddingTest {

    private static TransformersEmbeddingModel model;

    @BeforeAll
    static void loadModel() throws Exception {
        model = new TransformersEmbeddingModel();
        model.setModelResource(
                "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx");
        model.setTokenizerResource(
                "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/tokenizer.json");
        model.setResourceCacheDirectory(System.getProperty("user.home") + "/.cache/spring-ai-onnx");
        model.afterPropertiesSet();
    }

    @AfterAll
    static void close() throws Exception {
        model.close();
    }

    @Test
    void everyTextBecomesA384NumberVector() {
        assertThat(model.embed("My refund hasn't arrived")).hasSize(384);
    }

    @Test
    void meaningBeatsSharedWords() {
        List<float[]> vectors = model.embed(List.of(
                "I want my money back",
                "How do I request a refund?",
                "Reset my password",
                "Where is my parcel?"));

        double refund = SupportAssistant.cosine(vectors.get(0), vectors.get(1));
        double password = SupportAssistant.cosine(vectors.get(0), vectors.get(2));
        double parcel = SupportAssistant.cosine(vectors.get(0), vectors.get(3));

        // No shared content word with "refund", yet it is the closest meaning.
        assertThat(refund).isGreaterThan(password).isGreaterThan(0.3);
        assertThat(refund).isGreaterThan(parcel);
    }
}
