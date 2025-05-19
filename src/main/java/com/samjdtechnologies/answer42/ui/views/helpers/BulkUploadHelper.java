package com.samjdtechnologies.answer42.ui.views.helpers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import com.samjdtechnologies.answer42.model.Paper;
import com.samjdtechnologies.answer42.model.Project;
import com.samjdtechnologies.answer42.model.User;
import com.samjdtechnologies.answer42.service.PaperService;
import com.samjdtechnologies.answer42.service.ProjectService;
import com.samjdtechnologies.answer42.util.LoggingUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;

/**
 * Helper class for bulk paper uploads
 */
public class BulkUploadHelper {

    private static final Logger LOG = LoggerFactory.getLogger(BulkUploadHelper.class);
    
    /**
     * Process multiple files in a bulk upload
     */
    public static void processBulkUpload(
            List<MemoryBuffer> buffers,
            Map<String, FileEntry> fileEntries,
            List<String> authors,
            User currentUser,
            Project selectedProject,
            boolean makePublic,
            PaperService paperService,
            ProjectService projectService,
            UI ui,
            Consumer<String> updateProgressCallback) {
        
        LoggingUtil.info(LOG, "processBulkUpload", "Starting bulk upload with %d files", buffers.size());
        
        // Track overall progress
        AtomicInteger processedCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        int totalFiles = buffers.size();
        
        // Process each file in a separate thread to avoid UI blocking
        new Thread(() -> {
            for (MemoryBuffer buffer : buffers) {
                String fileName = buffer.getFileName();
                FileEntry fileEntry = fileEntries.get(fileName);
                
                if (fileEntry == null) {
                    LoggingUtil.error(LOG, "processBulkUpload", "File entry not found for file: %s", fileName);
                    continue;
                }
                
                // Update status to processing
                updateFileStatus(fileEntry, FileStatus.PROCESSING, null, ui, fileEntries);
                
                try {
                    // Create a title from the filename (without extension)
                    String title = fileName;
                    if (title.contains(".")) {
                        title = title.substring(0, title.lastIndexOf("."));
                    }
                    
                    // Convert authors list to array
                    String[] authorsArray = authors.toArray(new String[0]);
                    
                    // Create MultipartFile from buffer
                    MultipartFile file = PapersHelper.createMultipartFileFromBuffer(buffer);
                    
                    // Upload the paper
                    Paper paper = paperService.uploadPaper(
                        file,
                        title,
                        authorsArray,
                        currentUser
                    );
                    
                    // Set paper visibility
                    if (makePublic) {
                        paper.setIsPublic(true);
                        paperService.savePaper(paper);
                    }
                    
                    // Add to project if selected
                    if (selectedProject != null) {
                        projectService.addPaperToProject(selectedProject.getId(), paper);
                    }
                    
                    // Update status to success
                    updateFileStatus(fileEntry, FileStatus.SUCCESS, null, ui, fileEntries);
                    fileEntry.setPaperId(paper.getId());
                    successCount.incrementAndGet();
                    
                    LoggingUtil.info(LOG, "processBulkUpload", "Successfully processed file: %s, Paper ID: %s",
                        fileName, paper.getId());
                    
                } catch (Exception e) {
                    // Update status to error
                    String errorMsg = e.getMessage();
                    LoggingUtil.error(LOG, "processBulkUpload", "Error processing file %s: %s", fileName, errorMsg, e);
                    updateFileStatus(fileEntry, FileStatus.ERROR, errorMsg, ui, fileEntries);
                    errorCount.incrementAndGet();
                }
                
                // Update progress
                int processed = processedCount.incrementAndGet();
                updateProgress(processed, totalFiles, ui, updateProgressCallback);
            }
            
            // Show final notification
            ui.access(() -> {
                String message = String.format("Bulk upload complete: %d/%d successful, %d errors",
                    successCount.get(), totalFiles, errorCount.get());
                
                Notification notification = Notification.show(
                    message,
                    5000,
                    Notification.Position.BOTTOM_START
                );
                
                if (errorCount.get() > 0) {
                    notification.addThemeVariants(NotificationVariant.LUMO_WARNING);
                } else {
                    notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                }
            });
            
            LoggingUtil.info(LOG, "processBulkUpload", "Bulk upload completed. Success: %d, Errors: %d, Total: %d",
                successCount.get(), errorCount.get(), totalFiles);
            
        }, "BulkUploadProcessor").start();
    }
    
    /**
     * Update the status of a file in the file entries map
     */
    private static void updateFileStatus(FileEntry fileEntry, FileStatus status, String statusDetail, UI ui, Map<String, FileEntry> fileEntries) {
        // Run in UI thread to avoid concurrency issues with the UI
        ui.access(() -> {
            fileEntry.setStatus(status);
            fileEntry.setStatusDetail(statusDetail);
            
            LoggingUtil.debug(LOG, "updateFileStatus", "File %s status updated to %s",
                fileEntry.getFileName(), status);
        });
    }
    
    /**
     * Update the progress of the bulk upload
     */
    private static void updateProgress(int processed, int total, UI ui, Consumer<String> updateProgressCallback) {
        if (updateProgressCallback != null) {
            ui.access(() -> {
                double percentComplete = (double) processed / total * 100;
                String progressText = String.format("Processing: %d/%d files (%.1f%%)", processed, total, percentComplete);
                updateProgressCallback.accept(progressText);
                
                LoggingUtil.debug(LOG, "updateProgress", progressText);
            });
        }
    }
    
    /**
     * Convert a list of MemoryBuffers to a map of file entries
     */
    public static Map<String, FileEntry> createFileEntriesMap(List<MemoryBuffer> buffers) {
        Map<String, FileEntry> fileEntries = new ConcurrentHashMap<>();
        
        for (MemoryBuffer buffer : buffers) {
            String fileName = buffer.getFileName();
            long fileSize = 0;
            try {
                fileSize = buffer.getInputStream().available();
            } catch (IOException e) {
                LoggingUtil.error(LOG, "createFileEntriesMap", "Error getting file size for %s: %s",
                    fileName, e.getMessage(), e);
            }
            
            FileEntry entry = new FileEntry(fileName, fileSize);
            fileEntries.put(fileName, entry);
            
            LoggingUtil.debug(LOG, "createFileEntriesMap", "Created file entry for %s (size: %d bytes)",
                fileName, fileSize);
        }
        
        return fileEntries;
    }
    
    /**
     * Parse authors list from a comma-separated string
     */
    public static List<String> parseAuthors(String authorsInput) {
        if (authorsInput == null || authorsInput.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        // Split by comma, trim each author, and remove empty entries
        return Arrays.stream(authorsInput.split(","))
            .map(String::trim)
            .filter(author -> !author.isEmpty())
            .toList();
    }
}
