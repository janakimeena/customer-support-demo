# RAG demo: Building and Evaluating a RAG System with Spring AI

A small, standalone demo of the guide *Building and Evaluating a RAG System with Spring AI (Java)*.
Spring AI 2.0.1, Spring Boot 4.1, Java 21, Ollama (local models) and PGvector.

```
Course files ──► DocumentReader ──► TokenTextSplitter ──► PGvector (embeds on add)      ← ingestion, once
                                                              │ top-k chunks
Question ──► ChatClient ──► [transform ─► retrieve ─► post-process ─► augment] ──► model ──► answer + sources
                                                                                  └──► evaluators (gold set)
```

## Run it

```bash
cd rag-demo
docker compose up -d                                          # pgvector on 5438, Ollama on 11434
docker compose exec ollama ollama pull nomic-embed-text       # embeddings (768 dims)
docker compose exec ollama ollama pull qwen2.5:7b             # chat model
./mvnw spring-boot:run                                        # ingests src/main/resources/docs on first start
```

The app also downloads missing models on first use (`spring.ai.ollama.init.pull-model-strategy: when_missing`).
On a small or CPU-only machine, use a smaller chat model:
`./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.ai.ollama.chat.options.model=qwen2.5:1.5b"`

The first start logs `Ingested 17 chunks`. Later starts skip ingestion because the store is not empty.

## Ask questions

```bash
# mode: simple (QuestionAnswerAdvisor) | modular (RetrievalAugmentationAdvisor, default) | advanced
curl -s -X POST localhost:8080/api/ask -H 'Content-Type: application/json' \
  -d '{"question":"What is the difference between an interface and an abstract class?"}'

# Metadata filter (section 6.3): only Unit 1, so a collections question is refused
curl -s -X POST localhost:8080/api/ask -H 'Content-Type: application/json' \
  -d '{"question":"How does HashMap handle collisions?","filter":"unit == 1"}'

# Out of scope: the assistant declines instead of guessing
curl -s -X POST localhost:8080/api/ask -H 'Content-Type: application/json' \
  -d '{"question":"Who won the 2026 IPL final?"}'

# Follow-up questions (advanced mode: chat memory + CompressionQueryTransformer)
curl -s -X POST localhost:8080/api/ask -H 'Content-Type: application/json' \
  -d '{"question":"What is a HashMap?","mode":"advanced","conversationId":"s1"}'
curl -s -X POST localhost:8080/api/ask -H 'Content-Type: application/json' \
  -d '{"question":"How does it handle collisions?","mode":"advanced","conversationId":"s1"}'
```

Every response holds `answer` plus `sources` (file, PDF page, similarity score, excerpt). The augmented prompt
is logged at DEBUG by `SimpleLoggerAdvisor`.

## Evaluate

```bash
./mvnw test           # fast unit tests only (TopNPostProcessor, Hit@k/MRR maths); no models needed
./mvnw test -Peval    # the @Tag("eval") tests: real model calls against the running stack
```

`-Peval` runs `RelevancyEvalTest` (section 7.2) and `RagEvaluationSuite` (7.6). The suite runs all 20 questions in
`src/test/resources/eval/gold-set.json` (16 answerable, 4 out of scope) and prints:

```
===== RAG EVALUATION =====
Hit@5 / MRR / Relevancy / Faithfulness / Correctness / Refusal rate
```

The suite needs the fact-checking judge: `docker compose exec ollama ollama pull bespoke-minicheck`. Calibrate the
thresholds on your first baseline run.

## Where things are

| Guide section | File |
|---|---|
| 3. Setup | `pom.xml`, `compose.yaml`, `src/main/resources/application.yml` |
| 4. Ingestion (ETL) | `ingest/IngestionService.java`, startup runner in `RagApplication.java` |
| 5.1 / 5.2 / 6.1 RAG clients | `config/RagConfig.java` (`simpleRagClient`, `modularRagClient`, `advancedRagClient`) |
| 5.3 REST endpoint | `web/AskController.java` |
| 6.2 Post-processor | `config/TopNPostProcessor.java` |
| 7.1 Gold set | `src/test/resources/eval/gold-set.json`, `eval/GoldItem.java` |
| 7.2 Relevancy | `eval/RelevancyEvalTest.java` |
| 7.3 Faithfulness | `eval/EvalConfig.java` (bespoke-minicheck, `eval` profile) |
| 7.4 Correctness judge | `eval/CorrectnessEvaluator.java` |
| 7.5 Hit@k, MRR | `eval/RetrievalMetrics.java` |
| 7.6 Full harness | `eval/RagEvaluationSuite.java` |

Course documents are in `src/main/resources/docs/`. A `unitN-` file name prefix becomes the `unit` metadata used
by filters. Add your own PDFs or `.md` files there, then drop the table so they are ingested:
`docker compose exec pgvector psql -U postgres -d ragdb -c "drop table vector_store"`.

## Differences from the guide (Spring AI 2.0.1)

- `spring-boot-starter-webmvc` instead of `spring-boot-starter-web` (its Boot 4 name).
- PGvector is on host port **5438** so it doesn't clash with a local Postgres on 5432.
- `ChatClient.Builder.defaultOptions(...)` takes `ChatOptions.builder()`, not built `ChatOptions`.
- `OllamaChatModel.builder().options(...)` instead of `.defaultOptions(...)`.
- `FactCheckingEvaluator.forBespokeMinicheck(builder)` instead of `new FactCheckingEvaluator(builder)`. The
  factory also uses the prompt format bespoke-minicheck expects.
- `TypeReference` comes from `tools.jackson.core.type` (Jackson 3).
- **Bug found while testing:** if the model repeats the original question as one of `MultiQueryExpander`'s
  variants, the advisor fails with `IllegalStateException: Duplicate key Query[...]`. `RagConfig` wraps the
  expander so duplicate queries are dropped.
- The refusal check accepts the guide's "don't know" phrase and the advisor's built-in empty-context reply.
- `/api/ask` takes `mode`, `conversationId` and `filter`, so one endpoint demos all three pipelines.
