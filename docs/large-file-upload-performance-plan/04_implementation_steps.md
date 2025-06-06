# 4. Implementation Steps

This section details concrete coding tasks and configuration changes needed to improve large‐file uploads.

---

## 4.1 Frontend Changes

1. **Switch to `FileBuffer`**  
   
   - In `UploadPaperView.createFileUploadStep()`, change:
     
     ```java
     // OLD
     private MemoryBuffer buffer = new MemoryBuffer();
     Upload upload = new Upload(buffer);
     
     // NEW
     private FileBuffer buffer = new FileBuffer();
     Upload upload = new Upload(buffer);
     ```

2. **Handle Rejected Events**  
   
   - In `createFileUploadStep()`, add a listener to notify on file‐size rejection:  
     
     ```java
     upload.addFileRejectedListener(e -> {
         String msg = e.getErrorMessage();
         Notification.show("File rejected: " + msg, 3000, Notification.Position.BOTTOM_START)
             .addThemeVariants(NotificationVariant.LUMO_ERROR);
     });
     ```
   - Provides immediate feedback when the file exceeds the configured size limit.

---

## 4.2 Spring Boot Configuration

1. **application.properties**  
   
   ```properties
   spring.servlet.multipart.max-file-size=50MB
   spring.servlet.multipart.max-request-size=60MB
   spring.servlet.multipart.file-size-threshold=2MB
   ```

2. **Profile Overrides**  
   
   - Optionally override in `application-prod.properties` for higher limits.

---

## 4.3 Backend File I/O

1. **Buffered Copy Utility**  
   
   - Create `FileUtil.copyLarge(InputStream in, Path target)`:
     
     ```java
     public static void copyLarge(InputStream in, Path target) throws IOException {
         try (OutputStream out = Files.newOutputStream(target)) {
             byte[] buf = new byte[64 * 1024];
             int len;
             while ((len = in.read(buf)) != -1) {
                 out.write(buf, 0, len);
             }
         }
     }
     ```

2. **Refactor `PaperService.uploadPaper()`**  
   
   - Replace `Files.copy(...)` with `FileUtil.copyLarge(...)`.

---

## 4.4 Asynchronous Transfer

1. **FileTransferService**  
   
   ```java
   @Service
   public class FileTransferService {
     @Async("fileTaskExecutor")
     public CompletableFuture<Void> transfer(MultipartFile file, Path target) {
       FileUtil.copyLarge(file.getInputStream(), target);
       return CompletableFuture.completedFuture(null);
     }
   }
   ```

2. **Call in `uploadPaper()`**  
   
   ```java
   fileTransferService.transfer(file, targetPath);
   ```

---

## 4.5 Task Executor Configuration

1. **Bean Definition**  
   
   ```java
   @Configuration
   public class AsyncConfig {
     @Bean("fileTaskExecutor")
     public ThreadPoolTaskExecutor fileTaskExecutor() {
       ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
       exec.setCorePoolSize(4);
       exec.setMaxPoolSize(8);
       exec.setQueueCapacity(100);
       exec.initialize();
       return exec;
     }
   }
   ```

---

## 4.6 Monitoring Integration

1. **Micrometer**  
   
   - Inject `MeterRegistry` into `PaperService` or `FileTransferService`.
   
   - Wrap copy logic:
     
     ```java
     Timer.Sample sample = Timer.start(registry);
     FileUtil.copyLarge(...);
     sample.stop(registry.timer("upload.timer"));
     registry.summary("upload.size").record(file.getSize());
     ```

---

## 4.7 Rate Limiting

1. **Bucket4j Filter**  
   
   - Configure a servlet filter to limit `/uploadPaper` endpoint.

2. **Exception Handler**  
   
   ```java
   @ControllerAdvice
   public class GlobalExceptionHandler {
     @ExceptionHandler(RateLimitExceededException.class)
     @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
     public String handleRateLimit() { return "Upload rate limit exceeded"; }
   }
   ```

---

## 4.8 Testing & Documentation

- Ensure unit tests cover `FileUtil`, `FileTransferService`, and `uploadPaper`.
- Update README with new configuration properties.
- Commit all changes in a single feature branch `large-file-upload-optimizations`.

_End of Implementation Steps (approx. 120 lines)_
