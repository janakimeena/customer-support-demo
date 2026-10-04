# Day 4 interactive checkpoints

Small programs to stop on during the Day 4 session (design patterns through ETL). Each one takes 3–7 minutes: learners **predict** what happens, **run** it, then **fix** or **discuss** it. They mirror the real code in `../day4-support-service`, but each is a single file with no dependencies.

The answers are in [ANSWERS.md](ANSWERS.md). Leave that file out if you share this folder with learners before class.

## Run

JDK 21, no build tool. Each file runs straight from source:

```sh
cd day4-interactive
java C2_StrategyWriter.java
```

The Mockito exercise (`mockito/PartialRejectionTest.java`) is the exception. It is copied into the Day 4 project and run with Maven (see the instructions in that file).

## Where each checkpoint fits

The times match the suggested 2-hour session in `../day4-support-service/README.md`.

| # | File | When | Min | Learners do | Core? |
| --- | --- | --- | --- | --- | --- |
| 1 | `C1_NaiveImporter.java` | 0:00 hook | 5 | Read, answer 5 questions, find the duplicate customer | Core |
| 2 | `C2_StrategyWriter.java` | 0:15, Strategy | 5 | Predict output, add a writer without touching `run()` | Core |
| 3 | `C3_AdapterVendor.java` | 0:20, Adapter | 7 | Make 4 checks PASS, discuss "ACME, INC." | Core |
| 4 | `C4_FactorySwitch.java` | 0:28, Factory | 4 | Uncomment `SFTP`, predict which method breaks | Optional |
| 5 | `C5_BuilderBug.java` | 0:32, Builder | 6 | Spot the swapped `int`s, finish the builder | Optional |
| 6 | `C6_LazyPaging.java` | 0:38, after the CRM adapter | 5 | Predict which pages are fetched and closed | Core |
| 7 | `C7_Idempotency.java` | 0:45, before running the import twice | 6 | Predict the second run, make the upsert idempotent | Core |
| 8 | `C8_SelfInvocation.java` | 0:58, transactions | 4 | Predict how many BEGINs, map it to the real code | Optional |
| 9 | `mockito/PartialRejectionTest.java` | 1:05, Mockito | 20 | Finish two tests (replaces the exercise in the Day 4 README) | Core |

Core checkpoints add about 50 minutes. With a tight session, run the optional ones as predict-only questions on screen (about 1 minute each).

## How to run a checkpoint

1. **Show the header comment only.** Every file starts with PREDICT / TRY / ASK.
2. **Make everyone commit to a prediction.** Have them write it down or show fingers ("how many pages get fetched?"). Asking the room only lets the fastest person answer.
3. **Run it.** The surprise is the lesson. Ask someone who predicted wrong to explain the actual output.
4. **Connect it to the real class.** Every ASK list ends with a question pointing into `day4-support-service`, so open that file next.

For TRY tasks, pair learners up: one types, one reads the checks. Each TRY file prints FAIL until it's done, so pairs know when they've finished.
