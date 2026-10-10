import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MCP wire explorer: a hand-written MCP client with no SDK and no libraries.
 *
 * <p>It does the host/client's job by hand (notes §3, §5): it launches the server as a child
 * process, writes JSON-RPC requests to the server's stdin, one per line, and reads responses
 * from its stdout. It prints every message so you can see what an SDK normally hides.
 *
 * <pre>
 *   java WireExplorer.java ../booknest-mcp-server/target/booknest-mcp.jar          # run all steps
 *   java WireExplorer.java ../booknest-mcp-server/target/booknest-mcp.jar --step   # pause after each step
 * </pre>
 *
 * Server logs (its stderr) go to server-stderr.log, not to the protocol channel.
 */
public class WireExplorer {

    private static final String RESET = "\u001B[0m", BOLD = "\u001B[1m", DIM = "\u001B[2m",
            CYAN = "\u001B[36m", GREEN = "\u001B[32m", YELLOW = "\u001B[33m", MAGENTA = "\u001B[35m";

    private static final Pattern RESPONSE_ID = Pattern.compile("^\\{\"jsonrpc\":\"2\\.0\",\"id\":(\\d+)");

    private final BufferedWriter toServer;
    private final Map<Integer, BlockingQueue<String>> pending = new ConcurrentHashMap<>();
    private final boolean stepMode;
    private final BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in));
    private int nextId = 1;

    WireExplorer(Process server, boolean stepMode) {
        this.toServer = new BufferedWriter(new OutputStreamWriter(server.getOutputStream(), StandardCharsets.UTF_8));
        this.stepMode = stepMode;
        Thread reader = new Thread(() -> readServerStdout(server), "stdout-reader");
        reader.setDaemon(true);
        reader.start();
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("usage: java WireExplorer.java <path/to/booknest-mcp.jar> [--step]");
            System.exit(2);
        }
        Path jar = Path.of(args[0]).toAbsolutePath();
        if (!Files.isRegularFile(jar)) {
            System.err.println("No jar at " + jar + ". Build it first: ./mvnw -pl booknest-mcp-server package");
            System.exit(2);
        }
        boolean step = List.of(args).contains("--step");

        // The host launches the server process. This is the stdio transport (notes §6).
        Path stderrLog = Path.of("server-stderr.log").toAbsolutePath();
        Process server = new ProcessBuilder("java", "-jar", jar.toString())
                .redirectError(stderrLog.toFile())
                .start();
        System.out.println(DIM + "Launched: java -jar " + jar + RESET);
        System.out.println(DIM + "Server diagnostics (stderr) -> " + stderrLog + RESET);

        try {
            new WireExplorer(server, step).tour();
        } finally {
            server.destroy();
            server.waitFor(5, TimeUnit.SECONDS);
        }
    }

    private void tour() throws Exception {
        section("1. Handshake: initialize",
                "The client says which protocol version it speaks and what it can do.",
                "The server replies with the agreed version, its capabilities and serverInfo.",
                "Look at result.capabilities: tools, resources and prompts. That is the server",
                "telling us which primitives it supports.");
        request("initialize", """
                {"protocolVersion":"2025-06-18","capabilities":{},\
                "clientInfo":{"name":"wire-explorer","version":"1.0.0"}}""");

        section("2. notifications/initialized",
                "A notification has no id, so the server sends nothing back.",
                "It tells the server the handshake is done and normal requests can start.");
        notify("notifications/initialized");

        section("3. Discovery: tools/list",
                "The client asks which tools exist. Each one has a name, a description and an",
                "inputSchema built from the Java method by Spring AI. A host passes these to the",
                "model, and the model picks a tool using only that text.");
        request("tools/list", null);

        section("4. tools/call: search_books",
                "This is the request from notes §5: method tools/call, params.name, params.arguments.",
                "The result is a list of content blocks. A Java List<Book> comes back as JSON text.");
        request("tools/call", """
                {"name":"search_books","arguments":{"query":"java"}}""");

        section("5. tools/call: get_book_price with a valid ID",
                "Spring AI routes the call to BookTools.getBookPrice(\"B2\").");
        request("tools/call", """
                {"name":"get_book_price","arguments":{"bookId":"B2"}}""");

        section("6. tools/call: get_book_price with an unknown ID",
                "An expected miss is a normal result (isError=false) with a helpful message,",
                "so the model can recover, for example by calling search_books.");
        request("tools/call", """
                {"name":"get_book_price","arguments":{"bookId":"B9"}}""");

        section("7. tools/call: invalid input becomes a tool error",
                "books_by_max_price throws on a negative price. The server catches the exception",
                "and returns isError=true. The schema said \"number\", and -5 is a number:",
                "a schema does not replace validation on the server (notes §10).");
        request("tools/call", """
                {"name":"books_by_max_price","arguments":{"maxPrice":-5}}""");

        section("8. Unknown method: a protocol-level error",
                "Compare with step 7. This is a JSON-RPC 'error' object, not a 'result'. The request",
                "itself was invalid, so no tool ran.");
        request("tools/frobnicate", null);

        section("9. Resources: resources/list, then resources/read",
                "Resources are read-only content identified by a URI. The host decides when to",
                "attach them as context; the model does not call them like tools.");
        request("resources/list", null);
        request("resources/read", """
                {"uri":"booknest://policies/returns"}""");

        section("10. Resource templates",
                "booknest://books/{id} is one pattern that covers every book. The client fills in",
                "the {id} and reads the result like any other resource.");
        request("resources/templates/list", null);
        request("resources/read", """
                {"uri":"booknest://books/B3"}""");

        section("11. Prompts: prompts/list, then prompts/get",
                "A prompt is a template a user picks in the host. The server fills in the arguments",
                "and returns ready-made messages. It does not run anything.");
        request("prompts/list", null);
        request("prompts/get", """
                {"name":"recommend-books","arguments":{"topic":"concurrency","level":"intermediate"}}""");

        section("12. Why every request has an id",
                "Three requests go out at once. The server can answer in any order, and the",
                "client matches each response to its request by id.");
        int a = send("tools/call", """
                {"name":"search_books","arguments":{"query":"martin"}}""");
        int b = send("resources/read", """
                {"uri":"booknest://books/B1"}""");
        int c = send("tools/call", """
                {"name":"get_book_price","arguments":{"bookId":"B5"}}""");
        for (int id : new int[]{a, b, c}) {
            printResponse(id, await(id));
        }

        System.out.println();
        System.out.println(BOLD + GREEN + "Done. Every message above is what an MCP SDK sends for you." + RESET);
    }

    // ---- JSON-RPC plumbing --------------------------------------------------------------

    /** Sends a request and prints it with the matching response. */
    private void request(String method, String params) throws Exception {
        int id = send(method, params);
        printResponse(id, await(id));
    }

    private int send(String method, String params) throws IOException {
        int id = nextId++;
        pending.put(id, new LinkedBlockingQueue<>(1));
        String json = "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"" + method + "\""
                + (params == null ? "" : ",\"params\":" + params) + "}";
        System.out.println(CYAN + BOLD + "→ request id=" + id + RESET);
        System.out.println(CYAN + pretty(json) + RESET);
        writeLine(json);
        return id;
    }

    private void notify(String method) throws IOException {
        String json = "{\"jsonrpc\":\"2.0\",\"method\":\"" + method + "\"}";
        System.out.println(CYAN + BOLD + "→ notification (no id, no reply expected)" + RESET);
        System.out.println(CYAN + pretty(json) + RESET);
        writeLine(json);
    }

    private void writeLine(String json) throws IOException {
        // stdio framing: one JSON message per line.
        toServer.write(json);
        toServer.newLine();
        toServer.flush();
    }

    private String await(int id) throws InterruptedException {
        String response = pending.get(id).poll(60, TimeUnit.SECONDS);
        pending.remove(id);
        if (response == null) {
            throw new IllegalStateException("No response for id " + id + " within 60s. Check server-stderr.log");
        }
        return response;
    }

    private void printResponse(int id, String json) {
        String colour = json.contains("\"error\":") || json.contains("\"isError\":true") ? YELLOW : GREEN;
        System.out.println(colour + BOLD + "← response id=" + id + RESET);
        System.out.println(colour + pretty(json) + RESET);
    }

    /** Reads every line the server writes to stdout and routes responses by id. */
    private void readServerStdout(Process server) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(server.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                Matcher m = RESPONSE_ID.matcher(line);
                if (m.find() && pending.containsKey(Integer.parseInt(m.group(1)))) {
                    pending.get(Integer.parseInt(m.group(1))).offer(line);
                } else if (line.startsWith("{")) {
                    // A server-initiated notification or request, e.g. notifications/message.
                    System.out.println(MAGENTA + "← server-initiated message" + RESET);
                    System.out.println(MAGENTA + pretty(line) + RESET);
                } else {
                    // This is the bug from notes §6: non-JSON text on the protocol channel.
                    System.out.println(YELLOW + BOLD + "!! non-JSON on stdout (would break a real client): " + line + RESET);
                }
            }
        } catch (IOException ignored) {
            // Server exited.
        }
    }

    // ---- presentation -------------------------------------------------------------------

    private void section(String title, String... explanation) throws IOException {
        if (stepMode && nextId > 1) {
            System.out.print(DIM + "\n[Enter] for the next step " + RESET);
            keyboard.readLine();
        }
        System.out.println();
        System.out.println(BOLD + "━━ " + title + " " + "━".repeat(Math.max(3, 70 - title.length())) + RESET);
        for (String line : explanation) {
            System.out.println(DIM + "   " + line + RESET);
        }
    }

    /** A minimal JSON indenter: enough for display, aware of strings and escapes. */
    static String pretty(String json) {
        StringBuilder out = new StringBuilder("  ");
        int indent = 1;
        boolean inString = false;
        for (int i = 0; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (inString) {
                out.append(ch);
                if (ch == '\\' && i + 1 < json.length()) {
                    out.append(json.charAt(++i));
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            switch (ch) {
                case '"' -> { inString = true; out.append(ch); }
                case '{', '[' -> {
                    char close = ch == '{' ? '}' : ']';
                    if (i + 1 < json.length() && json.charAt(i + 1) == close) {
                        out.append(ch).append(close);
                        i++;
                    } else {
                        out.append(ch).append('\n').append("  ".repeat(++indent));
                    }
                }
                case '}', ']' -> out.append('\n').append("  ".repeat(--indent)).append(ch);
                case ',' -> out.append(ch).append('\n').append("  ".repeat(indent));
                case ':' -> out.append(": ");
                default -> out.append(ch);
            }
        }
        return out.toString();
    }
}
