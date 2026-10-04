import java.util.Locale;
import java.util.Objects;

/**
 * Checkpoint 3: Adapter (session 0:15-0:45).
 *
 * A new partner, "HelpDeskPro", sends users like this:
 *     display = "DOE, JANE"    (LAST, FIRST in upper case)
 *     level   = 1, 2 or 3      (3 means premium)
 *
 * Run:  java C3_AdapterVendor.java       (prints FAIL until adapt() is finished)
 *
 * TRY   Finish adapt() so all checks PASS (about 5 minutes). titleCase() is already written.
 *       - "DOE, JANE" -> "Jane Doe"; a name without a comma is only title-cased
 *       - level 3 -> PREMIUM, 1 or 2 -> STANDARD, anything else -> "level N" (passed through)
 * ASK   1. The email "JANE@HELPDESK.IO" is NOT lower-cased by the adapter. Why not? Who does it?
 *       2. Why pass an unknown level through as "level 7" instead of guessing STANDARD?
 *       3. Look at the last line of output: "ACME, INC." Is your adapter right? Whose problem is it?
 */
public class C3_AdapterVendor {

    /** What the vendor sends (we don't control this). */
    record HelpDeskUser(long userId, String display, String mail, int level) {
    }

    /** What our pipeline understands (same shape as ingest/RawCustomer). */
    record RawCustomer(String origin, String name, String email, String tier) {
    }

    static RawCustomer adapt(HelpDeskUser user) {
        // TODO: translate the name and the level. Right now everything is passed through untouched.
        return new RawCustomer("helpdesk user " + user.userId(), user.display(), user.mail(),
                String.valueOf(user.level()));
    }

    /** "MARY-JANE O'BRIEN" -> "Mary-Jane O'Brien". */
    static String titleCase(String name) {
        StringBuilder result = new StringBuilder(name.length());
        boolean startOfWord = true;
        for (char c : name.toLowerCase(Locale.ROOT).toCharArray()) {
            result.append(startOfWord ? Character.toUpperCase(c) : c);
            startOfWord = c == ' ' || c == '-' || c == '\'';
        }
        return result.toString();
    }

    public static void main(String[] args) {
        check(new HelpDeskUser(1, "DOE, JANE", "JANE@HELPDESK.IO", 3), "Jane Doe", "JANE@HELPDESK.IO", "PREMIUM");
        check(new HelpDeskUser(2, "MARY-JANE O'BRIEN", "mj@helpdesk.io", 1), "Mary-Jane O'Brien", "mj@helpdesk.io", "STANDARD");
        check(new HelpDeskUser(3, "SMITH,BOB", "bob@helpdesk.io", 2), "Bob Smith", "bob@helpdesk.io", "STANDARD");
        check(new HelpDeskUser(4, "LEE, ANA", "ana@helpdesk.io", 7), "Ana Lee", "ana@helpdesk.io", "level 7");

        RawCustomer acme = adapt(new HelpDeskUser(5, "ACME, INC.", "billing@acme.com", 2));
        System.out.println("\nDiscuss: \"ACME, INC.\" became \"" + acme.name() + "\"");
    }

    static void check(HelpDeskUser input, String name, String email, String tier) {
        RawCustomer expected = new RawCustomer("helpdesk user " + input.userId(), name, email, tier);
        RawCustomer actual = adapt(input);
        boolean ok = Objects.equals(expected, actual);
        System.out.println((ok ? "PASS  " : "FAIL  ") + input.display() + " / level " + input.level()
                + (ok ? "" : "%n      expected %s%n      actual   %s".formatted(expected, actual)));
    }
}
