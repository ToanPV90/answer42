# 2. Current Architecture and Issues

## 2.1 Frontend Upload Flow

1. **Vaadin `Upload` component**  
   - Uses a `MemoryBuffer` to buffer the entire file in heap.  
   - `Upload` configured with accepted file types and max files = 1.  
   - Listeners update `fileUploaded` flag on success/failure.

2. **Conversion to `MultipartFile`**  
   - In `UploadPaperView.uploadPaper()`, calls  
     ```java
     MultipartFile file = PapersViewHelper.createMultipartFileFromBuffer(buffer);
     ```
   - `PapersViewHelper` implements `MultipartFile` by loading `MemoryBuffer.getInputStream()`.

3. **HTTP Transfer**  
   - Browser → Vaadin server over HTTP POST.  
   - Entire file travels from client to server before service invocation.

## 2.2 Backend Upload Flow

1. **Spring Boot Controller or View**  
   - `UploadPaperView` calls `paperService.uploadPaper(...)` directly—no separate controller layer.

2. **`PaperService.uploadPaper()`**  
   - Creates user directory under `uploads/papers/<userId>`.  
   - Generates a random UUID filename preserving extension.  
   - **File Copy**:  
     ```java
     Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
     ```  
     - Uses default `Files.copy` with small internal buffer (~8 KB).  
     - Runs on the HTTP request thread.

   - Persists `Paper` entity: title, authors, metadata, fileInfo.  
   - Triggers Spring Batch job synchronously (if configured).

## 2.3 Configuration Defaults

- **Spring Multipart Settings**:  
  - `spring.servlet.multipart.enabled=true` (default thresholds apply).  
  - No explicit `max-file-size` or `max-request-size` configured → default unlimited.

- **Vaadin UI**:  
  - No server‐side size validation; only client‐side info message “PDF up to 20MB”.

## 2.4 Identified Performance Bottlenecks

| Layer              | Issue                                                             |
| ------------------ | ----------------------------------------------------------------- |
| **UI (Vaadin)**    | Full file buffered in JVM heap → OOM risk for large uploads.      |
| **HTTP Thread**    | Copying large file runs on servlet thread → blocks request threads |
| **I/O Throughput** | `Files.copy` uses small buffer → increases context switches & I/O calls |
| **Memory Usage**   | No streaming → GC spikes & high heap consumption.                 |
| **Request Limits** | No multipart size limits → clients can upload arbitrarily large files |
| **Batch Trigger**  | Synchronous batch job submission further costs on request thread   |

## 2.5 Impact on System

- **Heap Pressure**: Large PDFs (50–200MB) can exhaust heap, leading to crashes.  
- **Thread Starvation**: During long uploads, servlet pool threads are tied up → reduces concurrent users.  
- **Slow I/O**: Default copy buffer severely limits throughput; high I/O wait times.  
- **Service Availability**: Unbounded uploads can DoS the service.  
- **Monitoring Gaps**: No metrics on upload sizes or durations.

---

End of Page 02 (approx. 80 lines). Proceed to recommendations in next page.
