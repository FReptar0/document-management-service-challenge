# ADR-0002: Streaming uploads — bypass Spring multipart, use commons-fileupload2 on raw request

- **Status:** Accepted
- **Date:** 2026-05-03
- **Tags:** memory, upload, streaming, performance, multipart

## Context
**Hard constraint:** JVM heap = 50 MB. Per-file size ≤ 500 MB. Concurrent uploads ≤ 10. Combined worst case ≈ 5 GB of in-flight bytes. Anything that buffers or copies the file body into the heap collapses the system.

Spring MVC's default multipart resolver (`StandardServletMultipartResolver`) delegates to the servlet container (Tomcat). Tomcat buffers parts to disk after `spring.servlet.multipart.file-size-threshold` is exceeded — but the path **still copies the body in chunks via heap buffers** before flushing to disk, and `MultipartFile.getInputStream()` is realized after the part has been fully received. Empirically, under load this can pin tens of MB of heap per request. With 10 in flight, OOM is realistic.

We need a path where:
1. Parsing the multipart envelope reads small fixed-size chunks (bounded heap, regardless of file size).
2. The file body is exposed as a single `InputStream` that the controller hands directly to the storage adapter — no intermediate file or buffer.
3. The storage adapter forwards to MinIO with a fixed part size (5 MB — S3 multipart minimum) without buffering more.

## Decision
**Disable Spring's multipart processing** (`spring.servlet.multipart.enabled=false`) and parse the request body manually with **`org.apache.commons:commons-fileupload2-jakarta`** — the maintained Jakarta-namespace successor to `commons-fileupload`.

### Controller skeleton (illustrative)

```java
@PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<Void> upload(HttpServletRequest request) throws Exception {
    JakartaServletFileUpload upload = new JakartaServletFileUpload();
    FileItemInputIterator it = upload.getItemIterator(request);
    UploadCommand cmd = null;

    while (it.hasNext()) {
        FileItemInput item = it.next();
        if ("metadata".equals(item.getFieldName()) && item.isFormField()) {
            cmd = objectMapper.readValue(item.getInputStream(), UploadCommand.class);
            validator.validate(cmd);
        } else if ("file".equals(item.getFieldName())) {
            require(cmd, "metadata must be sent before file");
            try (InputStream in = item.getInputStream()) {
                useCase.execute(cmd, in, item.getName(), item.getContentType());
            }
        }
    }
    return ResponseEntity.status(HttpStatus.CREATED).build();
}
```

### Adapter skeleton

```java
minioClient.putObject(
    PutObjectArgs.builder()
        .bucket(props.bucket())
        .object(objectKey)
        .stream(inputStream, -1L /* unknown total */, 5L * 1024 * 1024 /* 5 MB part */)
        .contentType(contentType)
        .build());
```

Using `size = -1` with `partSize = 5 MB` instructs the MinIO SDK to do multipart upload with bounded buffers per part. Memory cost per active upload ≈ one part-size buffer (~5 MB) + small SDK overhead. Ten concurrent uploads ≈ ~55 MB of temporary buffers — over budget if all peaks line up, so we further mitigate:

- Cap concurrent uploads at 10 via Tomcat thread pool (`server.tomcat.threads.max=20`, with the search/download endpoints reserving headroom).
- Set `spring.mvc.async.request-timeout=-1` and `server.servlet.async.timeout=-1` (uploads of 500 MB on slow links take minutes; README §"Upload time limit" allows this).
- Force ordering: `metadata` part **must** arrive before `file`. Lets us short-circuit malformed requests before consuming bytes.
- Persist the DB row only **after** the storage write succeeds. On DB failure, issue a compensating `removeObject` on MinIO.

### Memory budget self-check (must hold for any change to this path)

| Item | Per upload | × 10 | Within 50 MB? |
|---|---|---|---|
| Multipart parser fixed buffer (`commons-fileupload2`) | 64–256 KB | ≤ 2.5 MB | yes |
| MinIO SDK part buffer (5 MB) | ~5 MB | ~50 MB peak | **tight** — verified by stress test |
| JVM/Hibernate/connection-pool overhead | — | ~10 MB baseline | absorbed before requests arrive |

The "tight" row above is the reason we run the **heap-bounded concurrency test** described in `ADR-0007`. If the assumption breaks (e.g., a future MinIO SDK version bumps internal buffers), the test fails loudly.

## Consequences
- **Positive:** Heap usage bounded and predictable. Upload speed is limited by network, not by buffering. Adapter is simple and testable with `Testcontainers#minio`. Backpressure is provided naturally by the Tomcat thread pool.
- **Negative:** We give up Spring's `@RequestPart` ergonomics. Multipart part ordering becomes a documented contract (`ADR-0011`). Manual error mapping for upload-specific failures is needed.
- **Risks:**
  - Clients may send `file` before `metadata` and we'd reject. **Mitigation:** documented contract + explicit 400 with hint header.
  - `commons-fileupload2-jakarta` is one library deep into transitive land. **Mitigation:** pin version explicitly; wrap behind a thin `MultipartStreamReader` so the parser is replaceable.
  - MinIO SDK `putObject` with size=-1 internally allocates per-part buffers. **Mitigation:** stress-test (`ADR-0007`) verifies the 50 MB cap holds under 10 concurrent.

## Alternatives considered
- **Spring's default multipart with `file-size-threshold=0`.** Forces every part to disk immediately, but Tomcat still copies through heap on the way to disk. Empirically not safe at 50 MB cap with 10 concurrent.
- **Spring WebFlux + reactive `Part.content()`.** Native backpressure-aware streaming. Rejected: project starter is Spring MVC; rewriting the application to WebFlux is scope creep, adds a learning surface for reviewers, and complicates JPA usage (`spring-boot-starter-data-jpa` is blocking).
- **`HttpServletRequest.getParts()` directly.** Goes through Tomcat's `DiskFileItem`; same problem as Spring's resolver.
- **Pre-signed PUT (client uploads directly to MinIO).** Elegant; skirts the requirement *"the service must handle PDF uploads"* and removes our ability to validate / normalize before persistence.
- **Chunked / resumable upload protocol (e.g., tus).** Out of scope; would change the API contract substantially.

## Links
- README §"Memory Limitation", §"Concurrent Uploads", §"Upload time limit"
- `commons-fileupload2-jakarta` documentation
- MinIO Java SDK `PutObjectArgs`
- `ADR-0001` (architecture style)
- `ADR-0005` (concurrency model)
- `ADR-0007` (heap-bounded test)
- `ADR-0011` (contract deviation: multipart)
