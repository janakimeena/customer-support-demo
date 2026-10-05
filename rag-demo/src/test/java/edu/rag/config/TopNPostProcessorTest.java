package edu.rag.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TopNPostProcessorTest {

    private static Document doc(String text, double score) {
        return Document.builder().text(text).score(score).build();
    }

    @Test
    void sortsByScoreDropsDuplicatesAndKeepsTopN() {
        var docs = List.of(doc("low", 0.5), doc("best", 0.9), doc("  best ", 0.8), doc("mid", 0.7));

        List<Document> result = new TopNPostProcessor(2).process(new Query("q"), docs);

        assertThat(result).extracting(Document::getText).containsExactly("best", "mid");
    }
}
