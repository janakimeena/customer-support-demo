package edu.rag.eval;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Section 7.2: one question, judged for relevancy to the query and the retrieved context. */
@SpringBootTest
@Tag("eval")
class RelevancyEvalTest {

    @Autowired @Qualifier("modularRagClient") ChatClient ragClient;
    @Autowired ChatModel chatModel;

    @Test
    void answerIsRelevantToContext() {
        String question = "Explain the difference between HashMap and TreeMap.";

        ChatResponse response = ragClient.prompt().user(question).call().chatResponse();
        List<Document> context = response.getMetadata()
                .get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT);

        var request = new EvaluationRequest(
                question,
                context,
                response.getResult().getOutput().getText());

        var evaluator = new RelevancyEvaluator(ChatClient.builder(chatModel));
        EvaluationResponse result = evaluator.evaluate(request);

        assertThat(result.isPass())
                .as("Judge feedback: %s", result.getFeedback())
                .isTrue();
    }
}
