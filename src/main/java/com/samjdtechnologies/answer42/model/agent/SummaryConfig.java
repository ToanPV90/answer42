package com.samjdtechnologies.answer42.model.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration class for summary types in ContentSummarizerAgent.
 * Defines word count ranges and guidance for different summary levels.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SummaryConfig {
    private int minWords;
    private int maxWords;
    private String guidance;
    
    public int getTargetWords() { 
        return (minWords + maxWords) / 2; 
    }
    
    public String getTargetRange() { 
        return minWords + "-" + maxWords + " words"; 
    }
}
