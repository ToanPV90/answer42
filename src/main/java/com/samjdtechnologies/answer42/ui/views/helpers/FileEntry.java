package com.samjdtechnologies.answer42.ui.views.helpers;

import java.util.UUID;

/**
 * Model representing a file in the bulk upload process.
 */
public class FileEntry {
    private String fileName;
    private long fileSize;
    private FileStatus status;
    private String statusDetail;
    private UUID paperId;
    
    /**
     * Constructs a new file entry with the specified name and size.
     * The status is automatically set to PENDING.
     * 
     * @param fileName the name of the file
     * @param fileSize the size of the file in bytes
     */
    public FileEntry(String fileName, long fileSize) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.status = FileStatus.PENDING;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public long getFileSize() {
        return fileSize;
    }
    
    public FileStatus getStatus() {
        return status;
    }
    
    public void setStatus(FileStatus status) {
        this.status = status;
    }
    
    public String getStatusDetail() {
        return statusDetail;
    }
    
    public void setStatusDetail(String statusDetail) {
        this.statusDetail = statusDetail;
    }
    
    public UUID getPaperId() {
        return paperId;
    }
    
    public void setPaperId(UUID paperId) {
        this.paperId = paperId;
    }
}
