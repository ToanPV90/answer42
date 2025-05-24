# 3. Embedding Generation

## OpenAI Embeddings Service

### Core Embedding Service Implementation

```java
@Service
public class EmbeddingService {
    
    private final OpenAiEmbeddingModel embeddingModel;
    private final TokenCountingService tokenCountingService;
    private final RateLimitingService rateLimitingService;
    private static final Logger LOG = LoggerFactory.getLogger(EmbeddingService.class);
    
    public EmbeddingService(
            AIConfig aiConfig, 
            TokenCountingService tokenCountingService,
            RateLimitingService rateLimitingService) {
        this.embeddingModel = aiConfig.openAiEmbeddingModel();
        this.tokenCountingService = tokenCountingService;
        this.rateLimitingService = rateLimitingService;
    }
    
    /**
     * Generate embedding for text using OpenAI's text-embedding-ada-002
     */
    public EmbeddingResult generateEmbedding(String text) {
        if (text == null || text.trim().isEmpty()) {
            return EmbeddingResult.empty();
        }
        
        try {
            // Rate limiting check
            rateLimitingService.checkEmbeddingRateLimit();
            
            // Preprocess and validate text
            String processedText = preprocessText(text);
            int tokenCount = tokenCountingService.countTokens(processedText);
            
            if (tokenCount > 8000) {
                processedText = truncateToTokenLimit(processedText, 8000);
                tokenCount = tokenCountingService.countTokens(processedText);
            }
            
            // Generate embedding
            long startTime = System.currentTimeMillis();
            
            EmbeddingRequest request = EmbeddingRequest.builder()
                .input(List.of(processedText))
                .model("text-embedding-ada-002")
                .build();
            
            EmbeddingResponse response = embeddingModel.call(request);
            long processingTime = System.currentTimeMillis() - startTime;
            
            // Extract vector data
            float[] embedding = response.getResult().getOutput().stream()
                .findFirst()
                .map(this::convertToFloatArray)
                .orElseThrow(() -> new EmbeddingException("No embedding returned"));
            
            LoggingUtil.info(LOG, "generateEmbedding", 
                "Generated embedding in %dms, tokens: %d", processingTime, tokenCount);
            
            return EmbeddingResult.builder()
                .embedding(embedding)
                .tokenCount(tokenCount)
                .processingTimeMs(processingTime)
                .inputLength(text.length())
                .processedLength(processedText.length())
                .model("text-embedding-ada-002")
                .success(true)
                .build();
                
        } catch (Exception e) {
            LoggingUtil.error(LOG, "generateEmbedding", 
                "Failed to generate embedding: %s", e, e.getMessage());
            
            return EmbeddingResult.builder()
                .success(false)
                .errorMessage(e.getMessage())
                .inputLength(text.length())
                .build();
        }
    }
    
    /**
     * Generate embeddings for multiple texts in batch
     */
    public List<EmbeddingResult> generateEmbeddings(List<String> texts) {
        if (texts.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<EmbeddingResult> results = new ArrayList<>();
        
        // Process in batches of 100 (OpenAI limit)
        for (int i = 0; i < texts.size(); i += 100) {
            int end = Math.min(i + 100, texts.size());
            List<String> batch = texts.subList(i, end);
            
            List<EmbeddingResult> batchResults = processBatch(batch);
            results.addAll(batchResults);
            
            // Rate limiting between batches
            if (end < texts.size()) {
                rateLimitingService.enforceDelay();
            }
        }
        
        return results;
    }
    
    private List<EmbeddingResult> processBatch(List<String> texts) {
        try {
            // Preprocess all texts
            List<String> processedTexts = texts.stream()
                .map(this::preprocessText)
                .map(text -> truncateToTokenLimit(text, 8000))
                .collect(Collectors.toList());
            
            // Calculate total token usage
            int totalTokens = processedTexts.stream()
                .mapToInt(tokenCountingService::countTokens)
                .sum();
            
            long startTime = System.currentTimeMillis();
            
            EmbeddingRequest request = EmbeddingRequest.builder()
                .input(processedTexts)
                .model("text-embedding-ada-002")
                .build();
            
            EmbeddingResponse response = embeddingModel.call(request);
            long processingTime = System.currentTimeMillis() - startTime;
            
            // Convert response to results
            List<Embedding> embeddings = response.getResult().getOutput();
            List<EmbeddingResult> results = new ArrayList<>();
            
            for (int i = 0; i < embeddings.size(); i++) {
                float[] embeddingVector = convertToFloatArray(embeddings.get(i));
                String originalText = texts.get(i);
                String processedText = processedTexts.get(i);
                int tokenCount = tokenCountingService.countTokens(processedText);
                
                results.add(EmbeddingResult.builder()
                    .embedding(embeddingVector)
                    .tokenCount(tokenCount)
                    .processingTimeMs(processingTime / embeddings.size()) // Distribute time
                    .inputLength(originalText.length())
                    .processedLength(processedText.length())
                    .model("text-embedding-ada-002")
                    .success(true)
                    .build());
            }
            
            LoggingUtil.info(LOG, "processBatch", 
                "Generated %d embeddings in %dms, total tokens: %d", 
                embeddings.size(), processingTime, totalTokens);
            
            return results;
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "processBatch", 
                "Failed to process embedding batch: %s", e, e.getMessage());
            
            // Return error results for all texts
            return texts.stream()
                .map(text -> EmbeddingResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .inputLength(text.length())
                    .build())
                .collect(Collectors.toList());
        }
    }
    
    private String preprocessText(String text) {
        // Clean and normalize text
        return text.trim()
            .replaceAll("\\s+", " ")          // Normalize whitespace
            .replaceAll("[\\p{Cntrl}]", "")   // Remove control characters
            .replaceAll("\\p{So}", "")        // Remove symbols
            .substring(0, Math.min(text.length(), 100000)); // Reasonable length limit
    }
    
    private String truncateToTokenLimit(String text, int maxTokens) {
        int tokenCount = tokenCountingService.countTokens(text);
        
        if (tokenCount <= maxTokens) {
            return text;
        }
        
        // Binary search for optimal truncation point
        int low = 0;
        int high = text.length();
        String result = text;
        
        while (low < high) {
            int mid = (low + high) / 2;
            String candidate = text.substring(0, mid);
            int candidateTokens = tokenCountingService.countTokens(candidate);
            
            if (candidateTokens <= maxTokens) {
                result = candidate;
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        
        return result;
    }
    
    private float[] convertToFloatArray(Embedding embedding) {
        List<Double> data = embedding.getData();
        float[] result = new float[data.size()];
        for (int i = 0; i < data.size(); i++) {
            result[i] = data.get(i).floatValue();
        }
        return result;
    }
}
```

