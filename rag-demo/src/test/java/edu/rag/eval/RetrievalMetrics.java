package edu.rag.eval;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;

import java.util.List;

/** Section 7.5: Hit@k and MRR. Deterministic and cheap, so run them on every chunking/topK/threshold change. */
public final class RetrievalMetrics {

    public record RetrievalScore(double hitAtK, double mrr) {}

    private RetrievalMetrics() {}

    public static RetrievalScore scoreRetrieval(DocumentRetriever retriever,
                                                List<GoldItem> gold, int k) {
        List<GoldItem> answerable = gold.stream().filter(GoldItem::answerable).toList();
        int hits = 0;
        double reciprocalRankSum = 0;

        for (GoldItem item : answerable) {
            List<Document> docs = retriever.retrieve(new Query(item.question()));
            List<String> ranked = docs.stream().limit(k)
                    .map(d -> String.valueOf(d.getMetadata().get("source")))
                    .toList();

            for (int i = 0; i < ranked.size(); i++) {
                if (item.expectedSources().contains(ranked.get(i))) {
                    hits++;
                    reciprocalRankSum += 1.0 / (i + 1);
                    break;
                }
            }
        }
        return new RetrievalScore(
                (double) hits / answerable.size(),
                reciprocalRankSum / answerable.size());
    }
}
