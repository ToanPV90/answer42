package com.samjdtechnologies.answer42.model.pipeline;

/**
 * Exception thrown during pipeline processing operations.
 */
public class PipelineException extends RuntimeException {
    
    public PipelineException(String message) {
        super(message);
    }
    
    public PipelineException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public PipelineException(Throwable cause) {
        super(cause);
    }
}
