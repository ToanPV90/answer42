# 3. Recommendations

This section outlines targeted improvements to each layer of the upload pipeline to address the identified bottlenecks.

## 3.1 Frontend (Vaadin UI)

- **Replace MemoryBuffer**  
  Use `FileBuffer` instead of `MemoryBuffer` to stream uploads directly to disk:
  ```java
  FileBuffer fileBuffer = new FileBuffer();
  Upload upload = new Upload(fileBuffer);
  ```
- **Server‐Side Size Validation**  
  Reject files > configured threshold in the `FileBuffer` listener:
  ```java
  upload.addFileRejectedListener(e -> {
    if (e.getErrorMessage().contains("File too large")) { /* UI notification */ }
  });
  ```

## 3.2 Spring Boot Configuration

Add in `application.properties`:
```properties
spring.servlet.multipart.max-file-size=50MB
spring.servlet.multipart.max-request-size=60MB
spring.servlet.multipart.file-size-threshold=2MB
```
- **Request Size Limits**  
  Protects server from excessively large payloads.
- **Threshold**  
  Files above 2 MB spill to disk temp instead of memory.

## 3.3 File I/O Optimization

- **Buffered Copy**  
  Replace `Files.copy` with a buffered loop:
  ```java
  try (InputStream in = file.getInputStream();
       OutputStream out = Files.newOutputStream(target)) {
    byte[] buf = new byte[64 * 1024];
    int len;
    while ((len = in.read(buf)) != -1) {
      out.write(buf, 0, len);
    }
  }
  ```
  - **64 KB Buffer** reduces syscalls and context switches.
- **Commons IO**  
  Alternatively use `FileUtils.copyLarge(in, out)` for convenience.

## 3.4 Asynchronous File Transfer

- **Offload I/O**  
  Create a `FileTransferService` annotated with `@Async`:
  ```java
  @Service
  public class FileTransferService {
    @Async
    public CompletableFuture<Void> transfer(MultipartFile file, Path target) { ... }
  }
  ```
- **Apply to Upload**  
  In `PaperService.uploadPaper()`, call `fileTransferService.transfer(...)` and return before copy completes.

## 3.5 Threading & Concurrency

- **Configure Task Executor**  
  Define custom `ThreadPoolTaskExecutor` for async file I/O:
  ```java
  @Bean
  public TaskExecutor fileTaskExecutor() {
    ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
    exec.setCorePoolSize(4);
    exec.setMaxPoolSize(8);
    exec.setQueueCapacity(100);
    return exec;
  }
  ```
- **Detach from Request Thread**  
  Ensures servlet threads are free to handle new requests.

## 3.6 Monitoring & Metrics

- **Micrometer Histogram**  
  Record file‐size and duration:
  ```java
  meterRegistry.timer("upload.timer").record(() -> { /* transfer logic */ });
  meterRegistry.summary("upload.size").record(file.getSize());
  ```
- **Endpoint Metering**  
  Expose endpoints for Grafana dashboards.

## 3.7 Rate Limiting & Back‐Pressure

- **Spring Cloud RateLimiter** or **Bucket4j**  
  Limit concurrent uploads per user/IP.
- **Respond with 429**  
  When rate threshold exceeded:  
  ```java
  @ExceptionHandler(RateLimitException.class)
  @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
  ```

## 3.8 Error Handling & Feedback

- **Early Validation**  
  Reject oversize files before streaming:
  ```java
  if (file.getSize() > MAX_SIZE) {
    throw new FileSizeExceededException();
  }
  ```
- **Global Exception Handler**  
  Provide consistent JSON or UI error response.

---

End of Recommendations (approx. 80 lines). Proceed to implementation steps.
