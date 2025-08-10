# Ollama Fallback Completion Plan

## Current Status Analysis

After reviewing your actual architecture, I can see that **AgentRetryPolicy** uses direct dependency injection to get fallback agents. Currently you have 7 fallback agents already implemented:

### ✅ Existing Fallback Agents (7/9):
1. ContentSummarizerFallbackAgent ✅
2. ConceptExplainerFallbackAgent ✅ 
3. MetadataEnhancementFallbackAgent ✅
4. PaperProcessorFallbackAgent ✅
5. QualityCheckerFallbackAgent ✅
6. CitationFormatterFallbackAgent ✅
7. CitationVerifierFallbackAgent ✅

### ❌ Missing Fallback Agents (2):
8. **PerplexityResearchFallbackAgent** - MISSING
9. **RelatedPaperDiscoveryFallbackAgent** - MISSING

## Agent Analysis from AgentRetryPolicy

From your `getRetryConfigForAgent` method, I can see these 9 agent types that all need fallbacks:
- PAPER_PROCESSOR ✅
- CONTENT_SUMMARIZER ✅  
- CONCEPT_EXPLAINER ✅
- METADATA_ENHANCER ✅
- QUALITY_CHECKER ✅
- CITATION_FORMATTER ✅
- CITATION_VERIFIER ✅
- PERPLEXITY_RESEARCHER ❌ (Missing fallback)
- RELATED_PAPER_DISCOVERY ❌ (Missing fallback)

**All 9 agents require Ollama fallback implementations for complete system resilience.**

## Implementation Plan

### Phase 1: Create PerplexityResearchFallbackAgent

**File**: `src/main/java/com/samjdtechnologies/answer42/service/agent/PerplexityResearchFallbackAgent.java`

This agent will need to:
- Extend `OllamaBasedAgent`
- Handle research queries using local knowledge
- Provide simplified research results without external API calls
- Return structured `PerplexityResearchResult` objects

**Key Challenges**:
- PerplexityResearchAgent requires external APIs for real research
- Fallback will need to provide mock/simplified research results
- Should focus on claim verification using local knowledge

### Phase 2: Create RelatedPaperDiscoveryFallbackAgent

**File**: `src/main/java/com/samjdtechnologies/answer42/service/agent/RelatedPaperDiscoveryFallbackAgent.java`

This agent will need to:
- Extend `OllamaBasedAgent`
- Handle paper discovery using local knowledge only
- Provide simplified discovery results without external API calls
- Return structured `RelatedPaperDiscoveryResult` objects

**Key Challenges**:
- RelatedPaperDiscoveryAgent requires external APIs (Crossref, Semantic Scholar) for real discovery
- Fallback will need to provide mock/basic discovery results
- Should focus on text-based similarity using local analysis

### Phase 3: Update AgentRetryPolicy

Update the constructor to inject the new fallback agents:

```java
public AgentRetryPolicy(ThreadConfig threadConfig, AgentCircuitBreaker circuitBreaker,
                       // Existing agents...
                       PerplexityResearchFallbackAgent perplexityResearchFallback,
                       RelatedPaperDiscoveryFallbackAgent relatedPaperDiscoveryFallback) {
    // Set new agents
    this.perplexityResearchFallback = perplexityResearchFallback;
    this.relatedPaperDiscoveryFallback = relatedPaperDiscoveryFallback;
}
```

Update the `getFallbackAgent` method:

```java
private AIAgent getFallbackAgent(AgentType agentType) {
    switch (agentType) {
        // Existing cases...
        case PERPLEXITY_RESEARCHER:
            return perplexityResearchFallback;
        case RELATED_PAPER_DISCOVERY:
            return relatedPaperDiscoveryFallback;
        default:
            return null;
    }
}
```

## Technical Implementation Details

### PerplexityResearchFallbackAgent Structure

```java
@Component
@ConditionalOnProperty(name = "spring.ai.ollama.enabled", havingValue = "true", matchIfMissing = false)
public class PerplexityResearchFallbackAgent extends OllamaBasedAgent {
    
    @Override
    public AgentType getAgentType() {
        return AgentType.PERPLEXITY_RESEARCHER;
    }
    
    @Override
    protected AgentResult processWithConfig(AgentTask task) {
        // Extract research parameters
        // Generate local knowledge-based research
        // Return PerplexityResearchResult with fallback data
    }
    
    // Local research methods without external APIs
    private PerplexityResearchResult generateLocalResearch(ResearchParameters params);
    private List<ResearchResult> createFallbackQueryResults(List<ResearchQuery> queries);
    private PerplexityResearchResult.ResearchSynthesis synthesizeLocalFindings();
}
```

### Key Implementation Points

1. **Content Truncation**: Use `MAX_LOCAL_CONTENT_LENGTH = 8000` for Ollama processing
2. **Simplified Research**: Focus on basic claim verification without external sources  
3. **Structured Results**: Return proper `PerplexityResearchResult` objects
4. **Fallback Quality**: Include notifications that external research is limited
5. **Error Handling**: Provide meaningful fallback when local processing fails

## Next Steps

1. **Clarify 9th Agent**: Please identify which is the 9th agent that needs a fallback
2. **Implement PerplexityResearchFallbackAgent**: Create the research fallback agent
3. **Implement Second Fallback**: Create the second missing fallback agent  
4. **Update AgentRetryPolicy**: Add both new agents to dependency injection
5. **Test Integration**: Verify all agents can fall back to Ollama when cloud providers fail

## Questions for Clarification

1. What is the 9th agent that needs a fallback agent?
2. Should RelatedPaperDiscoveryAgent have a fallback despite needing external APIs?
3. Are there other agent types I haven't identified in the codebase?

Please clarify which agents need fallbacks so I can complete the implementation plan.
