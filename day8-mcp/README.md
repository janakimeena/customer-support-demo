# Day 8 Demo: Model Context Protocol (MCP)

The BookNest example from the MCP notes, built and tested against **Spring Boot 4.1.1 + Spring AI 2.0.1** (MCP Java SDK 2.0.0, protocol `2025-06-18`). The demo has three parts, one per MCP role:

| Part | MCP role | What it shows | Notes |
| --- | --- | --- | --- |
| [booknest-mcp-server](booknest-mcp-server) | **Server** | 3 tools, 1 resource, 1 resource template, 1 prompt over stdio | §4, §6, §7 |
| [mcp-wire-explorer](mcp-wire-explorer) | **Client** (by hand) | Every raw JSON-RPC message, with no SDK in the way | §5 |
| [booknest-chat-host](booknest-chat-host) | **Host** + client | Gemini using the server's tools, the host attaching a resource, the host using a prompt | §3, §8.2 |

```
You ─► Host (booknest-chat-host + Gemini) ─► MCP client ⇄ JSON-RPC over stdio ⇄ booknest-mcp.jar
```

## Build

```sh
cd day8-mcp
./mvnw package          # builds both modules and runs the tests (9, no API key needed)
```

This produces `booknest-mcp-server/target/booknest-mcp.jar`.

## 1. See the protocol: the wire explorer

```sh
cd mcp-wire-explorer
java WireExplorer.java ../booknest-mcp-server/target/booknest-mcp.jar --step   # Enter between steps
```

The explorer is a single Java file with no libraries. It launches the server, writes JSON-RPC to the server's stdin and prints what comes back. It walks through 12 steps:

1. `initialize`: version and capability negotiation
2. `notifications/initialized`: a notification has no `id` and gets no reply
3. `tools/list`: names, descriptions and the generated `inputSchema`
4. to 6. `tools/call`: a search, a price lookup, and an unknown ID (a normal result, `isError: false`)
7. A negative price, which becomes a **tool error** (`result.isError: true`). The schema allowed it, and the server's validation rejected it.
8. An unknown method, which becomes a **protocol error** (`error.code: -32601`). Compare it with step 7.
9. and 10. `resources/list`, `resources/read`, and the `booknest://books/{id}` template
11. `prompts/list`, `prompts/get`
12. Three requests sent at once. The server may answer out of order, and that is why every request has an `id`.

Server logs go to `server-stderr.log` (stderr), never to stdout.

## 2. Inspect it with MCP Inspector

```sh
npx @modelcontextprotocol/inspector java -jar "$PWD/booknest-mcp-server/target/booknest-mcp.jar"
```

Then follow the checklist in notes §9. Expected results:

| Action | Result |
| --- | --- |
| `search_books` with `java` | B2 Head First Java, B3 Effective Java, B4 Java Concurrency in Practice |
| `get_book_price` with `B2` | `Head First Java costs Rs. 599.00` |
| `get_book_price` with `B9` | `No book with id B9. Use search_books to find a valid ID.` (not an error) |
| `books_by_max_price` with `-5` | Tool error: `maxPrice must be zero or more` |
| Read `booknest://policies/returns` | The demo return policy |
| Read template `booknest://books/{id}` with `B3` | Effective Java's details |
| Get `recommend-books` with `topic=concurrency` | One filled-in user message |

## 3. A real host: Spring AI + Gemini

```sh
export GEMINI_API_KEY=...            # from https://aistudio.google.com/apikey
export GEMINI_CHAT_MODEL=gemini-3.5-flash   # optional; this is the default
cd booknest-chat-host
../mvnw spring-boot:run
```

Open http://localhost:8080. The host launches the server jar itself (see `spring.ai.mcp.client.stdio.connections` in [application.yml](booknest-chat-host/src/main/resources/application.yml)). The page shows:

- **What the server advertised.** This uses only MCP, no model call, so it works without an API key.
- **Ask the model.** Gemini gets the three tool definitions. The trace shows each tool the model chose, the arguments it picked, and what the server returned. Tick the box to have the host read the return-policy **resource** and attach it as context. The model never calls a resource itself.
- **Use the server's prompt.** The host fetches `recommend-books` with `prompts/get` and sends the filled-in text as the user message.

The same as REST:

| Endpoint | MCP request behind it |
| --- | --- |
| `GET /api/mcp/servers` | `initialize` result: serverInfo, capabilities, instructions |
| `GET /api/mcp/tools` | `tools/list` |
| `GET /api/mcp/resources` | `resources/list` + `resources/templates/list` |
| `GET /api/mcp/resources/read?uri=booknest://books/B1` | `resources/read` |
| `GET /api/mcp/prompts`, `GET /api/mcp/prompts/recommend-books?topic=java` | `prompts/list`, `prompts/get` |
| `POST /api/chat` `{"message": "...", "attachReturnPolicy": true}` | Model + any number of `tools/call` |
| `POST /api/chat/recommend` `{"topic": "concurrency", "level": "beginner"}` | `prompts/get`, then model + `tools/call` |

