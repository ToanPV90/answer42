package com.samjdtechnologies.answer42.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.samjdtechnologies.answer42.config.AIConfig;
import com.samjdtechnologies.answer42.util.LoggingUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test to verify Perplexity API connection with the new "sonar" model.
 * This test requires valid API keys in the .env file.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.ai.perplexity.chat.options.model=sonar-pro",
    "spring.ai.token-logging.enabled=true"
})
public class PerplexityIntegrationTest {
    
    private static final Logger LOG = LoggerFactory.getLogger(PerplexityIntegrationTest.class);
    
    @Autowired
    @Qualifier("perplexityChatModel")
    private OpenAiChatModel perplexityChatModel;
    
    @Autowired
    @Qualifier("perplexityChatClient")
    private ChatClient perplexityChatClient;
    
    @Autowired
    private AIConfig aiConfig;
    
    @Test
    public void testPerplexityConnectionWithSonarModel() {
        LoggingUtil.info(LOG, "testPerplexityConnectionWithSonarModel", 
            "Testing Perplexity API connection with 'sonar' model");
        
        try {
            // Simple test prompt
            String prompt = "What is the capital of France? Please provide a brief answer.";
            
            // Make the API call
            ChatResponse response = perplexityChatClient
                .prompt()
                .user(prompt)
                .call()
                .chatResponse();
            
            // Verify we got a response
            assertNotNull(response, "ChatResponse should not be null");
            assertNotNull(response.getResult(), "Response result should not be null");
            assertNotNull(response.getResult().getOutput(), "Response output should not be null");
            
            String content = response.getResult().getOutput().toString();
            assertNotNull(content, "Response content should not be null");
            assertFalse(content.isEmpty(), "Response content should not be empty");
            
            // Log the response
            LoggingUtil.info(LOG, "testPerplexityConnectionWithSonarModel", 
                "Successfully received response from Perplexity 'sonar' model");
            LoggingUtil.info(LOG, "testPerplexityConnectionWithSonarModel", 
                "Response: %s", content);
            
            // Log token usage if available
            if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                aiConfig.logTokenUsage(response, "sonar");
            }
            
            // Verify the response contains expected content
            assertTrue(content.toLowerCase().contains("paris"), 
                "Response should mention Paris as the capital of France");
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "testPerplexityConnectionWithSonarModel", 
                "Failed to connect to Perplexity API with 'sonar' model", e);
            fail("Failed to connect to Perplexity API: " + e.getMessage());
        }
    }
    
    @Test
    public void testPerplexityResearchQuery() {
        LoggingUtil.info(LOG, "testPerplexityResearchQuery", 
            "Testing Perplexity API with a research-style query");
        
        try {
            // Research-style prompt
            String prompt = "What are the latest advancements in quantum computing as of 2024? Provide 2-3 key points.";
            
            // Make the API call
            ChatResponse response = perplexityChatClient
                .prompt()
                .user(prompt)
                .call()
                .chatResponse();
            
            // Verify we got a response
            assertNotNull(response, "ChatResponse should not be null");
            assertNotNull(response.getResult(), "Response result should not be null");
            assertNotNull(response.getResult().getOutput(), "Response output should not be null");
            
            String content = response.getResult().getOutput().toString();
            assertNotNull(content, "Response content should not be null");
            assertFalse(content.isEmpty(), "Response content should not be empty");
            
            // Log the response
            LoggingUtil.info(LOG, "testPerplexityResearchQuery", 
                "Successfully received research response from Perplexity 'sonar' model");
            LoggingUtil.debug(LOG, "testPerplexityResearchQuery", 
                "Research response: %s", content);
            
            // Log token usage if available
            if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                aiConfig.logTokenUsage(response, "sonar");
            }
            
            // Verify the response contains research-related content
            assertTrue(content.toLowerCase().contains("quantum") || 
                      content.toLowerCase().contains("computing") ||
                      content.toLowerCase().contains("qubit"), 
                "Response should contain quantum computing related content");
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "testPerplexityResearchQuery", 
                "Failed to execute research query with Perplexity API", e);
            fail("Failed to execute research query: " + e.getMessage());
        }
    }
}
