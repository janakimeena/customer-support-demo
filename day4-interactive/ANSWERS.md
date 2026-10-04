# Day 4 checkpoints: answer key (instructor only)

All outputs below were produced by running the files with JDK 21.

## C1: the naive importer

Output, final table:

```
asha@example.com     Asha Rao
eve@example.com      Eve Stone
ben@example.com      Ben Carter
ana@example.com      Ana Lee
EVE@EXAMPLE.COM      Eve stone
jon@example.com      Jon park
```

1. **A fourth source:** a new `else if` branch, which copies the dry-run handling, the email rule and the print again. The method grows with every source.
2. **Dry run:** 6 lines (an `if (!dryRun)` and a print prefix per branch). One missed copy means a "dry run" that writes.
3. **Email rule:** three copies, all different. CSV trims and lower-cases, the API branch lower-cases but doesn't trim, and legacy does neither. Result: **Eve is in the table twice.** That's the bug the RUN step asks about.
4. **Testing:** you can't test it. The data is hard-coded in the branch and the table is a static field. There's no seam to replace the API.
5. **Running twice:** `Map.put` happens to overwrite, so it looks fine. But there are no counts and no versions, and with a real `INSERT` the second run would fail on the unique email constraint.

Bonus bug: the legacy title-casing only capitalises the first letter ("Eve stone", "Jon park"). It's another rule written twice, done differently.

## C2: Strategy

**Prediction:** 3 lines per run (chunks of 2, 2 and 1). `DB` holds the 5 emails once, because the dry run wrote nothing.

**TRY solution:**

```java
CustomerWriter audited = chunk -> {
    System.out.println("  AUDIT " + chunk.size() + " customers");
    return upsert.write(chunk);
};
System.out.println("Audited run:");
run(List.of("f@x.com"), audited, 2);
```

Output adds `AUDIT 1 customers` / `wrote [f@x.com]`, and `DB` ends with `f@x.com`.

1. **Where was the decision made?** In `main`, by choosing which object to pass. Real code: `CustomerWriter writer = dryRun ? dryRunWriter : upsertWriter;` in `CustomerIngestionService.run()`.
2. **Two flags:** 4 combinations, and 8 with a third flag. With strategies, `run()` doesn't change at all.
3. Teaser for strong learners: `audited` wraps another strategy and adds behaviour. That's the **Decorator** pattern.

## C3: Adapter

**TRY solution:**

```java
static RawCustomer adapt(HelpDeskUser user) {
    return new RawCustomer("helpdesk user " + user.userId(), firstNameFirst(user.display()), user.mail(),
            tierFor(user.level()));
}

static String firstNameFirst(String display) {
    if (!display.contains(",")) {
        return titleCase(display.trim());
    }
    String[] parts = display.split(",", 2);
    return titleCase(parts[1].trim() + " " + parts[0].trim());
}

static String tierFor(int level) {
    return switch (level) {
        case 3 -> "PREMIUM";
        case 1, 2 -> "STANDARD";
        default -> "level " + level;
    };
}
```

All four checks PASS. The last line prints `"ACME, INC." became "Inc. Acme"`.

1. **Email not lower-cased:** lower-casing is a rule for *every* source, so it lives once in `CustomerNormalizer`. The adapter only translates this vendor's dialect.
2. **Unknown level passed through:** the normalizer then rejects it with a clear reason (`tier: unknown value 'level 7'`). A guess would silently downgrade a customer who may be paying for a higher tier.
3. **"ACME, INC.":** the vendor's "LAST, FIRST" rule breaks on company names. There's no perfect fix in code. Options: ask the data owner, use a separate company field, or only flip when the vendor guarantees a person. Link it to the Kara Singh row in `customers-east.csv`.

## C4: Factory

**Prediction:** after uncommenting `SFTP`:

```
C4_FactorySwitch.java:32: error: the switch expression does not cover all possible input values
```