To see every JSON-RPC message the host sends, set `MCP_LOG_LEVEL=DEBUG`. Without an API key, the chat endpoints return 503 and everything else still works.

## 4. Claude Desktop

Add this to the Claude Desktop config (notes §8.1), using the absolute path to the jar, then restart Claude Desktop:

```json
{
  "mcpServers": {
    "booknest": {
      "command": "java",
      "args": ["-jar", "/absolute/path/to/day8-mcp/booknest-mcp-server/target/booknest-mcp.jar"]
    }
  }
}
```

## Run it with Docker

You only need Docker. No JDK, Maven or Node is needed. The build stage compiles both modules and runs the tests.

The server uses stdio, so whoever talks to it launches it as a child process. For that reason the server jar is packaged **inside** each image that uses it, and there is no separate "server container". (To run the server as its own container, it would need the Streamable HTTP transport; see the API notes below.)

```sh
cd day8-mcp
docker compose build                                  # once: builds booknest-mcp-demo

docker compose run --rm explorer                      # 1. wire explorer, Enter between steps
docker compose --profile inspector up inspector       # 2. MCP Inspector, already connected to booknest
GEMINI_API_KEY=... docker compose up host             # 3. host at http://localhost:8080
```

- **Inspector.** Open the `http://localhost:6274?MCP_INSPECTOR_API_TOKEN=...` link that it prints. Its ports are published on 127.0.0.1 only.
- **Host.** Without `GEMINI_API_KEY`, the "advertised" panel and `/api/mcp/*` still work, and chat returns 503. Add `MCP_LOG_LEVEL=DEBUG` to see the JSON-RPC traffic with `docker compose logs -f host`.
- **Exercises.** After you edit the server, run `docker compose build` again (and `docker compose --profile inspector build`).

## Where each idea from the notes lives

| Concept | File |
| --- | --- |
| Tools, `@McpTool` / `@McpToolParam`, descriptions written for the model | [BookTools.java](booknest-mcp-server/src/main/java/com/example/booknest/BookTools.java) |
| Controlled "not found" vs a thrown exception (`isError`) | `getBookPrice` vs `booksByMaxPrice` in the same file |
| Resources and a URI template, `@McpResource` | [BookResources.java](booknest-mcp-server/src/main/java/com/example/booknest/BookResources.java) |
| Prompts, `@McpPrompt` / `@McpArg` returning `GetPromptResult` | [BookPrompts.java](booknest-mcp-server/src/main/java/com/example/booknest/BookPrompts.java) |
| Business logic kept free of MCP; `BigDecimal` for money | [BookCatalog.java](booknest-mcp-server/src/main/java/com/example/booknest/BookCatalog.java) |
| The stdout rule: no banner, logs to stderr | [application.properties](booknest-mcp-server/src/main/resources/application.properties), [logback-spring.xml](booknest-mcp-server/src/main/resources/logback-spring.xml) |
| One host, one client per server | [McpConnectionController.java](booknest-chat-host/src/main/java/com/example/booknest/host/McpConnectionController.java) |
| The model asks; the host runs the call | [TracingToolCallback.java](booknest-chat-host/src/main/java/com/example/booknest/host/TracingToolCallback.java), [ChatController.java](booknest-chat-host/src/main/java/com/example/booknest/host/ChatController.java) |

### API notes for Spring AI 2.0.1

The notes leave these as "verify for your version". This is what they are:

- Annotations: `org.springframework.ai.mcp.annotation.{McpTool, McpToolParam, McpResource, McpPrompt, McpArg}`.
- Server starter: `spring-ai-starter-mcp-server` (stdio). Use `-webmvc` or `-webflux` for Streamable HTTP.
- `spring.ai.mcp.server.stdio=true` is required. It defaults to `false`, and without it the server does not read stdin.
- A prompt method returns `io.modelcontextprotocol.spec.McpSchema.GetPromptResult`.
- A thrown exception in a tool becomes `isError: true` in the result, not a JSON-RPC error.
- In the host, the client starter registers a `ToolCallbackProvider` with the server's tools under their plain names.

## Exercises (notes §11)

Exercise 4 (`books_by_max_price`) is already done as a worked example. Try these next:

- **Exercise 5.** Add `System.out.println("Searching...")` to `searchBooks`, rebuild and run the explorer. The `!!` line it prints is the corrupted stream. Replace it with `log.info`.
- **Exercise 6.** Add a `booknest://policies/shipping` resource and find it in the explorer's step 9.
- **Exercise 7.** Make `get_book_price`'s description vaguer ("Get book information"), then ask the host *"How much is Effective Java?"*. Watch whether the model now passes a title instead of an ID.
- **Exercise 9.** Sketch a `cancel_order` tool. What would the host need before running it?
