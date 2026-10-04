import java.util.ArrayList;
import java.util.List;

/**
 * Checkpoint 2: Strategy (session 0:15-0:45).
 *
 * Run:  java C2_StrategyWriter.java
 *
 * PREDICT  Before running: how many lines does each run() print, and what is in DB at the end?
 * TRY      Add an "audited" writer that prints "AUDIT <n> customers" and then delegates to upsert.
 *          Rule: you may not change run(). (See the TODO in main.)
 * ASK      1. run() knows nothing about dry runs. Where was the dry-run decision made?
 *          2. Suppose run() took (boolean dryRun, boolean audit) instead of a writer.
 *             How many combinations must run() handle? What about a third option?
 *          3. Find the matching line in CustomerIngestionService.run().
 */
public class C2_StrategyWriter {

    /** The Strategy: how one chunk gets loaded. */
    interface CustomerWriter {
        String write(List<String> chunk);
    }

    static final List<String> DB = new ArrayList<>();

    /** The pipeline: written once, and it never changes. */
    static void run(List<String> emails, CustomerWriter writer, int chunkSize) {
        List<String> chunk = new ArrayList<>();
        for (String email : emails) {
            chunk.add(email);
            if (chunk.size() == chunkSize) {
                System.out.println("  " + writer.write(List.copyOf(chunk)));
                chunk.clear();
            }
        }
        if (!chunk.isEmpty()) {
            System.out.println("  " + writer.write(List.copyOf(chunk)));
        }
    }

    public static void main(String[] args) {
        List<String> emails = List.of("a@x.com", "b@x.com", "c@x.com", "d@x.com", "e@x.com");

        CustomerWriter upsert = chunk -> {
            DB.addAll(chunk);
            return "wrote " + chunk;
        };
        CustomerWriter dryRun = chunk -> "would write " + chunk;

        System.out.println("Dry run:");
        run(emails, dryRun, 2);
        System.out.println("Real run:");
        run(emails, upsert, 2);

        // TODO: CustomerWriter audited = ...   (print "AUDIT <n> customers", then call upsert)
        //       System.out.println("Audited run:");
        //       run(List.of("f@x.com"), audited, 2);

        System.out.println("DB = " + DB);
    }
}
