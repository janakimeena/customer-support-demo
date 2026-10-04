import java.lang.reflect.Proxy;
import java.util.List;

/**
 * Checkpoint 8: why each chunk gets its own transaction (session 0:45-1:05).
 *
 * Spring's @Transactional works through a proxy object wrapped around your bean. This file builds a tiny
 * proxy by hand (java.lang.reflect.Proxy) that prints BEGIN/COMMIT around every call that goes THROUGH it.
 *
 * PREDICT  Before running: how many BEGIN lines does A print? And B?
 * Run:     java C8_SelfInvocation.java
 * ASK      1. In B, writeAll() calls writeChunk() itself. Why is there no BEGIN around those calls?
 *          2. Which one matches CustomerIngestionService calling BatchUpsertWriter.write(chunk)?
 *          3. In B, chunk 3 fails. What happens to chunks 1 and 2? Why is A better for an idempotent import?
 *          4. Why is CustomerIngestionService.run() deliberately NOT @Transactional?
 */
public class C8_SelfInvocation {

    interface Writer {
        void writeAll(List<List<String>> chunks);

        void writeChunk(List<String> chunk);
    }

    static final class RealWriter implements Writer {
        @Override
        public void writeAll(List<List<String>> chunks) {
            for (List<String> chunk : chunks) {
                writeChunk(chunk); // self-invocation: "this.writeChunk", not the proxy
            }
        }

        @Override
        public void writeChunk(List<String> chunk) {
            System.out.println("      MERGE " + chunk);
        }
    }

    /** What Spring does for @Transactional, minus everything except the printing. */
    static Writer transactional(Writer target) {
        return (Writer) Proxy.newProxyInstance(Writer.class.getClassLoader(), new Class<?>[] {Writer.class},
                (proxy, method, args) -> {
                    System.out.println("    BEGIN  (" + method.getName() + ")");
                    Object result = method.invoke(target, args);
                    System.out.println("    COMMIT (" + method.getName() + ")");
                    return result;
                });
    }

    public static void main(String[] args) {
        Writer writer = transactional(new RealWriter());
        List<List<String>> chunks = List.of(List.of("a", "b"), List.of("c", "d"), List.of("e"));

        System.out.println("A: the service calls writer.writeChunk(chunk) once per chunk");
        for (List<String> chunk : chunks) {
            writer.writeChunk(chunk);
        }

        System.out.println("B: the service calls writer.writeAll(chunks)");
        writer.writeAll(chunks);
    }
}
