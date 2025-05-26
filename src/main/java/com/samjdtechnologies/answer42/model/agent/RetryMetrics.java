package com.samjdtechnologies.answer42.model.agent;

import java.util.concurrent.atomic.AtomicLong;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Per-agent retry metrics tracking.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RetryMetrics {
    
    @Builder.Default
    private final AtomicLong totalAttempts = new AtomicLong(0);
    
    @Builder.Default
    private final AtomicLong totalRetries = new AtomicLong(0);
    
    @Builder.Default
    private final AtomicLong successfulRetries = new AtomicLong(0);
    
    @Builder.Default
    private final AtomicLong failedOperations = new AtomicLong(0);
    
    public void reset() {
        totalAttempts.set(0);
        totalRetries.set(0);
        successfulRetries.set(0);
        failedOperations.set(0);
    }
}
