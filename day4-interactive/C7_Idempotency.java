import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Checkpoint 7: idempotent upsert (session 0:45-1:05, before running the real import twice).
 *
 * Run:  java C7_Idempotency.java
 *
 * PREDICT  The table already holds Ben (STANDARD, version 0). The import file has Asha, Ben (PREMIUM) and
 *          Hiro, and it is imported TWICE. Before running, write down:
 *            - the counts for run 1 and run 2
 *            - every customer's version at the end
 * TRY      Fix upsert() so the second run reports "unchanged" and no version moves.
 *          Then compare your fix with the WHEN MATCHED AND (...) clause in BatchUpsertWriter.UPSERT.
 * ASK      1. Why does a version bump with no real change hurt API users? (Think: optimistic locking, 409.)
 *          2. The import crashes after chunk 2 of 5. What is the recovery procedure?
 *          3. Tomorrow's file no longer contains Hiro. What does our import do about it? Is that right?
 */
public class C7_Idempotency {

    static final class Row {
        String name;
        String tier;
        int version;

        Row(String name, String tier) {
            this.name = name;
            this.tier = tier;
        }

        @Override
        public String toString() {
            return "%-11s %-8s v%d".formatted(name, tier, version);
        }
    }

    record Incoming(String email, String name, String tier) {
    }

    static final Map<String, Row> table = new TreeMap<>();

    /** Returns "inserted", "updated" or "unchanged". */
    static String upsert(Incoming in) {
        Row row = table.get(in.email());
        if (row == null) {
            table.put(in.email(), new Row(in.name(), in.tier()));
            return "inserted";
        }
        // TODO: make this idempotent
        row.name = in.name();
        row.tier = in.tier();
        row.version++;
        return "updated";
    }

    static void runImport(int run, List<Incoming> file) {
        Map<String, Integer> counts = new TreeMap<>(Map.of("inserted", 0, "updated", 0, "unchanged", 0));
        file.forEach(in -> counts.merge(upsert(in), 1, Integer::sum));
        System.out.println("run " + run + ": " + counts);
    }

    public static void main(String[] args) {
        table.put("ben@x.com", new Row("Ben Carter", "STANDARD"));

        List<Incoming> file = List.of(
                new Incoming("asha@x.com", "Asha Rao", "PREMIUM"),
                new Incoming("ben@x.com", "Ben Carter", "PREMIUM"),
                new Incoming("hiro@x.com", "Hiro Tanaka", "STANDARD"));

        runImport(1, file);
        runImport(2, file);

        System.out.println();
        table.forEach((email, row) -> System.out.printf("%-11s %s%n", email, row));
    }
}
