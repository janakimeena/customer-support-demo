# Day 4 Demo — Design Patterns through ETL (Multi-Source Customer Ingestion)

This is the Day 3 support service plus an **ingestion pipeline** that imports customers from three very different sources:

| Source name | Type | What it looks like |
| --- | --- | --- |
| `east-csv` | CSV file | Messy spreadsheet export: mixed header case, extra columns, padded names, upper-case emails, blank tiers, a bad row or two |
| `partner-crm` | REST API | A partner's paged JSON API with its own vocabulary: `"Carter, Ben"`, `emailAddress`, plans `GOLD`/`SILVER`/`FREE` |
| `legacy-db` | Database table | The old system's `legacy_accounts` table: `UPPER CASE` names, one-letter plan codes, closed accounts to skip |

Every run is **Extract → Transform → Load**. The load step is a batched, **idempotent upsert**: running the same import twice changes nothing the second time. The design patterns of the day (**Strategy, Factory, Adapter, Builder**) are what keep the three sources from turning into three copies of the pipeline.

Everything from Day 3 (customers, tickets, Flyway, paging, tests) is unchanged. See `../day3-support-service/README.md`. Day 3 itself stays untouched.

## Run

```sh
cd day4-support-service
./mvnw spring-boot:run          # dev profile: starts compose.yaml (Postgres on localhost:5434), Flyway, sample data
```

Day 4 has its own database container on port **5434**, so it can run next to Day 3's (5433). The new dev-only migration `db/dev-data/V3_2__legacy_accounts.sql` creates and fills the legacy table.

## Try it

Swagger UI: **http://localhost:8080/** → *Ingestion* and *Demo partner CRM*.

```sh
curl  localhost:8080/api/ingestion/sources                                    # what can be imported
curl  localhost:8080/demo/crm/contacts?page=0                                 # the "partner API" (dev-only fake)

curl -X POST 'localhost:8080/api/ingestion/sources/east-csv/runs?dryRun=true' # predict, write nothing
curl -X POST  localhost:8080/api/ingestion/sources/east-csv/runs              # import
curl -X POST  localhost:8080/api/ingestion/sources/east-csv/runs              # again: inserted 0, updated 0
curl -X POST  localhost:8080/api/ingestion/sources/partner-crm/runs
curl -X POST  localhost:8080/api/ingestion/sources/legacy-db/runs
curl 'localhost:8080/api/customers?size=50'                                   # 5 → 12 customers
curl -X POST  localhost:8080/api/ingestion/sources/nope/runs                  # 404 problem detail
```

A run returns a report:

```json
{ "source": "east-csv", "type": "CSV", "dryRun": false, "status": "COMPLETED",
  "read": 8, "inserted": 2, "updated": 1, "unchanged": 1, "rejected": 4,
  "rejections": [
    { "origin": "customers-east.csv:5", "reason": "email: must be a well-formed email address" },
    { "origin": "customers-east.csv:6", "reason": "name: must not be blank" },
    { "origin": "customers-east.csv:7", "reason": "tier: unknown value 'GOLD'" },
    { "origin": "customers-east.csv:9", "reason": "duplicate email, first seen at customers-east.csv:4" } ] }
```

Expected results on a fresh database, run in this order (verified on PostgreSQL 17):

| Source | read | inserted | updated | unchanged | rejected | Second run (ins / upd / unch / rej) |
| --- | --- | --- | --- | --- | --- | --- |
| `east-csv` | 8 | 2 | 1 (Ben → PREMIUM) | 1 | 4 | 0 / 0 / 4 / 4 |
| `partner-crm` | 7 | 3 | 0 (Ben already PREMIUM) | 2 | 2 | 0 / 0 / 5 / 2 |
| `legacy-db` | 6 (1 closed skipped) | 2 | 1 (Eve → PREMIUM) | 1 | 2 | 0 / 0 / 4 / 2 |

The dev log shows each chunk as one `Executing SQL batch update [MERGE INTO customers ...]` (chunk size 3 in dev, 100 by default).

To reset: `docker compose down -v`. Flyway rebuilds the schema and sample data on the next start.

## The pipeline

```
 application.yml                CustomerIngestionService.run(name, dryRun)
 support.ingest.sources ─name─▶
                                CustomerSourceFactory ──▶ CustomerSource ──▶ CustomerNormalizer ──▶ chunk ──▶ CustomerWriter
                                     (Factory)           (Strategy; CSV /    (Transform: clean     (dedupe,    (Strategy: batch
                                                          CRM & legacy are     + validate)          size N)     upsert | dry run)
                                                          Adapters)
                                          every step reports into ──▶ IngestionReport.Builder (Builder) ──▶ JSON report
```

