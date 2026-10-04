package com.example.support.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * Three provider starters are on the classpath; configuration decides which ChatModel and EmbeddingModel exist.
 * Exactly one of each, or the ChatClient and the services can't be wired.
 */
class ModelProviderSelectionTest {

    @Nested
    @SpringBootTest
    @ActiveProfiles({"dev", "test"})
    class DefaultIsGeminiChatWithOpenSourceEmbeddings {

        @Autowired
        ApplicationContext context;

        @Test
        void geminiAnswersHuggingFaceModelEmbeds() {
            assertThat(context.getBean(ChatModel.class)).isInstanceOf(GoogleGenAiChatModel.class);
            assertThat(context.getBeansOfType(EmbeddingModel.class)).hasSize(1)
                    .allSatisfy((name, model) -> assertThat(model).isInstanceOf(TransformersEmbeddingModel.class));
        }
    }

    @Nested
    @SpringBootTest
    @ActiveProfiles({"dev", "test", "ollama"})
    class OllamaProfileIsLocalOnly {

        @Autowired
        ApplicationContext context;

        @Test
        void ollamaAnswersAndEmbeds() {
            assertThat(context.getBean(ChatModel.class)).isInstanceOf(OllamaChatModel.class);
            assertThat(context.getBeansOfType(EmbeddingModel.class)).hasSize(1)
                    .allSatisfy((name, model) -> assertThat(model).isInstanceOf(OllamaEmbeddingModel.class));
        }
    }

    @Nested
    @SpringBootTest
    @ActiveProfiles({"dev", "test", "anthropic"})
    class AnthropicProfileSwitchesChatOnly {

        @Autowired
        ApplicationContext context;

        @Test
        void claudeAnswersHuggingFaceModelEmbeds() {
            assertThat(context.getBean(ChatModel.class)).isInstanceOf(AnthropicChatModel.class);
            assertThat(context.getBean(EmbeddingModel.class)).isInstanceOf(TransformersEmbeddingModel.class);
        }
    }
}
