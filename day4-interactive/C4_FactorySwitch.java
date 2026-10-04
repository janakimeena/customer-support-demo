import java.util.Map;

/**
 * Checkpoint 4: Factory (session 0:15-0:45).
 *
 * Run:  java C4_FactorySwitch.java              (builds every configured source)
 *       java C4_FactorySwitch.java partner-crm  (builds one by name)
 *
 * PREDICT  Uncomment SFTP in the enum (and its config line). Before running:
 *          - Which method stops compiling, create() or kind()?
 *          - Which one compiles but quietly gives a wrong answer?
 * ASK      1. Callers pass a source NAME, never a type or location. Imagine instead:
 *               POST /api/ingestion/runs?type=CSV&location=file:/etc/passwd
 *               POST /api/ingestion/runs?type=CRM_API&location=http://169.254.169.254/latest/meta-data
 *             What could an attacker do with each one?
 *          2. Where do the RestClient, ResourceLoader and JdbcTemplate come from in the real
 *             CustomerSourceFactory, and why doesn't CustomerIngestionService need them?
 */
public class C4_FactorySwitch {

    enum SourceType { CSV, CRM_API, LEGACY_DB /* , SFTP */ }

    record SourceDefinition(SourceType type, String location) {
    }

    interface CustomerSource {
        String read();
    }

    /** The factory: an exhaustive switch expression with no default. */
    static CustomerSource create(SourceDefinition def) {
        return switch (def.type()) {
            case CSV -> () -> "reading file " + def.location();
            case CRM_API -> () -> "GET " + def.location() + "/contacts?page=0";
            case LEGACY_DB -> () -> "SELECT ... FROM legacy_accounts WHERE status = 'A'";
        };
    }

    /** Old-style switch statement with a default branch. */
    static String kind(SourceType type) {
        switch (type) {
            case CSV:
                return "file";
            case CRM_API:
                return "http";
            default:
                return "database";
        }
    }

    // Stands in for support.ingest.sources in application.yml
    static final Map<String, SourceDefinition> CONFIG = Map.of(
            "east-csv", new SourceDefinition(SourceType.CSV, "classpath:sample-data/customers-east.csv"),
            "partner-crm", new SourceDefinition(SourceType.CRM_API, "http://localhost:8080/demo/crm"),
            "legacy-db", new SourceDefinition(SourceType.LEGACY_DB, null)
            // , "nightly-sftp", new SourceDefinition(SourceType.SFTP, "sftp://files.partner.com/customers.csv")
    );

    public static void main(String[] args) {
        Iterable<String> names = args.length > 0 ? java.util.List.of(args) : new java.util.TreeSet<>(CONFIG.keySet());
        for (String name : names) {
            SourceDefinition def = CONFIG.get(name);
            if (def == null) {
                System.out.println(name + ": unknown source (the API answers 404)");
                continue;
            }
            System.out.printf("%-13s %-9s (%s)  %s%n", name, def.type(), kind(def.type()), create(def).read());
        }
    }
}
