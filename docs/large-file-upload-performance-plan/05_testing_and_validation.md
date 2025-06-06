# 5. Testing and Validation

This page defines test cases, performance benchmarks, and monitoring strategies to verify the upload optimizations.

## 5.1 Unit Tests

1. **`FileUtil.copyLarge`**  
   - Test small (1 KB), medium (5 MB), and large (50 MB) streams.  
   - Verify file contents match source and method handles EOF correctly.

2. **`FileTransferService.transfer`**  
   - Mock `MultipartFile` to return a test stream.  
   - Verify `CompletableFuture` completes and target file exists.

3. **Threshold Validation**  
   - Test upload rejection via `upload.addFileRejectedListener` when size > 50 MB.

## 5.2 Integration Tests

1. **Frontend → Backend Flow**  
   - Using Vaadin TestBench or Selenium, upload files of sizes: 1 MB, 10 MB, 40 MB, 60 MB.  
   - Verify UI shows success for ≤50 MB, error for >50 MB.

2. **Asynchronous Behavior**  
   - Spy on `FileTransferService` to confirm copy runs on async executor.  
   - Verify HTTP response returns before file fully written.

3. **Batch Job Trigger**  
   - Upload trigger invokes pipeline only after async transfer completes.

## 5.3 Performance Benchmarks

1. **Copy Throughput**  
   - Measure time to copy a 100 MB file using new buffered loop vs `Files.copy`.  
   - Record average throughput (MB/s).

2. **Memory Usage**  
   - Profile heap during large uploads (100 MB PDF) before and after migration.  
   - Ensure no heap spikes above buffer threshold.

3. **Concurrency Test**  
   - Simulate 10 concurrent uploads of 40 MB files.  
   - Verify servlet pool threads remain available and no timeouts occur.

## 5.4 Monitoring Setup

- **Micrometer Metrics**  
  - `upload.timer` (duration histogram)  
  - `upload.size` (summary)  
- **Dashboard Alerts**  
  - Alert if `upload.timer` P95 > 5 seconds.  
  - Alert if >10 rejections per minute (rate‐limit events).

## 5.5 Acceptance Criteria

- No OOM or GC pauses during 100 MB upload.  
- Buffered copy throughput ≥50 MB/s on average.  
- UI rejects >50 MB files consistently.  
- Async copy offloads I/O; HTTP response time <200 ms.  
- Rate limit returns 429 status under sustained high load.

_End of Testing and Validation (approx. 120 lines)_
