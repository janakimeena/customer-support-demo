package edu.rag.eval;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Hit@k and MRR on a fake retriever: no database or model needed. */
class RetrievalMetricsTest {

    private static Document from(String source) {
        return new Document("text", Map.of("source", source));
    }

    @Test
    void computesHitAtKAndMrr() {
        DocumentRetriever retriever = query -> switch (query.text()) {
            case "first" -> List.of(from("a.md"), from("x.md"));            // rank 1 -> 1.0
            case "second" -> List.of(from("x.md"), from("b.md"));           // rank 2 -> 0.5
            default -> List.of(from("x.md"), from("y.md"));                 // miss   -> 0
        };
        var gold = List.of(
                new GoldItem("1", "first", List.of("a.md"), "", true),
                new GoldItem("2", "second", List.of("b.md"), "", true),
                new GoldItem("3", "third", List.of("c.md"), "", true),
                new GoldItem("4", "off topic", List.of(), "", false));     // ignored

        var score = RetrievalMetrics.scoreRetrieval(retriever, gold, 5);

        assertThat(score.hitAtK()).isCloseTo(2.0 / 3, within(1e-9));
        assertThat(score.mrr()).isCloseTo(1.5 / 3, within(1e-9));
    }
}
