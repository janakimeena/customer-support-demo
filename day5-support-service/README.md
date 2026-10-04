# Day 5 Demo — GenAI Fundamentals (First Support-Assistant Endpoints)

This is the Day 4 support service plus a **support assistant** built with **Spring AI**. It adds five endpoints, one for each GenAI fundamental of the day:

| Endpoint | Concept | What it shows |
| --- | --- | --- |
| `POST /api/assistant/chat` | Prompting, tokens | A system prompt plus the agent's question. The answer comes back with the **token usage** the provider bills. |
| `POST /api/assistant/chat/stream` | Streaming | The same answer, sent as Server-Sent Events as the model writes it. |
| `POST /api/assistant/triage` | Structured output | A raw customer email becomes a typed `TicketTriage` (category, priority, sentiment, summary, suggested reply). |
| `POST /api/assistant/ask` | Tool calling | "What's the status of Asha's refund ticket?" The model calls our read-only tools over the Day 3 data. |
| `POST /api/assistant/similarity` | Embeddings | Texts are ranked by meaning (cosine similarity of embedding vectors). This is the groundwork for Day 6 RAG. |

Everything from Days 3 and 4 is unchanged. Day 4 itself stays untouched.

## Models

| Role | Provider | Model | Profile |
| --- | --- | --- | --- |
| Chat | Google Gemini (Gemini Developer API), via Spring AI's Google GenAI module | `gemini-3.5-flash` (override with `GEMINI_CHAT_MODEL`) | default |
| Embeddings | Open-source Hugging Face model, run **inside the JVM** (Spring AI Transformers + ONNX Runtime) | [`sentence-transformers/all-MiniLM-L6-v2`](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2) (Apache 2.0), 384 dimensions | default |
| Chat | Anthropic Claude | `claude-opus-5-5` | `anthropic` (embeddings stay on the Hugging Face model) |
| Chat + embeddings | Ollama (local, no key) | `llama3.2` + `nomic-embed-text` | `ollama` |

Chat needs `GEMINI_API_KEY`; embeddings need **no key and no extra service**: the model's ONNX file (~90 MB) and tokenizer are downloaded from Hugging Face on the first start into `~/.cache/spring-ai-onnx` and loaded from there afterwards (also offline). It is the same model as `embedding-demo/compare_embeddings.py`, so the Python demo and the Java endpoint give the same picture. `spring.ai.model.chat` and `spring.ai.model.embedding` in `application.yml` choose the provider for each role; the profiles only change those switches.

## Run

Get a Gemini API key from Google AI Studio: https://aistudio.google.com/apikey

```sh
cd day5-support-service
export GEMINI_API_KEY=...                    # Windows PowerShell: $env:GEMINI_API_KEY="..."; never commit it
./mvnw spring-boot:run                       # dev profile: Postgres on localhost:5435, Flyway, sample data
```

Day 5 has its own database container on port **5435**, so it can run next to Day 3 (5433) and Day 4 (5434).

Without a key the app still starts (a placeholder key is used, because the Gemini client refuses to start without one). `/chat`, `/chat/stream`, `/triage` and `/ask` then answer `503 Model not configured`; `/similarity` works, because embeddings run locally.

Other providers:

```sh
# Claude for chat (embeddings stay on the Hugging Face model)
export ANTHROPIC_API_KEY=sk-ant-...
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,anthropic

# Everything local, no key (much weaker; good for comparing)
docker compose --profile ollama up -d ollama
docker compose exec ollama ollama pull nomic-embed-text
docker compose exec ollama ollama pull llama3.2
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,ollama
```

## Try it

Swagger UI: **http://localhost:8080/** → *Assistant*.

```sh
# Prompt + token usage
curl -s localhost:8080/api/assistant/chat -H 'Content-Type: application/json' \
  -d '{"question": "A customer says their package arrived damaged. What should I ask them first?"}' | jq

# Streaming (-N turns off curl's buffering so you see the pieces arrive)
curl -N localhost:8080/api/assistant/chat/stream -H 'Content-Type: application/json' \
  -d '{"question": "Write a short apology for a late delivery."}'

# Structured output
curl -s localhost:8080/api/assistant/triage -H 'Content-Type: application/json' \
  -d '{"message": "You charged me TWICE for order 5521 and nobody answers my emails. Fix this today."}' | jq

# Tool calling: watch toolCalls in the answer
curl -s localhost:8080/api/assistant/ask -H 'Content-Type: application/json' \
  -d '{"question": "Does asha.rao@example.com have any open tickets? Which is the most urgent?"}' | jq

# Embeddings
curl -s localhost:8080/api/assistant/similarity -H 'Content-Type: application/json' \
  -d '{"query": "I want my money back", "candidates": ["How do I request a refund?", "Reset my password", "Where is my parcel?"]}' | jq

# Token budget: rejected before any model call
curl -s localhost:8080/api/assistant/chat -H 'Content-Type: application/json' \
  -d "{\"question\": \"$(printf 'word %.0s' {1..3000})\"}" | jq
```

