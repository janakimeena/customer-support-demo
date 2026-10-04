package com.example.support.assistant;

import com.anthropic.models.messages.OutputConfig;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

/**
 * Builds the one {@link ChatClient} the assistant uses.
 *
 * <p>Spring AI auto-configures a {@link ChatModel} for the provider selected by {@code spring.ai.model.chat}
 * (Claude by default, Ollama with the {@code ollama} profile) and a {@code ChatClient.Builder} on top of it.
 * Everything every request shares goes in here once: the system prompt and the provider options.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AssistantProperties.class)
public class AssistantConfig {

    @Bean
    ChatClient supportChatClient(ChatClient.Builder builder, ChatModel chatModel, AssistantProperties properties,
            @Value("classpath:prompts/support-system.st") Resource systemPrompt) {
        builder.defaultSystem(system -> system.text(systemPrompt).param("company", properties.companyName()));
        // Provider-specific options only for the provider they belong to, so the ollama profile still works.
        // Model and max-tokens come from spring.ai.anthropic.chat.* in application.yml.
        if (chatModel instanceof AnthropicChatModel) {
            builder.defaultOptions(AnthropicChatOptions.builder()
                    .effort(OutputConfig.Effort.of(properties.effort().toLowerCase())));
        }
        return builder.build();
    }

    /**
     * Counts tokens locally, without calling the model. JTokkit implements OpenAI's tokenizers, so for Claude it
     * is an estimate (typically within 10-30%); the exact count comes back in the response's usage metadata.
     */
    @Bean
    TokenCountEstimator tokenCountEstimator() {
        return new JTokkitTokenCountEstimator();
    }
}
