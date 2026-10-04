package com.example.support.assistant;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.genai.errors.ClientException;
import com.google.genai.errors.ServerException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;

/** HTTP layer only: validation, JSON shape, SSE, and how model failures become problem responses. */
@WebMvcTest(AssistantController.class)
class AssistantControllerTest {

    private static final String QUESTION = """
            {"question": "Hi"}
            """;

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    SupportAssistant assistant;

    @Test
    void chatReturnsAnswerWithUsage() throws Exception {
        when(assistant.chat("Hi")).thenReturn(new ChatAnswer("Hello!", "claude-opus-5-5", "end_turn",
                new TokenUsage(120, 30, 150, 1)));

        mockMvc.perform(post("/api/assistant/chat").contentType(MediaType.APPLICATION_JSON).content(QUESTION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Hello!"))
                .andExpect(jsonPath("$.usage.inputTokens").value(120))
                .andExpect(jsonPath("$.usage.estimatedQuestionTokens").value(1));
    }

    @Test
    void blankQuestionIs400WithoutCallingTheModel() throws Exception {
        mockMvc.perform(post("/api/assistant/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": "  "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.question").exists());
        verifyNoInteractions(assistant);
    }

    @Test
    void questionOverTheTokenBudgetIs400() throws Exception {
        when(assistant.chat(anyString())).thenThrow(new QuestionTooLargeException(2500, 2000));

        mockMvc.perform(post("/api/assistant/chat").contentType(MediaType.APPLICATION_JSON).content(QUESTION))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Question too large"))
                .andExpect(jsonPath("$.detail").value("The question is about 2500 tokens; the limit is 2000"));
    }

    @Test
    void unreachableModelIs503AndRejectedCallIs502() throws Exception {
        when(assistant.ask(anyString())).thenThrow(new TransientAiException("connection refused"));
        mockMvc.perform(post("/api/assistant/ask").contentType(MediaType.APPLICATION_JSON).content(QUESTION))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Model unreachable"));

        when(assistant.chat(anyString())).thenThrow(new NonTransientAiException("model not found"));
        mockMvc.perform(post("/api/assistant/chat").contentType(MediaType.APPLICATION_JSON).content(QUESTION))
                .andExpect(status().isBadGateway())
                // The provider's own message stays in the log.
                .andExpect(jsonPath("$.detail").value("The model could not answer this request"));
    }

    @Test
    void geminiErrorsMapToTheSameProblemsAsOtherProviders() throws Exception {
        // doThrow: re-stubbing with when(assistant.chat(...)) would call the mock and hit the previous stub.
        doThrow(new ClientException(400, "INVALID_ARGUMENT", "API key not valid. Please pass a valid API key."))
                .when(assistant).chat(anyString());
        mockMvc.perform(post("/api/assistant/chat").contentType(MediaType.APPLICATION_JSON).content(QUESTION))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Model not configured"));

        doThrow(new ClientException(429, "RESOURCE_EXHAUSTED", "Quota exceeded")).when(assistant).chat(anyString());
        mockMvc.perform(post("/api/assistant/chat").contentType(MediaType.APPLICATION_JSON).content(QUESTION))
                .andExpect(status().isTooManyRequests());

        doThrow(new ServerException(503, "UNAVAILABLE", "The model is overloaded")).when(assistant).chat(anyString());
        mockMvc.perform(post("/api/assistant/chat").contentType(MediaType.APPLICATION_JSON).content(QUESTION))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("The model could not answer this request"));
    }

    @Test
    void streamIsServerSentEvents() throws Exception {
        when(assistant.stream("Hi")).thenReturn(Flux.just("Hel", "lo"));

        MvcResult started = mockMvc.perform(post("/api/assistant/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON).content(QUESTION))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string("data:Hel\n\ndata:lo\n\n"));
    }
}
