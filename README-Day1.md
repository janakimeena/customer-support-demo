# Day 1 Demo — Parallel Ticket File Processing

A dependency-free Java 21 demo for the first training day. It uses records, a sealed result type, pattern matching for `switch`, `ExecutorService`, `CompletableFuture`, and Java 21 virtual threads.

## Prerequisite

Install a JDK 21. This demo intentionally uses only the JDK so it can be run without Maven or external libraries.

## Compile

From the project root:

```sh
mkdir -p out
javac -d out $(find src/main/java -name '*.java')
```

## Run with a fixed platform-thread pool

```sh
java -cp out com.example.support.TicketDemo
```

Choose the pool size or input directory:

```sh
java -cp out com.example.support.TicketDemo --executor=fixed --workers=2 data/tickets
```

## Run with virtual threads (Java 21)

```sh
java -cp out com.example.support.TicketDemo --executor=virtual data/tickets
```

The demo submits one task per CSV file using `CompletableFuture.supplyAsync`. The file tasks run concurrently; reports are printed in stable filename order. Invalid rows are represented as rejected results instead of stopping processing of the remaining rows.

## Run smoke tests

```sh
mkdir -p out
javac -d out $(find src/main/java src/test/java -name '*.java')
java -ea -cp out com.example.support.TicketFileProcessorTest
```

## Concepts demonstrated

- `SupportTicket` is an immutable Java record with constructor invariants.
- `TicketParseResult` is sealed and distinguishes accepted tickets from rejected CSV rows.
- A pattern-matching `switch` handles every permitted result type.
- The same workload can run on a bounded platform-thread pool or a virtual-thread-per-task executor.
- CSV fields support quoted commas and escaped quotes; timestamps use ISO-8601 instants.

This is an instructional starter, not a production ingestion service. A production CSV pipeline should use a thoroughly tested CSV library, explicit schema/versioning, structured logging, metrics, and operational retry/dead-letter handling.
