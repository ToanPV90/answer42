package com.samjdtechnologies.answer42.config;

import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableAsync
@EnableScheduling
public class ThreadConfig {

    @Value("${spring.task.execution.thread-name-prefix}")
    private String executionThreadNamePrefix;

    @Value("${spring.task.execution.shutdown.await-termination}")
    private boolean executionAwaitTermination;

    @Value("${spring.task.execution.shutdown.await-termination-period}")
    private String executionAwaitTerminationPeriod;

    @Value("${spring.task.scheduling.thread-name-prefix}")
    private String schedulingThreadNamePrefix;

    @Value("${spring.task.scheduling.shutdown.await-termination}")
    private boolean schedulingAwaitTermination;

    @Value("${spring.task.scheduling.shutdown.await-termination-period}")
    private String schedulingAwaitTerminationPeriod;

    /**
     * Configures and creates a thread pool task executor for asynchronous task execution.
     * 
     * @return an Executor instance configured with thread pool settings from application properties
     */
    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix(executionThreadNamePrefix);
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(25);
        executor.setWaitForTasksToCompleteOnShutdown(executionAwaitTermination);
        // Parse duration string to seconds
        long terminationSeconds = parseDurationToSeconds(executionAwaitTerminationPeriod);
        executor.setAwaitTerminationSeconds((int) terminationSeconds);
        executor.initialize();
        return executor;
    }

    /**
     * Configures and creates a thread pool task scheduler for scheduled task execution.
     * 
     * @return a ThreadPoolTaskScheduler instance configured with settings from application properties
     */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix(schedulingThreadNamePrefix);
        scheduler.setPoolSize(4);
        scheduler.setWaitForTasksToCompleteOnShutdown(schedulingAwaitTermination);
        // Parse duration string to seconds
        long terminationSeconds = parseDurationToSeconds(schedulingAwaitTerminationPeriod);
        scheduler.setAwaitTerminationSeconds((int) terminationSeconds);
        return scheduler;
    }

    private long parseDurationToSeconds(String duration) {
        if (duration == null || duration.isEmpty()) {
            return 0;
        }
        
        // Handle "20s" format
        if (duration.endsWith("s")) {
            return Long.parseLong(duration.substring(0, duration.length() - 1));
        }
        
        // Handle "1m" format - convert to seconds
        if (duration.endsWith("m")) {
            return Long.parseLong(duration.substring(0, duration.length() - 1)) * 60;
        }
        
        // Default - try to parse as seconds
        return Long.parseLong(duration);
    }
}