A `/chat` answer has this shape (illustrative values):

```json
{ "answer": "Ask for photos of the damage and the packaging, the order number, ...",
  "model": "gemini-3.5-flash", "finishReason": "STOP",
  "usage": { "inputTokens": 141, "outputTokens": 96, "totalTokens": 237, "estimatedQuestionTokens": 17 } }
```

An `/ask` answer also lists the tools the model used, for example `"toolCalls": ["findCustomerByEmail(asha.rao@example.com)", "listTickets(C-1, OPEN)"]`.

The model's wording and the exact token counts differ from run to run. The JSON shapes are fixed.

## Where each concept lives

| Concept | Where | Teaching point |
| --- | --- | --- |
| **ChatClient** | `assistant/AssistantConfig` | One client for the whole app. The system prompt and provider options are set once. The `ChatModel` underneath is swappable (Gemini, Claude or Ollama) with a profile, without changing the service. |
| **Prompting** | `resources/prompts/support-system.st`, `prompts/triage.st` | Prompts are files, not string literals. `{company}` and `{message}` are template parameters. The customer's text is fenced in `<message>` tags so it reads as data, not instructions (prompt injection gets the full treatment on Day 14). |
| **Tokens** | `TokenUsage`, `SupportAssistant.checkBudget` | The provider reports exact `inputTokens`/`outputTokens` (what you pay). JTokkit estimates locally *before* the call. Compare `estimatedQuestionTokens` with `inputTokens`: the system prompt and tool definitions are sent on every call. |
| **Context window** | `support.assistant.max-question-tokens` | Gemini's and Claude's windows hold about 1M tokens, but every token costs money and time. The budget check rejects oversized input with `400` before anything is sent. |
| **Finish reason** | `SupportAssistant.answerText` | Always check why the model stopped: `max_tokens` means the answer was cut off, and `refusal` may come with no text at all. |
| **Structured output** | `TicketTriage`, `.entity(TicketTriage.class)` | Spring AI turns the record into a JSON schema, sends it with the prompt, and parses the reply. Enums (including the Day 3 `TicketPriority`) keep the model to values our code understands. |
| **Tool calling** | `SupportTools` (`@Tool`, `@ToolParam`) | The model asks, our code runs, the model reads the result. Descriptions are written *for the model*. Tools reuse the Day 3 services, are read-only, and return `{"error": ...}` instead of throwing, so the model can say "not found". |
| **Streaming** | `AssistantController.stream` → `Flux<String>` | Spring MVC writes each element as an SSE `data:` event. The first words arrive in about a second instead of after the whole answer. |
| **Embeddings** | `SupportAssistant.similarity`, `cosine`, `spring.ai.embedding.transformer.*` | An open-source Hugging Face model turns text into a 384-number vector; similar meanings point in similar directions. It runs in-process: no network call per request, no cost, data never leaves the server. Day 6 stores vectors like these in pgvector and runs the same cosine inside SQL. |
| **Provider options** | `application.yml` (model, max-output-tokens, embedding dimensions and task type), `AssistantConfig` (Claude's effort, `anthropic` profile only) | Options that belong to one provider stay with that provider: Gemini's embedding task type, Claude's effort. The service code never sees them. |

## Failures

Model problems become problem details like every other error (`api/ApiExceptionHandler`). The provider's own message is logged, not returned.

| Situation | Status | Title |
| --- | --- | --- |
| Question over the token budget | 400 | Question too large |
| Gemini (429 `RESOURCE_EXHAUSTED`) or Claude rate limit / quota | 429 | Model rate limit reached |
| `GEMINI_API_KEY` (or `ANTHROPIC_API_KEY`) missing or invalid | 503 | Model not configured |
| Ollama not running, or a network failure | 503 | Model unreachable |
| Any other provider error (Gemini 4xx/5xx, Claude errors) | 502 | Model call failed |

Verified by starting the app without any key: `/chat` reached Google's real Gemini API, was rejected with "API key not valid" and answered 503 *Model not configured*, while `/similarity` answered from the local Hugging Face model:

| Candidate for "I want my money back" | Score |
| --- | --- |
| How do I request a refund? | 0.575 |
| Reset my password | 0.330 |
| My card was charged twice | 0.298 |
| Where is my parcel? | 0.185 |

## Tests

```sh
./mvnw test        # 85 tests, no API key, no Ollama, no Docker
                   # (the first run downloads the ~90 MB Hugging Face embedding model once)
```

| Test | Kind | Shows |
| --- | --- | --- |
| `SupportAssistantTest` | Real `ChatClient` on a **mocked `ChatModel`** | The system prompt is rendered, usage is mapped, a refusal is handled, JSON is parsed into `TicketTriage`, and the **full tool loop** (model asks for `getTicket(3)`, we run it, the result goes back in the next request). Also the token budget, streaming, and cosine ranking. |
| `ModelProviderSelectionTest` | `@SpringBootTest` per profile | Default = Gemini chat + Hugging Face embeddings; `ollama` = Ollama for both; `anthropic` = Claude chat + Hugging Face embeddings; always exactly one EmbeddingModel |
| `OpenSourceEmbeddingTest` | The **real** Hugging Face model, no mocks | 384 dimensions; "I want my money back" is closest to "How do I request a refund?", although they share no content word |
| `ClaudeRequestTest` | `@SpringBootTest` (`anthropic` profile) with the real Anthropic SDK pointed at a **local fake server** | The actual request body: `claude-opus-5-5`, `max_tokens: 16000`, `output_config.effort: medium`, no `temperature`, the system prompt, and the tool schemas with descriptions |
| `SupportToolsTest` | Plain JUnit + Mockito | Tools are ordinary methods. "Not found" becomes an error result the model can read. |
| `AssistantControllerTest` | `@WebMvcTest` | Validation, the token-budget 400, 503/502 mapping (including Gemini's invalid key, 429 quota and 503 overload), and SSE output (`data:Hel\n\ndata:lo\n\n`) |

> Unit tests check what *our* code sends and how it handles replies. They can't tell you whether the model answers *well*. That is what evaluations are for (golden datasets on Day 7, agent evals on Day 14).

## Suggested 2-hour session

| Time | Activity |
| --- | --- |
| 0:00–0:15 | Tokens, context window, cost. Call `/chat` and read `usage`. Ask: why is `inputTokens` so much bigger than `estimatedQuestionTokens`? |
| 0:15–0:35 | Prompting: walk through `support-system.st`. Change one rule live (e.g. "answer in one sentence") and call again. Show the 400 from the token budget. |
| 0:35–0:50 | Streaming: run `curl -N` on `/chat/stream` beside `/chat`. Discuss when streaming matters (chat UIs, long answers) and when it doesn't (triage). |
| 0:50–1:10 | Structured output: `/triage` with three different emails. Show the JSON schema inside the prompt (`SupportAssistantTest.triageParsesTheJsonReplyIntoARecord`). |
| 1:10–1:35 | Tool calling: `/ask` with a question that needs two tools. Read `toolCalls`. Walk through `askRunsTheToolTheModelRequestsAndSendsBackTheResult` to show the loop step by step. |
| 1:35–1:50 | Embeddings: `/similarity` with "money back" vs. "refund" vs. "password". Preview Day 6: store these vectors and search them. |
| 1:50–2:00 | Explain-back: "What is a token and why do we budget them?" "Who executes a tool call, the model or us?" "Why does structured output use enums?" |

Optional comparison: run the same `/triage` and `/ask` requests with the `anthropic` or `ollama` profile and compare them with Gemini. The service code is identical; only the model changes.

## Capstone (Employee Platform): Day 5 checklist

- [ ] `ChatClient` with a system prompt loaded from a template file, with the company name as a parameter.
- [ ] `POST /api/employee-assistant/chat` returning the answer and the token usage.
- [ ] A token budget that rejects oversized questions with `400` before any model call.
- [ ] Structured output: classify an HR request into a record (`category`: LEAVE / PAYROLL / BENEFITS / IT / OTHER, `urgency`, `summary`).
- [ ] Two read-only tools over the Day 3 employee data (e.g. `findEmployeeByEmail`, `getLeaveBalance`), with `toolCalls` in the response.
- [ ] A streaming endpoint (SSE).
- [ ] Tests on a mocked `ChatModel`: the system prompt is sent, the structured output parses, and one full tool-call round trip.
- [ ] Stretch: `/similarity` over five HR policy titles. That's the warm-up for Day 6's policy Q&A.

## Looking ahead

- **Day 6 (RAG):** the embeddings from `/similarity` go into pgvector, and Day 4's ETL shape ingests the support docs (clean, chunk, embed, upsert). `/chat` gains retrieved context and citations.
- **Day 9 (resilience):** model calls get timeouts, retry with backoff for 429/5xx, a circuit breaker, and a fallback answer.
- **Day 10 (MCP):** the `SupportTools` methods become MCP tools (`get_customer`, `get_ticket`) for any MCP client.
- **Day 14 (security):** prompt injection through the triage message, data leakage across customers, and output validation.

Still missing for production: no authentication on `/api/assistant` (anyone could spend your tokens), no per-user rate limit, no conversation memory (each call stands alone), no prompt caching for the repeated system prompt, and no LLM tracing.
