package edu.rag.config;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Section 6.2: sort by score, drop duplicate texts, keep the best n. Also the hook for a re-ranker. */
public class TopNPostProcessor implements DocumentPostProcessor {

    private final int n;

    public TopNPostProcessor(int n) { this.n = n; }

    @Override
    public List<Document> process(Query query, List<Document> documents) {
        Map<String, Document> unique = new LinkedHashMap<>();
        documents.stream()
                .sorted(Comparator.comparing(
                        (Document d) -> d.getScore() == null ? 0.0 : d.getScore()).reversed())
                .forEach(d -> unique.putIfAbsent(d.getText().strip(), d));
        return unique.values().stream().limit(n).toList();
    }
}
