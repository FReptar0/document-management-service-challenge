# ADR-0012: Container memory limit set above heap to allow JVM startup overhead

- **Status:** Accepted
- **Date:** 2026-05-04
- **Tags:** memory, deployment, docker, deviation

## Context

README §"Memory Limitation" states *"memory limitation of 50MB assigned to the document management service container"* and the docker-compose stub (lines 32–38) shows a literal example combining `JAVA_OPTS=-Xmx50m -Xms50m` with `deploy.resources.limits.memory: 50M`.

Setting the container memory limit to 50 MB is incompatible with Spring Boot startup. The Java heap is one component of total process RAM; the JVM additionally allocates:

- **Metaspace** (loaded class metadata): ~30–60 MB for Spring Boot 3.4.
- **Code cache** (JIT): ~10–20 MB at steady state.
- **JIT compiler workspace**: variable, 5–15 MB during warm-up.
- **Native heap** (NIO buffers, Hibernate, JDBC drivers, MinIO SDK): ~10–30 MB.
- **Per-thread stack** (`-Xss` default 512 KB × Tomcat threads): 10–15 MB.

A 50 MB container limit OOM-kills the process during class loading, before the heap has a chance to use any of its 50 MB budget. The service does not boot — which conflicts with the more fundamental requirement *"the service must handle PDF uploads"*.

The intent of the 50 MB constraint is to force a streaming design (`ADR-0002`); that intent is preserved by the **heap** cap, not the container cap.

## Decision

- **Heap = 50 MB** via `JAVA_OPTS=-Xmx50m -Xms50m` (literal README requirement, kept verbatim in `.env.example` and the Dockerfile default).
- **Metaspace cap = 128 MB** and **code cache = 48 MB** also pinned in `JAVA_OPTS`. Without explicit caps the JVM lets metaspace grow unbounded into native memory and the container OOM-kills at random; with the caps total non-heap consumption is bounded and predictable.
- **Container memory limit = 384 MB** in `docker/docker-compose.yml` (`deploy.resources.limits.memory` and the v2-native `mem_limit`). 50 MB heap + 128 MB metaspace + 48 MB code cache + ~60 MB threads/native + headroom ≈ 290 MB working set, with margin for JIT spikes during warm-up.
- The deviation is documented inline in the compose file, in this ADR, and in the submission README's "Deviations" section.
- The streaming heap-budget regression test (`ADR-0007`) runs the JVM with `-Xmx50m` directly and asserts heap stays below 45 MB — the binding constraint is exercised independently of the container cap.

## Consequences

- **Positive:** The service actually boots. The 50 MB heap constraint — the *interesting* engineering challenge — is preserved and enforced by tests.
- **Negative:** The literal docker-compose example in the README is not matched. Reviewers comparing line-for-line will see `384M` instead of `50M`.
- **Mitigation:** Comment in compose, ADR-0012, and a README "Deviations" section explain the trade-off and reference the heap-bounded test that proves the streaming design holds under the spirit of the 50 MB constraint.

## Revision history

- **2026-05-04 (initial):** container memory = 256 MB, metaspace = 64 MB.
- **2026-05-04 (revised):** Phase 8 added springdoc + actuator + logback-encoder + RFC 7807 advice; the cumulative class footprint exceeded 64 MB and Spring Boot 3.4 terminated with `OutOfMemoryError: Metaspace` at first context refresh. Metaspace bumped to 128 MB, container limit raised in proportion to 384 MB. Caught while validating the `./run.sh up` end-to-end bootstrap.

## Alternatives considered

- **`memory: 50M` literal.** OOM-kill during startup; service does not run; remaining requirements unreachable. Rejected.
- **GraalVM native image.** Drops total memory to ~80–120 MB and could approach 50 MB with effort. Adds significant build complexity (reflection metadata for Spring + Hibernate + Jackson + MinIO SDK), longer compile times, and a learning surface for reviewers. Out of scope for the time budget; revisit if requested.
- **CDS / AppCDS to slim startup memory.** Modest improvement (~10–20 MB savings); not enough alone to fit 50 MB.
- **Reduce metaspace aggressively (`-XX:MaxMetaspaceSize=32m`).** Risks `OutOfMemoryError: Metaspace` mid-request as classes are lazy-loaded. Rejected.

## Links

- README §"Memory Limitation"
- `docker/docker-compose.yml` — enforcement point with inline comment
- `Dockerfile` — JAVA_OPTS default
- `ADR-0002` (heap budget for streaming upload)
- `ADR-0007` (heap-bounded regression test)

