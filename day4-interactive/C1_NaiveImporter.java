import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Checkpoint 1: the naive importer (session 0:00-0:15, the hook).
 *
 * Run:  java C1_NaiveImporter.java
 *
 * READ   It works. Read importCustomers() before running it.
 * ASK    1. A fourth source (XML) arrives. How many places in this file change?
 *        2. Point at every line that knows about dry run.
 *        3. Lower-casing emails: how many copies of that rule are there? Are they all the same?
 *        4. How would you unit-test the "api" branch without a real API?
 *        5. What happens if this runs twice?
 * RUN    Look at the final table. Something is wrong. Which question above predicted it?
 */
public class C1_NaiveImporter {

    // email -> name, standing in for the customers table
    static final Map<String, String> db = new LinkedHashMap<>();

    static void importCustomers(String type, boolean dryRun) {
        if (type.equals("csv")) {
            for (String line : List.of("Asha Rao,ASHA@EXAMPLE.COM", "  Eve Stone ,eve@example.com")) {
                String[] f = line.split(",");
                String email = f[1].trim().toLowerCase();
                if (!dryRun) {
                    db.put(email, f[0].trim());
                }
                System.out.println((dryRun ? "[dry] " : "") + "csv    -> " + email);
            }
        } else if (type.equals("api")) {
            for (String contact : List.of("Carter, Ben|BEN@example.com", "Lee, Ana|ana@example.com")) {
                String[] f = contact.split("\\|");
                String[] n = f[0].split(",");
                String email = f[1].toLowerCase();
                if (!dryRun) {
                    db.put(email, n[1].trim() + " " + n[0].trim());
                }
                System.out.println((dryRun ? "[dry] " : "") + "api    -> " + email);
            }
        } else if (type.equals("db")) {
            for (String[] row : List.of(new String[] {"EVE STONE", "EVE@EXAMPLE.COM"},
                                        new String[] {"JON PARK", "jon@example.com"})) {
                String name = row[0].charAt(0) + row[0].substring(1).toLowerCase();
                String email = row[1].trim();
                if (!dryRun) {
                    db.put(email, name);
                }
                System.out.println((dryRun ? "[dry] " : "") + "legacy -> " + email);
            }
        } else {
            throw new IllegalArgumentException("unknown type " + type);
        }
    }

    public static void main(String[] args) {
        importCustomers("csv", false);
        importCustomers("api", false);
        importCustomers("db", false);
        System.out.println();
        db.forEach((email, name) -> System.out.printf("%-20s %s%n", email, name));
    }
}
