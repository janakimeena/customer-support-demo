package edu.rag.eval;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.evaluation.FactCheckingEvaluator;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Section 7.3: a small, purpose-built fact-checking judge (bespoke-minicheck), only in the eval profile. */
@Configuration
@Profile("eval")
class EvalConfig {

    @Bean
    FactCheckingEvaluator factChecker(@Value("${spring.ai.ollama.base-url}") String ollamaUrl) {
        var minicheck = OllamaChatModel.builder()
                .ollamaApi(OllamaApi.builder().baseUrl(ollamaUrl).build())
                // Spring AI 2.0.1: options(...), not defaultOptions(...) as in the guide
                .options(OllamaChatOptions.builder()
                        .model("bespoke-minicheck")
                        .numPredict(2)      // it only needs to say Yes/No
                        .temperature(0.0)
                        .build())
                .build();
        // Spring AI 2.0.1: this factory also applies the document/claim prompt bespoke-minicheck was trained on
        return FactCheckingEvaluator.forBespokeMinicheck(ChatClient.builder(minicheck));
    }
}
