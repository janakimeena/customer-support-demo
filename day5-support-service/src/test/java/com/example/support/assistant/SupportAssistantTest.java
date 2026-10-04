package com.example.support.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.support.domain.TicketPriority;
import com.example.support.domain.TicketStatus;
import com.example.support.dto.TicketResponse;
import com.example.support.service.CustomerService;
import com.example.support.service.TicketNotFoundException;
import com.example.support.service.TicketService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.core.io.ClassPathResource;
import reactor.core.publisher.Flux;

/**
 * The assistant with a <b>mocked model</b>: no API key, no network, no cost, and the same answer every run. The
 * real {@link ChatClient} (built by {@link AssistantConfig}) runs on top of the mock, so these tests cover what
 * our code sends (system prompt, tools, format instructions) and how it handles what comes back (usage, refusals,
 * JSON, tool calls). Whether the real model answers <em>well</em> is a different question, answered by evals on
 * Day 7 and Day 14, not by unit tests.
 */
@ExtendWith(MockitoExtension.class)
class SupportAssistantTest {

    @Mock
    ChatModel chatModel;

    @Mock
    EmbeddingModel embeddingModel;

    @Mock
    CustomerService customers;

    @Mock
    TicketService tickets;

    @Captor
    ArgumentCaptor<Prompt> prompts;

    private SupportAssistant assistant;