`create()` stops compiling, which is the helpful failure. `kind()` keeps compiling. Once a `case SFTP` is added to `create()` (and the config line is uncommented), the output shows the silent bug:

```
nightly-sftp  SFTP      (database)  sftp get sftp://files.partner.com/customers.csv
```

**Takeaway:** a `default` branch turns a compile error into a wrong answer at runtime. Switch expressions over enums (and sealed types) should be exhaustive with no `default`.

1. **Attacks:** `file:/etc/passwd` reads any file the server can read. `http://169.254.169.254/...` is the cloud metadata endpoint, which can leak credentials. That's **SSRF**: the server makes requests on the attacker's behalf. Choosing by name from config makes both impossible.
2. Spring injects them into `CustomerSourceFactory`'s constructor. The service only asks the factory for a source by name, so it has no reason to know how sources are built.

## C5: Builder

**Part A:** it compiles and prints `updated=3, unchanged=1`. The correct values are 1 and 3, because `updated` and `unchanged` were passed in swapped order. Both are `int`, so neither the compiler nor a quick review catches it. Tests that happen to use equal values (1 and 1) don't catch it either.

**Part B solution:**

```java
Builder reject(String origin) {
    rejected++;
    if (rejections.size() < MAX_LISTED) {
        rejections.add(origin);
    }
    return this;
}

Builder add(int chunkInserted, int chunkUpdated, int chunkUnchanged) {
    inserted += chunkInserted;
    updated += chunkUpdated;
    unchanged += chunkUnchanged;
    return this;
}
```

Prints PASS.

1. **Events, not setters:** callers report *what happened*, and the builder does the arithmetic. Nobody can set `inserted` to the wrong total, and every caller gets the same counting rules.
2. **The cap belongs in the builder:** "count all, list N" is a rule about the report, so it lives with the report. The service loop stays about the flow. In the real code, chunk rejections from the writer go through the same `reject()`.
3. `RestClient.builder()`, `Health.up().withDetail(...)`, `UriComponentsBuilder`, `ResponseEntity.ok().header(...)`.

## C6: lazy paging

| Run | Pages fetched | "connection closed"? |
| --- | --- | --- |
| A `findFirst()` | 0 | No |
| B `limit(4)` | 0, 1 | No |
| C `count()` | 0, 1, 2 | No |
| D try-with-resources, `limit(1)` | 0 | Yes |

1. **Page 0 is fetched eagerly:** the seed `fetch(0)` is an ordinary argument to `Stream.iterate`, so it runs as soon as `read()` is called, before any terminal operation. The real `CrmCustomerSource` behaves the same way. That's why `CustomerIngestionService.open()` wraps errors from `read()` as "Cannot open source".
2. **Leaks:** A, B and C. `onClose` handlers run only when the stream is closed. Terminal operations do **not** close a stream.
3. Without the try-with-resources in `run()`, the file handle, HTTP connection or JDBC connection (for `LegacyAccountsSource`, a pooled connection) stays open. A few failed runs can exhaust the connection pool.

## C7: idempotency

**Prediction (naive code):**

```
run 1: {inserted=2, unchanged=0, updated=1}
run 2: {inserted=0, unchanged=0, updated=3}

asha@x.com  Asha Rao    PREMIUM  v1
ben@x.com   Ben Carter  PREMIUM  v2
hiro@x.com  Hiro Tanaka STANDARD v1
```

**TRY solution:** add this before the update:

```java
if (row.name.equals(in.name()) && row.tier.equals(in.tier())) {
    return "unchanged";
}
```

Output after the fix:

```
run 1: {inserted=2, unchanged=0, updated=1}
run 2: {inserted=0, unchanged=3, updated=0}

asha@x.com  Asha Rao    PREMIUM  v0
ben@x.com   Ben Carter  PREMIUM  v1
hiro@x.com  Hiro Tanaka STANDARD v0
```

The SQL equivalent is `WHEN MATCHED AND (c.name <> s.name OR c.tier <> s.tier) THEN UPDATE ...`.

