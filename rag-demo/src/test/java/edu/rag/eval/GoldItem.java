package edu.rag.eval;

import java.util.List;

/** One hand-written question from src/test/resources/eval/gold-set.json (section 7.1). */
public record GoldItem(String id, String question, List<String> expectedSources,
                       String referenceAnswer, boolean answerable) {}
