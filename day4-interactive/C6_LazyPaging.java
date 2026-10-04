import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Checkpoint 6: lazy streams and paging (session 0:15-0:45, after the CRM adapter).
 *
 * The fake CRM has 3 pages of 3 contacts. read() works the same way as CrmCustomerSource.read().
 *
 * PREDICT  Before running, for each of A, B, C and D write down:
 *            - which pages get fetched ("HTTP GET page n")
 *            - whether "connection closed" is printed
 * Run:     java C6_LazyPaging.java
 * ASK      1. Why does A fetch page 0 even though findFirst() needs only one contact?
 *          2. Which of A-D would leak a connection if this were a real HTTP or JDBC stream?
 *          3. Find the try-with-resources in CustomerIngestionService.run(). What would break without it?
 */
public class C6_LazyPaging {

    record Page(int number, List<String> contacts, boolean last) {
    }

    static Page fetch(int page) {
        System.out.println("    HTTP GET page " + page);
        List<String> contacts = List.of("c" + (page * 3 + 1), "c" + (page * 3 + 2), "c" + (page * 3 + 3));
        return new Page(page, contacts, page == 2);
    }

    static Stream<String> read() {
        return Stream.iterate(fetch(0), Objects::nonNull, page -> page.last() ? null : fetch(page.number() + 1))
                .flatMap(page -> page.contacts().stream())
                .onClose(() -> System.out.println("    connection closed"));
    }

    public static void main(String[] args) {
        System.out.println("A: read().findFirst()");
        System.out.println("    -> " + read().findFirst().orElseThrow());

        System.out.println("B: read().limit(4).toList()");
        System.out.println("    -> " + read().limit(4).toList());

        System.out.println("C: read().count()");
        System.out.println("    -> " + read().count());

        System.out.println("D: try (var s = read()) { s.limit(1).toList() }");
        try (Stream<String> contacts = read()) {
            System.out.println("    -> " + contacts.limit(1).toList());
        }
    }
}
