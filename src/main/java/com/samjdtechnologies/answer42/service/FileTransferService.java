package com.samjdtechnologies.answer42.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.samjdtechnologies.answer42.util.FileUtil;

/**
 * Service to offload file transfer operations asynchronously.
 */
@Service
public class FileTransferService {

    private final MeterRegistry registry;

    public FileTransferService(MeterRegistry registry) {
        this.registry = registry;
    }


    /**
     * Copy the contents of a MultipartFile to the target Path on a separate thread.
     *
     * @param file   the uploaded file
     * @param target the destination path
     * @return completable future that completes when copy is done
     */
    @Async("taskExecutor")
    public CompletableFuture<Void> transfer(MultipartFile file, Path target) {
        Timer.Sample sample = Timer.start(registry);
        try {
            FileUtil.copyLarge(file.getInputStream(), target);
        } catch (IOException e) {
            throw new RuntimeException(
            String.format("File transfer failed for %s: %s", target, e.getMessage()), e);
        } finally {
            sample.stop(registry.timer("upload.timer"));
            registry.summary("upload.size").record(file.getSize());
        }
        return CompletableFuture.completedFuture(null);
    }

}