## Embedding Generation Agent

### Multi-Agent Pipeline Integration

```java
@Component
public class EmbeddingGenerationAgent extends OpenAIBasedAgent {
    
    private final EmbeddingService embeddingService;
    private final PaperRepository paperRepository;
    private final PaperSectionRepository paperSectionRepository;
    private final EmbeddingProcessingStatusRepository statusRepository;
    private final CreditService creditService;
    
    public EmbeddingGenerationAgent(
            AIConfig aiConfig,
            ThreadConfig threadConfig,
            AgentTaskService taskService,
            AgentMemoryStoreRepository memoryRepository,
            EmbeddingService embeddingService,
            PaperRepository paperRepository,
            PaperSectionRepository paperSectionRepository,
            EmbeddingProcessingStatusRepository statusRepository,
            CreditService creditService) {
        super(aiConfig, threadConfig, taskService, memoryRepository);
        this.embeddingService = embeddingService;
        this.paperRepository = paperRepository;
        this.paperSectionRepository = paperSectionRepository;
        this.statusRepository = statusRepository;
        this.creditService = creditService;
    }
    
    @Override
    public String getAgentType() {
        return "embedding-generator";
    }
    
    @Override
    protected AgentResult processWithConfig(AgentTask task) {
        String paperId = task.getInput().get("paperId").asText();
        boolean forceRegenerate = task.getInput().has("forceRegenerate") ? 
            task.getInput().get("forceRegenerate").asBoolean() : false;
        
        try {
            Paper paper = paperRepository.findById(UUID.fromString(paperId))
                .orElseThrow(() -> new EntityNotFoundException("Paper not found: " + paperId));
            
            // Check if embeddings already exist and are current
            if (!forceRegenerate && hasCurrentEmbeddings(paper)) {
                LoggingUtil.info(LOG, "processWithConfig", 
                    "Paper %s already has current embeddings, skipping", paperId);
                return AgentResult.success(task.getId(), "Embeddings already exist");
            }
            
            // Create/update processing status record
            EmbeddingProcessingStatus status = createOrUpdateProcessingStatus(paper.getId());
            
            // Generate embeddings for different content types
            EmbeddingGenerationResult result = generateAllEmbeddings(paper, status);
            
            // Update paper with embeddings
            updatePaperEmbeddings(paper, result);
            
            // Update sections with embeddings
            updateSectionEmbeddings(paper, result);
            
            // Track costs and deduct credits
            trackCostsAndCredits(paper.getUserId(), result, task.getId());
            
            // Mark as completed
            completeProcessingStatus(status, result);
            
            LoggingUtil.info(LOG, "processWithConfig", 
                "Successfully generated embeddings for paper %s", paperId);
            
            return AgentResult.success(task.getId(), result);
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "processWithConfig", 
                "Failed to generate embeddings for paper %s: %s", e, paperId, e.getMessage());
            
            // Update status as failed
            updateProcessingStatusAsFailed(paperId, e.getMessage());
            
            return AgentResult.failure(task.getId(), e.getMessage());
        }
    }
    
    private EmbeddingGenerationResult generateAllEmbeddings(
            Paper paper, 
            EmbeddingProcessingStatus status) {
        
        EmbeddingGenerationResult.Builder resultBuilder = EmbeddingGenerationResult.builder();
        int totalTokensUsed = 0;
        
        // 1. Title embedding
        if (paper.getTitle() != null) {
            EmbeddingResult titleResult = embeddingService.generateEmbedding(paper.getTitle());
            if (titleResult.isSuccess()) {
                resultBuilder.titleEmbedding(titleResult.getEmbedding());
                totalTokensUsed += titleResult.getTokenCount();
                updateProcessingProgress(status, "title", true);
            }
        }
        
        // 2. Abstract embedding
        if (paper.getPaperAbstract() != null) {
            EmbeddingResult abstractResult = embeddingService.generateEmbedding(paper.getPaperAbstract());
            if (abstractResult.isSuccess()) {
                resultBuilder.abstractEmbedding(abstractResult.getEmbedding());
                totalTokensUsed += abstractResult.getTokenCount();
                updateProcessingProgress(status, "abstract", true);
            }
        }
        
        // 3. Full content embedding (with chunking for large documents)
        if (paper.getTextContent() != null) {
            EmbeddingResult contentResult = generateContentEmbedding(paper.getTextContent());
            if (contentResult.isSuccess()) {
                resultBuilder.contentEmbedding(contentResult.getEmbedding());
                totalTokensUsed += contentResult.getTokenCount();
                updateProcessingProgress(status, "content", true);
            }
        }
        
        // 4. Concepts embedding (from JSONB field)
        if (paper.getMainConcepts() != null) {
            String conceptsText = extractTextFromJsonb(paper.getMainConcepts());
            if (!conceptsText.isEmpty()) {
                EmbeddingResult conceptsResult = embeddingService.generateEmbedding(conceptsText);
                if (conceptsResult.isSuccess()) {
                    resultBuilder.conceptsEmbedding(conceptsResult.getEmbedding());
                    totalTokensUsed += conceptsResult.getTokenCount();
                    updateProcessingProgress(status, "concepts", true);
                }
            }
        }
        
        // 5. Methodology embedding (from JSONB field)
        if (paper.getMethodologyDetails() != null) {
            String methodologyText = extractTextFromJsonb(paper.getMethodologyDetails());
            if (!methodologyText.isEmpty()) {
                EmbeddingResult methodologyResult = embeddingService.generateEmbedding(methodologyText);
                if (methodologyResult.isSuccess()) {
                    resultBuilder.methodologyEmbedding(methodologyResult.getEmbedding());
                    totalTokensUsed += methodologyResult.getTokenCount();
                    updateProcessingProgress(status, "methodology", true);
                }
            }
        }
        
        // 6. Key findings embedding (from JSONB field)
        if (paper.getKeyFindings() != null) {
            String findingsText = extractTextFromJsonb(paper.getKeyFindings());
            if (!findingsText.isEmpty()) {
                EmbeddingResult findingsResult = embeddingService.generateEmbedding(findingsText);
                if (findingsResult.isSuccess()) {
                    resultBuilder.findingsEmbedding(findingsResult.getEmbedding());
                    totalTokensUsed += findingsResult.getTokenCount();
                    updateProcessingProgress(status, "findings", true);
                }
            }
        }
        
        // 7. Section-based embeddings
        List<SectionEmbeddingResult> sectionResults = generateSectionEmbeddings(paper);
        resultBuilder.sectionEmbeddings(sectionResults);
        
        totalTokensUsed += sectionResults.stream()
            .mapToInt(SectionEmbeddingResult::getTokenCount)
            .sum();
        
        updateProcessingProgress(status, "sections", true);
        
        return resultBuilder
            .totalTokensUsed(totalTokensUsed)
            .build();
    }
    
    private List<SectionEmbeddingResult> generateSectionEmbeddings(Paper paper) {
        List<PaperSection> sections = paperSectionRepository
            .findByPaperIdOrderByIndex(paper.getId());
        
        List<SectionEmbeddingResult> results = new ArrayList<>();
        
        for (PaperSection section : sections) {
            if (section.getContent() != null && !section.getContent().trim().isEmpty()) {
                EmbeddingResult embeddingResult = embeddingService.generateEmbedding(section.getContent());
                
                if (embeddingResult.isSuccess()) {
                    // Classify section type using title
                    String sectionType = classifySectionType(section.getTitle());
                    
                    SectionEmbeddingResult sectionResult = SectionEmbeddingResult.builder()
                        .sectionId(section.getId())
                        .embedding(embeddingResult.getEmbedding())
                        .sectionType(sectionType)
                        .confidence(calculateSectionTypeConfidence(section.getTitle(), sectionType))
                        .tokenCount(embeddingResult.getTokenCount())
                        .build();
                    
                    results.add(sectionResult);
                }
            }
        }
        
        return results;
    }
    
    private String classifySectionType(String title) {
        String lowerTitle = title.toLowerCase();
        
        if (lowerTitle.contains("abstract")) return "abstract";
        if (lowerTitle.contains("introduction") || lowerTitle.contains("intro")) return "introduction";
        if (lowerTitle.contains("method") || lowerTitle.contains("approach")) return "methodology";
        if (lowerTitle.contains("result") || lowerTitle.contains("finding")) return "results";
        if (lowerTitle.contains("discussion") || lowerTitle.contains("analysis")) return "discussion";
        if (lowerTitle.contains("conclusion") || lowerTitle.contains("summary")) return "conclusion";
        if (lowerTitle.contains("background") || lowerTitle.contains("literature")) return "background";
        if (lowerTitle.contains("reference") || lowerTitle.contains("bibliography")) return "references";
        
        return "other";
    }
    
    private double calculateSectionTypeConfidence(String title, String classifiedType) {
        // Simple confidence calculation based on keyword matching
        String lowerTitle = title.toLowerCase();
        
        switch (classifiedType) {
            case "abstract":
                return lowerTitle.equals("abstract") ? 0.9 : 0.7;
            case "introduction":
                return lowerTitle.equals("introduction") ? 0.9 : 0.8;
            case "methodology":
                return lowerTitle.contains("methodology") ? 0.9 : 0.8;
            case "results":
                return lowerTitle.contains("results") ? 0.9 : 0.8;
            case "discussion":
                return lowerTitle.contains("discussion") ? 0.9 : 0.8;
            case "conclusion":
                return lowerTitle.contains("conclusion") ? 0.9 : 0.8;
            default:
                return 0.5;
        }
    }
    
    @Override
    public Duration estimateProcessingTime(AgentTask task) {
        String paperId = task.getInput().get("paperId").asText();
        
        try {
            Paper paper = paperRepository.findById(UUID.fromString(paperId)).orElse(null);
            if (paper == null) {
                return Duration.ofMinutes(2); // Default estimate
            }
            
            // Estimate based on content size
            int contentLength = (paper.getTextContent() != null ? paper.getTextContent().length() : 0) +
                               (paper.getPaperAbstract() != null ? paper.getPaperAbstract().length() : 0) +
                               (paper.getTitle() != null ? paper.getTitle().length() : 0);
            
            // Base time: 1 minute + 30 seconds per 10KB of content
            long baseSeconds = 60;
            long contentSeconds = (contentLength / 10000) * 30;
            
            return Duration.ofSeconds(Math.min(baseSeconds + contentSeconds, 600)); // Max 10 minutes
            
        } catch (Exception e) {
            return Duration.ofMinutes(5); // Safe default
        }
    }
}
```

This embedding generation system provides comprehensive vector representation for all aspects of academic papers while integrating seamlessly with Answer42's existing multi-agent pipeline infrastructure.