1. **Version bumps hurt API users:** an agent opens Ben at version 1, a no-op import bumps him to version 2, and the agent's save gets **409 Conflict** for no reason. `updated_at` also lies, and audit logs fill with noise.
2. **Recovery after a crash:** run the import again. Chunks 1–2 report "unchanged" and the rest load. No special recovery code is needed.
3. **Hiro missing tomorrow:** nothing happens, because an upsert never deletes. That's deliberate. A source that failed halfway, or an incomplete file, must not wipe customers. If deletes matter, track `last_seen_run` and handle missing customers as a reviewed business decision (soft delete or report), not a side effect.

## C8: self-invocation

**Prediction:** A prints 3 BEGIN/COMMIT pairs, one per chunk. B prints 1 pair, around `writeAll` only.

1. Inside `writeAll`, the call is `this.writeChunk(...)`. `this` is the real object, not the proxy, so nothing intercepts it. Spring's `@Transactional` works the same way.
2. **A.** `CustomerIngestionService` calls `writer.write(chunk)` on a separate bean (the Spring proxy), so each chunk gets its own transaction.
3. **In B, chunk 3 fails:** everything rolls back, including chunks 1 and 2. In A, chunks 1 and 2 stay committed and a re-run continues from there (C7). A also keeps transactions short, so locks and connections are held briefly.
4. **Why `run()` isn't `@Transactional`:** if it were, the writer's `@Transactional` (propagation `REQUIRED`) would *join* the outer transaction. That turns the run back into one big transaction, held open while waiting on files and the partner API.

## C9: Mockito exercise

**PREDICT (before TODO 1):** an unstubbed Mockito mock returns `null` for object return types. `upsertWriter.write(...)` returns `null`, and `report.add(null)` throws:

```
NullPointerException: Cannot invoke "...ChunkResult.inserted()" because "chunk" is null
```

**Solution, test 1:**

```java
when(upsertWriter.write(anyList())).thenReturn(
        new ChunkResult(1, 0, 0, List.of(new Rejection("f:2", "customer limit of 100 reached"))));

IngestionReport report = service.run("east-csv", false);

assertThat(report.status()).isEqualTo(IngestionReport.Status.COMPLETED);
assertThat(report.read()).isEqualTo(2);
assertThat(report.inserted()).isEqualTo(1);
assertThat(report.rejected()).isEqualTo(1);
assertThat(report.rejections()).extracting(Rejection::origin).containsExactly("f:2");
```

**Solution, test 2 (stretch):**

```java
Stream<RawCustomer> failing = Stream.of(1, 2, 3).map(i -> {
    if (i == 3) {
        throw new java.io.UncheckedIOException(new java.io.IOException("connection reset"));
    }
    return raw(i, "c" + i);
});
when(factory.create(CSV)).thenReturn(source);
when(source.read()).thenReturn(failing);
when(upsertWriter.write(anyList())).thenReturn(new ChunkResult(2, 0, 0, List.of()));

assertThatThrownBy(() -> service.run("east-csv", false))
        .isInstanceOfSatisfying(IngestionFailedException.class, ex -> {
            assertThat(ex.getReport().status()).isEqualTo(IngestionReport.Status.FAILED);
            assertThat(ex.getReport().read()).isEqualTo(2);
            assertThat(ex.getReport().inserted()).isEqualTo(2);
            assertThat(ex.getReport().error()).contains("connection reset");
        });
```

Remove the `fail("TODO ...")` lines. Both tests pass (verified with `./mvnw test -Dtest=PartialRejectionTest`).

1. **Real normalizer, mocked source and writers:** the normalizer is a pure function, so mocking it would just restate its logic in the test. The source and writers touch files, HTTP and the database, which are exactly what a unit test isolates from.
2. **Unused stub:** `MockitoExtension` fails the test with `UnnecessaryStubbingException`. Strict stubs keep tests honest: a stub nobody calls usually means the test isn't testing what it claims.