| Step | Class | Notes |
| --- | --- | --- |
| Extract | `ingest/csv/CsvCustomerSource`, `ingest/crm/CrmCustomerSource`, `ingest/legacy/LegacyAccountsSource` | Each returns a lazy `Stream<RawCustomer>`; nothing is loaded into memory all at once. CRM pages are fetched only when the previous page has been consumed. |
| Transform | `ingest/CustomerNormalizer` | Same rules for every source: trim/collapse names, lower-case emails, tier or default, Bean Validation. Returns the sealed `Normalized` type: `ImportCustomer` or `Rejection` (like Day 1's `TicketParseResult`). |
| Load | `ingest/load/BatchUpsertWriter`, `ChunkPlanner` | One `IN (...)` lookup per chunk plus a JDBC batch of standard-SQL `MERGE` statements. Each chunk is one transaction. |
| Orchestrate | `ingest/CustomerIngestionService` | Streams, de-duplicates by email, chunks, picks the writer, builds the report. Depends only on interfaces. |
| API | `api/IngestionController` | `GET /api/ingestion/sources`, `POST /api/ingestion/sources/{name}/runs?dryRun=` |

## Design patterns: where they are and why

| Pattern | Where | The problem it solves here |
| --- | --- | --- |
| **Strategy** | `CustomerSource` (3 implementations), `CustomerWriter` (`BatchUpsertWriter`, `DryRunWriter`) | The pipeline is written once. *How* to read and *how* to write are swappable objects. Dry run is just a different writer, with no `if (dryRun)` scattered through the code. |
| **Factory** | `CustomerSourceFactory.create(SourceDefinition)` | Construction knowledge (resource loader, HTTP client with base URL, JdbcTemplate) lives in one place. The service only says "give me source X". The `switch` over the `SourceType` enum is exhaustive, so a new type won't compile until it's handled. |
| **Adapter** | `CrmCustomerSource` adapts `CrmClient` (the "vendor SDK"); `LegacyAccountsSource` adapts the legacy table | Foreign interfaces (paged responses, ResultSets) and foreign vocabularies (`"Last, First"`, `GOLD`, `P`/`S`, upper case) are translated at the edge into `RawCustomer`. Nothing downstream knows these systems exist. |
| **Builder** | `IngestionReport.Builder` | A 13-field immutable report whose counters grow during the run. The builder collects events (`recordRead`, `reject`, `add(chunk)`) and owns the small rules (timing, capping the rejection list, status). Compare with `RestClient.builder()` and `Health.up().withDetail(...)`, which you've already used. |

Also worth pointing at:
- **Strategy vs. Template Method.** `CustomerIngestionService.run` is a fixed algorithm with pluggable steps, built by composition (Strategy) rather than inheritance (Template Method).
- **Sources come from config, not from the caller.** Clients pick a source *by name*, so the API can't be abused to read arbitrary files or call internal URLs (SSRF).

## ETL concerns this demo handles

| Concern | How |
| --- | --- |
| **Idempotency** | Upsert keyed on the natural key (email). `WHEN MATCHED AND (name or tier changed)` means unchanged rows aren't rewritten and `version` isn't bumped. Re-running after a failure is always safe. |
| **Batching** | `JdbcTemplate.batchUpdate`: one prepared statement, N parameter sets, one round trip per chunk. The JPA alternative (`save()` per row) costs a SELECT plus a write per row. |
| **Bypassing JPA responsibly** | The SQL maintains `updated_at` and `version` itself, so optimistic locking still works for API users. |
| **Business rules still apply** | `ChunkPlanner` enforces `max-customers` (ingestion doesn't go through `CustomerService`). |
| **Bad data** | Reported per record with its origin (`file:line`, `crm contact 7005`, `legacy account L-0006`), never fatal. Duplicates within a run are rejected. |
| **Source failure** | Read errors become `502 Bad Gateway` with the **partial report**. Earlier chunks stay committed; just re-run. |
| **Memory** | Streams in, chunks out. Only one chunk and the set of seen emails are held in memory. |
| **Dry run** | Uses the same planner as the real writer, so the prediction matches what the real run does. |

`MERGE` is standard SQL (PostgreSQL 15+ and H2, so the same SQL runs in tests). PostgreSQL's native `INSERT ... ON CONFLICT (email) DO UPDATE ... WHERE` is the classic alternative and is worth showing side by side.

## Tests

```sh
./mvnw test        # 61 tests, no Docker needed
```

| Test | Kind | Shows |
| --- | --- | --- |
| `CustomerIngestionServiceTest` | JUnit + **Mockito** (`@Mock`, `@Captor`, `verify`, `never`, `verifyNoInteractions`, `thenAnswer`) | Chunk sizes 2/2/1, strategy choice (dry run never touches the upsert writer), duplicate and invalid records, capped rejection list, source failure → partial report + stream closed |
| `CrmCustomerSourceTest` | Mockito mock of the adaptee | Adapter mapping, and laziness: `findFirst()` fetches page 0 only (`verifyNoMoreInteractions`) |
| `CrmClientTest` | `MockRestServiceServer` | The real HTTP call: URL, query params, JSON → records, 5xx → exception |
| `CsvCustomerSourceTest` | Plain JUnit, in-memory resources | Header by name in any order and case, optional column, short rows, missing column / missing file |
| `CustomerNormalizerTest` | Plain JUnit | Cleaning rules, default tier, all violations reported in a stable order |
| `CustomerSourceFactoryTest` | Plain JUnit | Right class per type, config validation |
| `CustomerIngestionIntegrationTest` | `@SpringBootTest` + MockMvc on H2 | Real SQL: upsert counts, `version` bumped exactly once, **second run is a no-op**, dry run writes nothing, 404 |

> When to mock: mock the collaborators you want to isolate from (source, writer, CRM client). Don't mock pure logic (the normalizer). For SQL and HTTP, use the real thing or a close stand-in (H2, `MockRestServiceServer`).

## Suggested 2-hour session

| Time | Activity |
| --- | --- |
| 0:00–0:15 | Problem: three sources, three formats. Sketch the naive version (`if csv … else if api …` inside one method) and list its pain points. |
| 0:15–0:45 | Walk the pipeline: Strategy (`CustomerSource`) → Adapter (`CrmCustomerSource`, `LegacyAccountsSource`) → Factory → Builder. Run each source in dry-run mode in Swagger. |
| 0:45–1:05 | Load: JPA `save()` vs. JDBC batch, `MERGE`, idempotency. Run an import twice and watch the SQL log and the `version` column. |
| 1:05–1:30 | Mockito: read `CustomerIngestionServiceTest` together, then have the learner write one new test (e.g. "a chunk the writer partly rejects still counts the rest"). |
| 1:30–1:50 | Capstone kickoff (below). |
| 1:50–2:00 | Explain-back: "Why is the dry run a writer and not a flag?" "What makes re-running safe?" "Where would a fourth source go?" |

Discussion seed: `east-csv` row 8 is `"Singh, Kara"`, and it imports exactly like that. The CSV source parsed the quoted comma correctly, but unlike the CRM adapter it doesn't flip "Last, First". Is that a bug? Whose job is it to fix: the source, the normalizer, or the data owner?

## Capstone (Employee Platform): Day 4 checklist

- [ ] `EmployeeSource` strategy with at least two implementations (CSV export from HR + one of: a fake HRIS REST API, or a legacy table).
- [ ] An adapter that translates a foreign vocabulary (e.g. department codes → names, `"LAST, First"` → `First Last`).
- [ ] A factory that builds sources from `application.yml`. Callers choose by name only.
- [ ] Batched upsert keyed on a natural key (employee number or work email). A second run reports 0 inserted, 0 updated.
- [ ] A report built with a builder: read / inserted / updated / unchanged / rejected + reasons.
- [ ] Mockito test of the pipeline (chunking + strategy choice) and one integration test proving idempotency.
- [ ] Stretch: ingest **policy documents** (title, category, effective date, body) the same way. They become the Day 6 RAG corpus, where Transform grows "chunk + embed" and Load becomes the vector store.

## Looking ahead

- **Day 6 (RAG):** the same Extract → Transform → Load shape ingests support documents into pgvector. Transform becomes clean + chunk + embed, and idempotent upserts keyed by document id + chunk hash keep re-ingestion cheap.
- **Day 9 (resilience):** the CRM call is the natural place for timeout, retry with backoff and a circuit breaker.

Still missing for production: runs are synchronous (use a background job + `202 Accepted` + status endpoint, or Spring Batch for restartable jobs), no authentication on the ingestion endpoint, no run history table, and no metrics per source/outcome.
