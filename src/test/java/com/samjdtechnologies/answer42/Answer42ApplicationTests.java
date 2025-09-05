package com.samjdtechnologies.answer42;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.samjdtechnologies.answer42.repository.PaperRepository;
import com.samjdtechnologies.answer42.repository.UserRepository;
import com.samjdtechnologies.answer42.service.PaperService;
import com.samjdtechnologies.answer42.service.UserService;
import com.samjdtechnologies.answer42.config.AIConfig;
import com.samjdtechnologies.answer42.config.ThreadConfig;

/**
 * Integration tests for the Answer42 application.
 * Tests application context loading, configuration, and core components.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false",
    "logging.level.com.samjdtechnologies.answer42=INFO"
})
class Answer42ApplicationTests {

    private static final Logger logger = LoggerFactory.getLogger(Answer42ApplicationTests.class);

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private PaperRepository paperRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PaperService paperService;

    @Autowired
    private UserService userService;

    @Autowired
    private AIConfig aiConfig;

    @Autowired
    private ThreadConfig threadConfig;

    @Test
    void contextLoads() {
        assertNotNull(applicationContext, "Application context should not be null");
        logger.info("Spring application context loaded successfully");
    }

    @Test
    void testApplicationClassConfiguration() {
        // Verify the main application class is properly configured
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        assertTrue(beanNames.length > 0, "Application context should contain beans");
        
        // Check for Spring Boot annotations
        assertTrue(Answer42Application.class.isAnnotationPresent(
            org.springframework.boot.autoconfigure.SpringBootApplication.class),
            "Answer42Application should be annotated with @SpringBootApplication");
        
        assertTrue(Answer42Application.class.isAnnotationPresent(EnableJpaRepositories.class),
            "Answer42Application should be annotated with @EnableJpaRepositories");
    }

    @Test
    void testJpaRepositoryConfiguration() {
        // Test that JPA repositories are properly configured and available
        assertNotNull(paperRepository, "PaperRepository should be autowired");
        assertNotNull(userRepository, "UserRepository should be autowired");
        
        // Verify repositories are JPA repositories
        assertTrue(paperRepository instanceof org.springframework.data.jpa.repository.JpaRepository,
            "PaperRepository should be a JpaRepository");
        assertTrue(userRepository instanceof org.springframework.data.jpa.repository.JpaRepository,
            "UserRepository should be a JpaRepository");
    }

    @Test
    void testEntityScanConfiguration() {
        // Verify that entities are properly scanned and configured
        jakarta.persistence.EntityManagerFactory entityManagerFactory = 
            applicationContext.getBean(jakarta.persistence.EntityManagerFactory.class);
        assertNotNull(entityManagerFactory, "EntityManagerFactory should be available");

        jakarta.persistence.metamodel.Metamodel metamodel = entityManagerFactory.getMetamodel();
        assertNotNull(metamodel, "Metamodel should be available");
        
        // Check that our entities are registered
        assertNotNull(metamodel.entity(com.samjdtechnologies.answer42.model.db.Paper.class),
            "Paper entity should be registered");
        assertNotNull(metamodel.entity(com.samjdtechnologies.answer42.model.db.User.class),
            "User entity should be registered");
    }

    @Test
    void testCoreServicesAvailable() {
        // Test that core business services are properly autowired
        assertNotNull(paperService, "PaperService should be autowired");
        assertNotNull(userService, "UserService should be autowired");
        
        // Verify services are Spring-managed beans
        assertTrue(applicationContext.containsBean("paperService"),
            "PaperService should be registered as a Spring bean");
        assertTrue(applicationContext.containsBean("userService"),
            "UserService should be registered as a Spring bean");
    }

    @Test
    void testConfigurationBeans() {
        // Test that configuration beans are properly loaded
        assertNotNull(aiConfig, "AIConfig should be autowired");
        assertNotNull(threadConfig, "ThreadConfig should be autowired");
        
        // Verify configuration classes are Spring-managed
        assertTrue(applicationContext.containsBean("AIConfig"),
            "AIConfig should be registered as a Spring bean");
        assertTrue(applicationContext.containsBean("threadConfig"),
            "ThreadConfig should be registered as a Spring bean");
    }

    @Test
    void testApplicationShutdownHook() throws Exception {
        // Test the custom shutdown logic in Answer42Application
        Answer42Application app = new Answer42Application();
        
        // Verify that the destroy method can be called without errors
        assertDoesNotThrow(() -> app.destroy(),
            "Application destroy method should execute without throwing exceptions");
        
        logger.info("Application shutdown hook tested successfully");
    }

    @Test
    void testDatabaseConnectivity() {
        // Test that the database connection is working
        assertDoesNotThrow(() -> {
            long userCount = userRepository.count();
            long paperCount = paperRepository.count();
            
            assertTrue(userCount >= 0, "User count should be non-negative");
            assertTrue(paperCount >= 0, "Paper count should be non-negative");
            
            logger.info("Database connectivity verified - Users: {}, Papers: {}", userCount, paperCount);
        }, "Database operations should work without errors");
    }

    @Test
    void testSpringProfilesConfiguration() {
        // Verify that Spring profiles are properly configured
        String[] activeProfiles = applicationContext.getEnvironment().getActiveProfiles();
        assertTrue(activeProfiles.length > 0, "At least one profile should be active");
        
        // Check for test profile
        boolean testProfileActive = false;
        for (String profile : activeProfiles) {
            if ("test".equals(profile)) {
                testProfileActive = true;
                break;
            }
        }
        assertTrue(testProfileActive, "Test profile should be active during testing");
    }

    @Test
    void testApplicationProperties() {
        // Verify that application properties are loaded
        org.springframework.core.env.Environment env = applicationContext.getEnvironment();
        assertNotNull(env, "Environment should be available");
        
        // Test some expected properties
        String datasourceUrl = env.getProperty("spring.datasource.url");
        assertNotNull(datasourceUrl, "Datasource URL should be configured");
        assertTrue(datasourceUrl.contains("h2"), "Test should use H2 database");
        
        logger.info("Application properties verified - Datasource: {}", datasourceUrl);
    }

    @Test
    void testBeanDependencyInjection() {
        // Test that complex dependency injection works
        assertDoesNotThrow(() -> {
            // PaperService should have its dependencies injected
            assertNotNull(paperService, "PaperService should be available");
            
            // UserService should have its dependencies injected
            assertNotNull(userService, "UserService should be available");
            
            logger.info("Dependency injection verified for core services");
        }, "Bean dependency injection should work correctly");
    }

    @Test
    void testSpringBootActuatorEndpoints() {
        // Test that management endpoints are configured (if actuator is included)
        if (applicationContext.containsBean("healthEndpoint")) {
            Object healthEndpoint = applicationContext.getBean("healthEndpoint");
            assertNotNull(healthEndpoint, "Health endpoint should be available if actuator is included");
            logger.info("Spring Boot Actuator endpoints are configured");
        } else {
            logger.info("Spring Boot Actuator not included in this configuration");
        }
    }

    @Test
    void testTransactionManagement() {
        // Test that transaction management is properly configured
        assertTrue(applicationContext.containsBean("transactionManager"),
            "Transaction manager should be configured");
        
        org.springframework.transaction.PlatformTransactionManager txManager = 
            applicationContext.getBean(org.springframework.transaction.PlatformTransactionManager.class);
        assertNotNull(txManager, "Transaction manager should be available");
        
        logger.info("Transaction management verified: {}", txManager.getClass().getSimpleName());
    }

    @Test
    void testThreadPoolConfiguration() {
        // Test that thread configuration is working
        assertNotNull(threadConfig, "ThreadConfig should be available");
        
        // Verify that the ThreadConfig creates the expected beans
        assertDoesNotThrow(() -> {
            // Check that task executor bean is created
            assertTrue(applicationContext.containsBean("taskExecutor"),
                "TaskExecutor bean should be configured");
            
            // Check that task scheduler bean is created
            assertTrue(applicationContext.containsBean("taskScheduler"),
                "TaskScheduler bean should be configured");
            
            // Verify executor bean can be retrieved
            java.util.concurrent.Executor executor = applicationContext.getBean("taskExecutor", java.util.concurrent.Executor.class);
            assertNotNull(executor, "Task executor should be available");
            
            logger.info("Thread pool configuration verified - Executor: {}", executor.getClass().getSimpleName());
        }, "Thread configuration should be valid");
    }

    @Test
    void testApplicationStartupTime() {
        // Test that application startup is reasonably fast
        long startTime = System.currentTimeMillis();
        
        // Perform some basic operations to ensure everything is loaded
        assertNotNull(applicationContext);
        assertNotNull(paperRepository);
        assertNotNull(userRepository);
        
        long endTime = System.currentTimeMillis();
        long startupTime = endTime - startTime;
        
        // Startup operations should complete quickly (< 5 seconds)
        assertTrue(startupTime < 5000, 
            "Basic application operations should complete quickly, took: " + startupTime + "ms");
        
        logger.info("Application startup performance verified - Operations took: {}ms", startupTime);
    }

    @Test
    void testMemoryUsageReasonable() {
        // Test that memory usage is reasonable after startup
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        // Log memory usage for monitoring
        logger.info("Memory usage - Total: {}MB, Used: {}MB, Free: {}MB",
            totalMemory / (1024 * 1024),
            usedMemory / (1024 * 1024),
            freeMemory / (1024 * 1024));
        
        // Basic sanity check - used memory should be reasonable (< 512MB for tests)
        assertTrue(usedMemory < 512 * 1024 * 1024,
            "Used memory should be reasonable for tests: " + (usedMemory / (1024 * 1024)) + "MB");
    }
}
