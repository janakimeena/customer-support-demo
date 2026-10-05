package edu.rag.eval;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.evaluation.Evaluator;

import java.util.Map;

/** Section 7.4: graded 1-5 LLM-as-judge against the reference answer, via structured output. */
public class CorrectnessEvaluator implements Evaluator {

    record Verdict(int score, String reasoning) {}

    private static final String PROMPT = """
            You are grading a student-facing answer.
            Question: {question}
            Reference answer: {reference}
            Candidate answer: {candidate}
            Score 1-5: 5 = fully correct and complete, 3 = partially correct,
            1 = wrong or contradicts the reference. Ignore style; judge facts only.
            """;

    private final ChatClient judge;
    private final int passMark;

    public CorrectnessEvaluator(ChatClient.Builder builder, int passMark) {
        this.judge = builder.build();
        this.passMark = passMark;
    }

    // userText = question, responseContent = candidate; reference passed via the data list
    @Override
    public EvaluationResponse evaluate(EvaluationRequest req) {
        String reference = req.getDataList().isEmpty() ? ""
                : req.getDataList().get(0).getText();

        Verdict v = judge.prompt()
                .user(u -> u.text(PROMPT)
                        .param("question", req.getUserText())
                        .param("reference", reference)
                        .param("candidate", req.getResponseContent()))
                .call()
                .entity(Verdict.class);

        return new EvaluationResponse(v.score() >= passMark, v.score() / 5f,
                v.reasoning(), Map.of());
    }
}
