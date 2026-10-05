package edu.rag.eval;

import edu.rag.eval.RetrievalMetrics.RetrievalScore;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.evaluation.FactCheckingEvaluator;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static edu.rag.eval.RetrievalMetrics.scoreRetrieval;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Section 7.6: every gold item, every layer, one report. Fails the build if a metric drops below its threshold.
 * Makes real model calls, so it only runs with ./mvnw test -Peval.
 */
@SpringBootTest
@ActiveProfiles("eval")
@Tag("eval")
class RagEvaluationSuite {

    @Autowired @Qualifier("modularRagClient") ChatClient ragClient;
    @Autowired VectorStore vectorStore;
    // Same model as the generator here, for a zero-cost lab. Best practice: a different or stronger judge.
    @Autowired ChatModel judgeModel;
    @Autowired FactCheckingEvaluator factChecker;
    @Autowired ObjectMapper mapper;

    @Test
    void evaluateGoldSet() throws Exception {
        List<GoldItem> gold = mapper.readValue(
                new ClassPathResource("eval/gold-set.json").getInputStream(),
                new TypeReference<>() {});

        // --- Retrieval ---
        var retriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore).topK(5).similarityThreshold(0.5).build();
        RetrievalScore rs = scoreRetrieval(retriever, gold, 5);

        // --- Generation ---
        var relevancy = new RelevancyEvaluator(ChatClient.builder(judgeModel));
        var correctness = new CorrectnessEvaluator(ChatClient.builder(judgeModel), 4);
        int relPass = 0, faithPass = 0, corrPass = 0, refusals = 0;
        int answerable = 0, unanswerable = 0;

        for (GoldItem item : gold) {
            ChatResponse resp = ragClient.prompt().user(item.question()).call().chatResponse();
            String answer = resp.getResult().getOutput().getText();
            List<Document> ctx = resp.getMetadata()
                    .getOrDefault(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT, List.of());

            if (!item.answerable()) {
                unanswerable++;
                if (isRefusal(answer)) refusals++;
                continue;
            }
            answerable++;
            var req = new EvaluationRequest(item.question(), ctx, answer);
            if (relevancy.evaluate(req).isPass()) relPass++;
            if (factChecker.evaluate(req).isPass()) faithPass++;
            if (correctness.evaluate(new EvaluationRequest(item.question(),
                    List.of(new Document(item.referenceAnswer())), answer)).isPass()) corrPass++;
        }

        double relevancyRate = (double) relPass / answerable;
        double faithfulness = (double) faithPass / answerable;
        double correctnessRate = (double) corrPass / answerable;
        double refusalRate = unanswerable == 0 ? 1.0 : (double) refusals / unanswerable;

        System.out.printf("""
                ===== RAG EVALUATION =====
                Hit@5        : %.2f
                MRR          : %.2f
                Relevancy    : %.2f
                Faithfulness : %.2f
                Correctness  : %.2f
                Refusal rate : %.2f
                %n""", rs.hitAtK(), rs.mrr(), relevancyRate, faithfulness,
                correctnessRate, refusalRate);

        // Starting points: calibrate on your first baseline run, then treat any drop as a regression.
        assertThat(rs.hitAtK()).isGreaterThanOrEqualTo(0.85);
        assertThat(faithfulness).isGreaterThanOrEqualTo(0.90);
        assertThat(correctnessRate).isGreaterThanOrEqualTo(0.75);
        assertThat(refusalRate).isGreaterThanOrEqualTo(0.80);
    }

    /**
     * Two refusal paths: our system prompt ("I don't know based on the course material"), and the
     * ContextualQueryAugmenter's built-in reply when nothing is retrieved ("...outside of my knowledge base...").
     * Models also use the curly apostrophe, so normalise it first.
     */
    private static boolean isRefusal(String answer) {
        String a = answer.toLowerCase().replace('’', '\'');
        return a.contains("don't know") || a.contains("outside of my knowledge") || a.contains("can't answer");
    }
}
