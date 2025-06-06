# Large-File Upload Performance Plan

This README tracks progress through the six-page design and follow-on implementation tasks for optimizing large file uploads.

## How to Use

- Review each section’s Markdown in this folder.
- Mark tasks as complete by changing `[ ]` to `[x]`.
- Follow the step-by-step plan under **Implementation**.
- Update tests and monitoring once code changes are in place.

---

## Documentation (All pages created)

- [x] 01_introduction.md  
- [x] 02_current_architecture_and_issues.md  
- [x] 03_recommendations.md  
- [x] 04_implementation_steps.md  
- [x] 05_testing_and_validation.md  
- [x] 06_future_considerations.md  

---

## Implementation Steps

### Frontend (Vaadin UI)

- [ ] Replace `MemoryBuffer` with `FileBuffer` in `UploadPaperView`  
- [ ] Add file-rejected listener to enforce size limit  

### Spring Boot Configuration

- [ ] Add `spring.servlet.multipart.*` properties to `application.properties`  
- [ ] Create `application-prod.properties` overrides if needed  

### File I/O

- [ ] Add `FileUtil.copyLarge(InputStream, Path)` utility  
- [ ] Refactor `PaperService.uploadPaper()` to call `FileUtil.copyLarge`  

### Asynchronous Transfer

- [ ] Implement `FileTransferService.transfer(...)` with `@Async`  
- [ ] Update `uploadPaper()` to call the async transfer method  

### Threading

- [ ] Define `fileTaskExecutor` bean in `AsyncConfig`  
- [ ] Verify async tasks use the custom executor  

### Monitoring & Metrics

- [ ] Inject `MeterRegistry` and record `upload.timer` and `upload.size`  
- [ ] Expose metrics endpoint and validate in Prometheus/Grafana  

### Rate Limiting

- [ ] Integrate Bucket4j or Spring Cloud RateLimiter for `/uploadPaper`  
- [ ] Add global exception handler for `RateLimitExceededException`  

---

## Testing & Validation

- [ ] Unit tests for `FileUtil.copyLarge` (1KB, 5MB, 50MB)  
- [ ] Unit tests for `FileTransferService.transfer`  
- [ ] Integration tests (Vaadin UI + backend) for ≤50MB and >50MB uploads  
- [ ] Performance benchmarks (50MB/s target)  
- [ ] Concurrency tests (10 parallel uploads)  
- [ ] Verify Micrometer metrics and alerts  

---

## Review & Release

- [ ] Code review completed  
- [ ] Merge feature branch `large-file-upload-optimizations`  
- [ ] Publish release notes and bump version  
- [ ] Monitor production metrics for upload performance  

---
