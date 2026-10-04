import java.util.ArrayList;
import java.util.List;

/**
 * Checkpoint 5: Builder (session 0:15-0:45).
 *
 * Run:  java C5_BuilderBug.java
 *
 * PART A  Spot the bug in reportByConstructor(). Does it compile? Is the printed report right?
 *         How long would this bug survive code review? In production?
 * PART B  Finish Builder.add() and Builder.reject() so the builder report matches the expected one.
 *         Rule: keep at most MAX_LISTED rejections in the list, but count all of them.
 * ASK     1. The builder takes events (recordRead, reject, add) rather than setters (setInserted...).
 *            What does that buy you?
 *         2. Why does the cap on the rejection list belong in the builder and not in the service loop?
 *         3. Which builders have you already used this week? (Hint: RestClient, Health, UriComponents)
 */
public class C5_BuilderBug {

    record Report(String source, int read, int inserted, int updated, int unchanged, int rejected,
                  List<String> rejections) {
    }

    // ---- Part A ------------------------------------------------------------------------------------

    static Report reportByConstructor() {
        int read = 8, inserted = 2, updated = 1, unchanged = 3, rejected = 2;
        return new Report("east-csv", read, inserted, unchanged, updated, rejected, List.of("line 5", "line 6"));
    }

    // ---- Part B ------------------------------------------------------------------------------------

    static final int MAX_LISTED = 1;

    static final class Builder {
        private final String source;
        private int read, inserted, updated, unchanged, rejected;
        private final List<String> rejections = new ArrayList<>();

        Builder(String source) {
            this.source = source;
        }

        Builder recordRead() {
            read++;
            return this;
        }

        Builder reject(String origin) {
            // TODO: count every rejection, but list at most MAX_LISTED
            return this;
        }

        /** One chunk's outcome from the writer. */
        Builder add(int chunkInserted, int chunkUpdated, int chunkUnchanged) {
            // TODO
            return this;
        }

        Report build() {
            return new Report(source, read, inserted, updated, unchanged, rejected, List.copyOf(rejections));
        }
    }

    static Report reportByBuilder() {
        Builder report = new Builder("east-csv");
        for (int i = 0; i < 8; i++) {
            report.recordRead();
        }
        report.reject("line 5").reject("line 6");
        report.add(1, 1, 1).add(1, 0, 2);
        return report.build();
    }

    public static void main(String[] args) {
        Report expected = new Report("east-csv", 8, 2, 1, 3, 2, List.of("line 5"));

        System.out.println("A  constructor: " + reportByConstructor());
        Report built = reportByBuilder();
        System.out.println("B  builder:     " + built);
        System.out.println("   expected:    " + expected);
        System.out.println(built.equals(expected) ? "PASS" : "FAIL");
    }
}
