package com.samjdtechnologies.answer42.service;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.samjdtechnologies.answer42.model.db.Paper;
import com.samjdtechnologies.answer42.model.db.User;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Service responsible for launching Spring Batch pipeline jobs.
 * This service breaks the circular dependency between PaperService and the batch job configuration.
 */
@Service
public class PipelineJobLauncher {

    private static final Logger logger = LoggerFactory.getLogger(PipelineJobLauncher.class);
    
    private final JobLauncher jobLauncher;
    private final CreditService creditService;
    
    // Job will be injected lazily to avoid circular dependency
    @Autowired(required = false)
    @Lazy
    private Job paperProcessingJob;
    
    public PipelineJobLauncher(JobLauncher jobLauncher, CreditService creditService) {
        this.jobLauncher = jobLauncher;
        this.creditService = creditService;
    }
    
    /**
     * Initiate multi-agent pipeline processing for a newly uploaded paper using Spring Batch.
     * 
     * @param paper The paper to process
     * @param user The user who uploaded the paper
     * @return true if job was launched successfully, false otherwise
     */
    public boolean launchPipelineProcessing(Paper paper, User user) {
        try {
            // Check if Spring Batch is available
            if (jobLauncher == null || paperProcessingJob == null) {
                LoggingUtil.warn(logger, "launchPipelineProcessing", 
                    "Spring Batch not available, skipping pipeline processing");
                return false;
            }
            
            // Check user credits if credit service is available (assuming 30 credits for full pipeline)
            if (creditService != null && !creditService.hasEnoughCredits(user.getId(), 30)) {
                LoggingUtil.warn(logger, "launchPipelineProcessing", 
                    "User %s has insufficient credits for pipeline processing", user.getId());
                return false;
            }
            
            // Create Spring Batch job parameters
            JobParameters jobParameters = new JobParametersBuilder()
                .addString("paperId", paper.getId().toString())
                .addString("userId", user.getId().toString())
                .addDate("startTime", new Date())
                .addString("processingMode", "COMPREHENSIVE")
                .toJobParameters();
            
            // Launch Spring Batch job
            jobLauncher.run(paperProcessingJob, jobParameters);
            
            LoggingUtil.info(logger, "launchPipelineProcessing", 
                "Initiated Spring Batch pipeline processing for paper %s", paper.getId());
            
            return true;
                
        } catch (Exception e) {
            LoggingUtil.error(logger, "launchPipelineProcessing", 
                "Failed to initiate pipeline processing: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Check if pipeline processing is available.
     * 
     * @return true if both JobLauncher and paperProcessingJob are available
     */
    public boolean isPipelineAvailable() {
        return jobLauncher != null && paperProcessingJob != null;
    }
    
    /**
     * Get the required credits for full pipeline processing.
     * 
     * @return number of credits required
     */
    public int getRequiredCredits() {
        return 30; // This could be made configurable
    }
}