    @BeforeEach
    void setUp() {
        lenient().when(chatModel.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        var properties = new AssistantProperties("Acme Support", 50, "medium");
        ChatClient chatClient = new AssistantConfig().supportChatClient(ChatClient.builder(chatModel), chatModel,
                properties, new ClassPathResource("prompts/support-system.st"));
        assistant = new SupportAssistant(chatClient, embeddingModel, new JTokkitTokenCountEstimator(), properties,
                customers, tickets, new ClassPathResource("prompts/triage.st"));
    }

    @Test
    void chatSendsSystemPromptAndQuestionAndReportsUsage() {
        when(chatModel.call(prompts.capture())).thenReturn(response("Hello! How can I help?", "end_turn"));

        ChatAnswer answer = assistant.chat("Hi there");

        assertThat(answer.answer()).isEqualTo("Hello! How can I help?");
        assertThat(answer.finishReason()).isEqualTo("end_turn");
        assertThat(answer.model()).isEqualTo("claude-opus-5-5");
        assertThat(answer.usage().inputTokens()).isEqualTo(120);
        assertThat(answer.usage().outputTokens()).isEqualTo(30);
        assertThat(answer.usage().totalTokens()).isEqualTo(150);
        assertThat(answer.usage().estimatedQuestionTokens()).isBetween(1, 5);

        List<Message> sent = prompts.getValue().getInstructions();
        assertThat(sent).extracting(Message::getMessageType).containsExactly(MessageType.SYSTEM, MessageType.USER);
        assertThat(sent.getFirst().getText()).contains("support assistant for Acme Support");
        assertThat(sent.get(1).getText()).isEqualTo("Hi there");
    }

    @Test
    void oversizedQuestionIsRejectedBeforeTheModelIsCalled() {
        String question = "Please read this very long complaint. ".repeat(20);

        assertThatThrownBy(() -> assistant.chat(question))
                .isInstanceOf(QuestionTooLargeException.class)
                .hasMessageContaining("the limit is 50");
        verifyNoInteractions(chatModel);
    }

    @Test
    void refusalIsReportedInsteadOfAnEmptyAnswer() {
        when(chatModel.call(any(Prompt.class))).thenReturn(response("", "refusal"));

        ChatAnswer answer = assistant.chat("Something the model won't do");

        assertThat(answer.finishReason()).isEqualTo("refusal");
        assertThat(answer.answer()).isEqualTo(SupportAssistant.REFUSAL_ANSWER);
    }

    @Test
    void triageParsesTheJsonReplyIntoARecord() {
        when(chatModel.call(prompts.capture())).thenReturn(response("""
                {"category": "REFUND", "priority": "HIGH", "sentiment": "ANGRY",
                 "summary": "Customer was charged twice and wants a refund",
                 "suggestedReply": "Sorry about the double charge. We are looking into it now."}
                """, "end_turn"));

        TicketTriage triage = assistant.triage("You charged me TWICE. Fix it now!");

        assertThat(triage.category()).isEqualTo(TicketTriage.Category.REFUND);
        assertThat(triage.priority()).isEqualTo(TicketPriority.HIGH);
        assertThat(triage.sentiment()).isEqualTo(TicketTriage.Sentiment.ANGRY);
        assertThat(triage.summary()).startsWith("Customer was charged twice");

        // The user message carries our instructions, the customer's text and the JSON schema to answer in.
        String userMessage = prompts.getValue().getUserMessage().getText();
        assertThat(userMessage)
                .contains("Triage this customer message")
                .contains("<message>\nYou charged me TWICE. Fix it now!\n</message>")
                .contains("suggestedReply", "ACCOUNT_ACCESS");
    }

    @Test
    void askRunsTheToolTheModelRequestsAndSendsBackTheResult() {
        when(tickets.findById(3)).thenReturn(ticket(3, "Refund status, please", TicketStatus.OPEN));
        when(chatModel.call(prompts.capture())).thenReturn(
                toolCall("getTicket", "{\"ticketId\": 3}"),
                response("Ticket 3 (Refund status, please) is still OPEN with HIGH priority.", "end_turn"));

        AskAnswer answer = assistant.ask("What is the status of ticket 3?");

        assertThat(answer.answer()).contains("Ticket 3", "OPEN");
        assertThat(answer.toolCalls()).containsExactly("getTicket(3)");
        verify(tickets).findById(3);
        verify(chatModel, times(2)).call(any(Prompt.class));

        // First request: the tools were offered to the model by name.
        assertThat(((ToolCallingChatOptions) prompts.getAllValues().getFirst().getOptions()).getToolCallbacks())
                .extracting(callback -> callback.getToolDefinition().name())
                .containsExactlyInAnyOrder("findCustomerByEmail", "listTickets", "getTicket");
        // Second request: the conversation now ends with our tool result, which the model reads.
        assertThat(lastToolResult(prompts.getAllValues().get(1))).contains("Refund status, please", "OPEN");
    }

    @Test
    void unknownTicketGoesBackToTheModelAsAnErrorItCanRepeat() {
        when(tickets.findById(99)).thenThrow(new TicketNotFoundException(99));
        when(chatModel.call(prompts.capture())).thenReturn(
                toolCall("getTicket", "{\"ticketId\": 99}"),
                response("I couldn't find ticket 99.", "end_turn"));

        AskAnswer answer = assistant.ask("What about ticket 99?");

        assertThat(answer.answer()).isEqualTo("I couldn't find ticket 99.");
        assertThat(lastToolResult(prompts.getAllValues().get(1))).contains("error", "99");
    }

    @Test
    void streamPassesTextOnPieceByPiece() {
        when(chatModel.stream(any(Prompt.class))).thenReturn(
                Flux.just(response("Hel", null), response("lo", null), response("!", "end_turn")));

        List<String> pieces = assistant.stream("Say hello").collectList().block();

        assertThat(pieces).containsExactly("Hel", "lo", "!");
    }

    @Test
    void similarityRanksCandidatesByCosine() {
        when(embeddingModel.embed(anyList())).thenReturn(List.of(
                new float[] {1f, 0f},      // query: "refund"
                new float[] {0f, 1f},      // "reset my password": unrelated
                new float[] {0.9f, 0.1f})); // "money back": close in meaning

        SimilarityResult result = assistant.similarity("refund", List.of("reset my password", "money back"));

        assertThat(result.dimensions()).isEqualTo(2);
        assertThat(result.ranking()).extracting(SimilarityResult.Match::text)
                .containsExactly("money back", "reset my password");
        assertThat(result.ranking().getFirst().score()).isCloseTo(0.994, within(0.001));
        assertThat(result.ranking().get(1).score()).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void cosineIgnoresLengthAndRejectsMismatchedVectors() {
        assertThat(SupportAssistant.cosine(new float[] {1, 2}, new float[] {2, 4})).isCloseTo(1.0, within(1e-9));
        assertThat(SupportAssistant.cosine(new float[] {1, 0}, new float[] {-1, 0})).isCloseTo(-1.0, within(1e-9));
        assertThatThrownBy(() -> SupportAssistant.cosine(new float[] {1}, new float[] {1, 2}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- helpers: what a provider's reply looks like to Spring AI ----

    private static ChatResponse response(String text, String finishReason) {
        return ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage(text),
                        ChatGenerationMetadata.builder().finishReason(finishReason).build())))
                .metadata(ChatResponseMetadata.builder()
                        .model("claude-opus-5-5")
                        .usage(new DefaultUsage(120, 30))
                        .build())
                .build();
    }

    /** The model's way of saying "run this tool with these arguments": no text, just a tool call. */
    private static ChatResponse toolCall(String tool, String argumentsJson) {
        AssistantMessage message = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", tool, argumentsJson)))
                .build();
        return ChatResponse.builder()
                .generations(List.of(new Generation(message,
                        ChatGenerationMetadata.builder().finishReason("tool_use").build())))
                .build();
    }

    private static String lastToolResult(Prompt prompt) {
        Message last = prompt.getInstructions().getLast();
        assertThat(last).isInstanceOf(ToolResponseMessage.class);
        return ((ToolResponseMessage) last).getResponses().getFirst().responseData();
    }

    private static TicketResponse ticket(long id, String subject, TicketStatus status) {
        Instant created = Instant.parse("2026-09-30T08:15:00Z");
        return new TicketResponse(id, "C-1", "Asha Rao", subject, status, TicketPriority.HIGH, created, created);
    }
}
