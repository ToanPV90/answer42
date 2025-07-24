package com.samjdtechnologies.answer42.service.discovery.sources;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.Year;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.samjdtechnologies.answer42.config.ThreadConfig;
import com.samjdtechnologies.answer42.model.db.Paper;
import com.samjdtechnologies.answer42.model.discovery.DiscoveredPaper;
import com.samjdtechnologies.answer42.model.discovery.DiscoveryConfiguration;
import com.samjdtechnologies.answer42.model.enums.DiscoverySource;
import com.samjdtechnologies.answer42.model.enums.RelationshipType;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Perplexity Discovery Service - integrates with Perplexity API for real-time trends and open access discovery.
 * Provides current research trends, academic discussions, and open access paper discovery.
 */
@Service
public class PerplexityDiscoveryService {

    private static final Logger LOG = LoggerFactory.getLogger(PerplexityDiscoveryService.class);
    
    // Enhanced regex patterns for comprehensive data extraction
    private static final Pattern DOI_PATTERN = Pattern.compile("10\\.\\d{4,}[/.][\\w\\-\\._/()\\[\\]]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern ARXIV_PATTERN = Pattern.compile("arXiv:(\\d{4}\\.\\d{4,5})(v\\d+)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern YEAR_PATTERN = Pattern.compile("\\b(19|20)\\d{2}\\b");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern VENUE_PATTERN = Pattern.compile("(?i)(?:published in|in|from|journal:|venue:)\\s*([^,\\n]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PMID_PATTERN = Pattern.compile("PMID:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PMCID_PATTERN = Pattern.compile("PMC\\d+", Pattern.CASE_INSENSITIVE);

    private final ChatClient perplexityChatClient;
    private final ThreadConfig threadConfig;

    public PerplexityDiscoveryService(
            @Qualifier("perplexityChatClient") ChatClient perplexityChatClient,
            ThreadConfig threadConfig) {
        this.perplexityChatClient = perplexityChatClient;
        this.threadConfig = threadConfig;
    }

    /**
     * Discover related papers using Perplexity API for real-time trends and discussions.
     */
    public List<DiscoveredPaper> discoverRelatedPapers(Paper sourcePaper, DiscoveryConfiguration config) {
        LoggingUtil.info(LOG, "discoverRelatedPapers", 
            "Starting Perplexity discovery for paper %s", sourcePaper.getId());

        Instant startTime = Instant.now();
        List<DiscoveredPaper> allDiscovered = new ArrayList<>();

        try {
            // Execute trend discovery
            CompletableFuture<List<DiscoveredPaper>> trendsFuture = 
                discoverTrendsAsync(sourcePaper, config);

            CompletableFuture<List<DiscoveredPaper>> openAccessFuture = 
                discoverOpenAccessAsync(sourcePaper, config);

            // Wait for discovery operations with timeout
            CompletableFuture.allOf(trendsFuture, openAccessFuture)
                .orTimeout(config.getEffectiveTimeoutSeconds(), java.util.concurrent.TimeUnit.SECONDS)
                .join();

            // Collect results
            allDiscovered.addAll(trendsFuture.join());
            allDiscovered.addAll(openAccessFuture.join());

            LoggingUtil.info(LOG, "discoverRelatedPapers", 
                "Completed Perplexity discovery for paper %s: found %d related papers in %dms", 
                sourcePaper.getId(), allDiscovered.size(), 
                Duration.between(startTime, Instant.now()).toMillis());

            return allDiscovered;

        } catch (Exception e) {
            LoggingUtil.error(LOG, "discoverRelatedPapers", 
                "Perplexity discovery failed for paper %s", e, sourcePaper.getId());
            return allDiscovered; // Return partial results
        }
    }

    /**
     * Discover trending papers and current discussions using Perplexity AI.
     */
    private CompletableFuture<List<DiscoveredPaper>> discoverTrendsAsync(
            Paper sourcePaper, DiscoveryConfiguration config) {
        
        return CompletableFuture.supplyAsync(() -> {
            List<DiscoveredPaper> trendingPapers = new ArrayList<>();

            try {
                String trendQuery = buildTrendSearchQuery(sourcePaper);
                String perplexityResponse = queryPerplexityForTrends(trendQuery);
                
                List<DiscoveredPaper> trends = parseTrendResponse(perplexityResponse, sourcePaper);
                trends.forEach(paper -> paper.setRelationshipType(RelationshipType.TRENDING));
                
                trendingPapers.addAll(trends);

                LoggingUtil.debug(LOG, "discoverTrendsAsync", 
                    "Found %d trending papers for %s", trendingPapers.size(), sourcePaper.getId());

            } catch (Exception e) {
                LoggingUtil.error(LOG, "discoverTrendsAsync", 
                    "Trends discovery failed for paper %s", e, sourcePaper.getId());
            }

            return trendingPapers;
        }, threadConfig.taskExecutor());
    }

    /**
     * Discover open access papers related to the source paper.
     */
    private CompletableFuture<List<DiscoveredPaper>> discoverOpenAccessAsync(
            Paper sourcePaper, DiscoveryConfiguration config) {
        
        return CompletableFuture.supplyAsync(() -> {
            List<DiscoveredPaper> openAccessPapers = new ArrayList<>();

            try {
                String openAccessQuery = buildOpenAccessSearchQuery(sourcePaper);
                String perplexityResponse = queryPerplexityForOpenAccess(openAccessQuery);
                
                List<DiscoveredPaper> openAccess = parseOpenAccessResponse(perplexityResponse, sourcePaper);
                openAccess.forEach(paper -> paper.setRelationshipType(RelationshipType.OPEN_ACCESS));
                
                openAccessPapers.addAll(openAccess);

                LoggingUtil.debug(LOG, "discoverOpenAccessAsync", 
                    "Found %d open access papers for %s", openAccessPapers.size(), sourcePaper.getId());

            } catch (Exception e) {
                LoggingUtil.error(LOG, "discoverOpenAccessAsync", 
                    "Open access discovery failed for paper %s", e, sourcePaper.getId());
            }

            return openAccessPapers;
        }, threadConfig.taskExecutor());
    }

    /**
     * Build search query for trending research in the paper's domain.
     */
    private String buildTrendSearchQuery(Paper sourcePaper) {
        StringBuilder query = new StringBuilder();
        query.append("Find recent trending research papers from 2024-2025 related to: ");
        query.append(sourcePaper.getTitle());
        
        if (sourcePaper.getPaperAbstract() != null && !sourcePaper.getPaperAbstract().trim().isEmpty()) {
            String[] abstractWords = sourcePaper.getPaperAbstract().split("\\s+");
            int wordCount = Math.min(abstractWords.length, 50);
            String abstractSnippet = String.join(" ", java.util.Arrays.copyOf(abstractWords, wordCount));
            query.append(" Keywords: ").append(abstractSnippet);
        }
        
        query.append(" Please provide paper titles, authors, publication venues, DOIs, and brief descriptions.");
        
        return query.toString();
    }

    /**
     * Build search query for open access papers.
     */
    private String buildOpenAccessSearchQuery(Paper sourcePaper) {
        StringBuilder query = new StringBuilder();
        query.append("Find open access research papers freely available online related to: ");
        query.append(sourcePaper.getTitle());
        query.append(" Include arXiv, PubMed Central, DOAJ, and institutional repositories.");
        query.append(" Provide paper titles, authors, DOIs, download links, and publication details.");
        
        return query.toString();
    }

    /**
     * Query Perplexity AI for trending research.
     */
    private String queryPerplexityForTrends(String query) {
        try {
            String systemPrompt = "You are a research discovery assistant. Find and list recent trending academic papers with their titles, authors, publication details, DOIs where available, and brief descriptions. Format the response as a structured list with clear paper entries.";
            
            return perplexityChatClient.prompt()
                .system(systemPrompt)
                .user(query)
                .call()
                .content();
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "queryPerplexityForTrends", "Failed to query Perplexity for trends", e);
            return "";
        }
    }

    /**
     * Query Perplexity AI for open access papers.
     */
    private String queryPerplexityForOpenAccess(String query) {
        try {
            String systemPrompt = "You are a research discovery assistant specializing in open access papers. Find freely available academic papers with their titles, authors, DOIs, publication venues, and direct access links. Prioritize papers from arXiv, PubMed Central, DOAJ, and other open repositories.";
            
            return perplexityChatClient.prompt()
                .system(systemPrompt)
                .user(query)
                .call()
                .content();
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "queryPerplexityForOpenAccess", "Failed to query Perplexity for open access", e);
            return "";
        }
    }

    /**
     * Parse Perplexity response for trending papers.
     */
    private List<DiscoveredPaper> parseTrendResponse(String response, Paper sourcePaper) {
        List<DiscoveredPaper> papers = new ArrayList<>();
        
        try {
            String[] lines = response.split("\n");
            
            String currentTitle = null;
            String currentAuthors = null;
            String currentDescription = null;
            String currentUrl = null;
            String currentVenue = null;
            String currentDoi = null;
            Integer currentYear = null;
            
            for (String line : lines) {
                line = line.trim();
                
                if (isNewPaperLine(line)) {
                    // Save previous paper if we have one
                    if (currentTitle != null) {
                        papers.add(createDiscoveredPaper(currentTitle, currentAuthors, currentDescription, 
                                                       RelationshipType.TRENDING, currentUrl, currentVenue, 
                                                       currentDoi, currentYear));
                    }
                    
                    // Start new paper
                    currentTitle = extractTitle(line);
                    currentAuthors = null;
                    currentDescription = null;
                    currentUrl = null;
                    currentVenue = null;
                    currentDoi = null;
                    currentYear = null;
                    
                } else if (line.toLowerCase().contains("author") && currentTitle != null) {
                    currentAuthors = extractAuthors(line);
                } else if (containsUrl(line) && currentTitle != null) {
                    currentUrl = extractUrl(line);
                } else if (containsDoi(line) && currentTitle != null) {
                    currentDoi = extractDoi(line);
                } else if (containsVenue(line) && currentTitle != null) {
                    currentVenue = extractVenue(line);
                } else if (containsYear(line) && currentTitle != null) {
                    currentYear = extractYear(line);
                } else if (line.length() > 20 && currentTitle != null && currentDescription == null) {
                    currentDescription = line;
                }
            }
            
            // Add the last paper
            if (currentTitle != null) {
                papers.add(createDiscoveredPaper(currentTitle, currentAuthors, currentDescription, 
                                               RelationshipType.TRENDING, currentUrl, currentVenue, 
                                               currentDoi, currentYear));
            }
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "parseTrendResponse", "Failed to parse trend response", e);
        }
        
        return papers;
    }

    /**
     * Parse Perplexity response for open access papers.
     */
    private List<DiscoveredPaper> parseOpenAccessResponse(String response, Paper sourcePaper) {
        List<DiscoveredPaper> papers = new ArrayList<>();
        
        try {
            String[] lines = response.split("\n");
            
            String currentTitle = null;
            String currentAuthors = null;
            String currentUrl = null;
            String currentVenue = null;
            String currentDoi = null;
            Integer currentYear = null;
            
            for (String line : lines) {
                line = line.trim();
                
                if (isNewPaperLine(line)) {
                    // Save previous paper if we have one
                    if (currentTitle != null) {
                        DiscoveredPaper paper = createDiscoveredPaper(currentTitle, currentAuthors, null, 
                                                                    RelationshipType.OPEN_ACCESS, currentUrl, 
                                                                    currentVenue, currentDoi, currentYear);
                        papers.add(paper);
                    }
                    
                    // Start new paper
                    currentTitle = extractTitle(line);
                    currentAuthors = null;
                    currentUrl = null;
                    currentVenue = null;
                    currentDoi = null;
                    currentYear = null;
                    
                } else if (line.toLowerCase().contains("author") && currentTitle != null) {
                    currentAuthors = extractAuthors(line);
                } else if (containsUrl(line) && currentTitle != null) {
                    currentUrl = extractUrl(line);
                } else if (containsDoi(line) && currentTitle != null) {
                    currentDoi = extractDoi(line);
                } else if (containsVenue(line) && currentTitle != null) {
                    currentVenue = extractVenue(line);
                } else if (containsYear(line) && currentTitle != null) {
                    currentYear = extractYear(line);
                }
            }
            
            // Add the last paper
            if (currentTitle != null) {
                DiscoveredPaper paper = createDiscoveredPaper(currentTitle, currentAuthors, null, 
                                                            RelationshipType.OPEN_ACCESS, currentUrl, 
                                                            currentVenue, currentDoi, currentYear);
                papers.add(paper);
            }
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "parseOpenAccessResponse", "Failed to parse open access response", e);
        }
        
        return papers;
    }

    /**
     * Enhanced method to create a DiscoveredPaper from parsed information with comprehensive metadata.
     */
    private DiscoveredPaper createDiscoveredPaper(String title, String authors, String description, 
                                                RelationshipType relationshipType, String url, 
                                                String venue, String doi, Integer year) {
        
        // Parse authors into a proper list
        List<String> authorList = parseAuthors(authors);
        
        // Generate a unique ID for the discovered paper
        String paperId = generatePaperId(title, doi);
        
        // Determine publication year (current year if not specified)
        Integer publicationYear = year != null ? year : Year.now().getValue();
        
        // Estimate publication date
        ZonedDateTime publishedDate = estimatePublicationDate(year);
        
        // Calculate relevance score based on relationship type and metadata completeness
        double relevanceScore = calculateRelevanceScore(relationshipType, doi, url, venue, authorList.size());
        
        return DiscoveredPaper.builder()
            .id(paperId)
            .title(cleanTitle(title))
            .authors(authorList)
            .abstractText(cleanDescription(description))
            .venue(cleanVenue(venue))
            .journal(cleanVenue(venue))
            .doi(cleanDoi(doi))
            .url(cleanUrl(url))
            .year(publicationYear)
            .publishedDate(publishedDate)
            .source(DiscoverySource.PERPLEXITY)
            .relationshipType(relationshipType)
            .relevanceScore(relevanceScore)
            .citationCount(0) // Unknown from Perplexity
            .influenceScore(0.0) // Unknown from Perplexity
            .build();
    }

    /**
     * Enhanced URL extraction with comprehensive pattern matching and validation.
     */
    private String extractUrl(String line) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }
        
        // Try different URL extraction approaches
        
        // 1. Direct URL pattern matching
        Matcher urlMatcher = URL_PATTERN.matcher(line);
        if (urlMatcher.find()) {
            String url = urlMatcher.group();
            return cleanUrl(url);
        }
        
        // 2. Look for common academic URL patterns
        String[] patterns = {
            "arxiv\\.org/[\\w/.-]+",
            "doi\\.org/[\\w/.-]+",
            "pubmed\\.ncbi\\.nlm\\.nih\\.gov/\\d+",
            "scholar\\.google\\.com/[\\w/.-]+",
            "researchgate\\.net/[\\w/.-]+",
            "semanticscholar\\.org/[\\w/.-]+",
            "acm\\.org/[\\w/.-]+",
            "ieee\\.org/[\\w/.-]+",
            "springer\\.com/[\\w/.-]+",
            "nature\\.com/[\\w/.-]+",
            "science\\.org/[\\w/.-]+"
        };
        
        for (String pattern : patterns) {
            Pattern p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(line);
            if (m.find()) {
                return "https://" + m.group();
            }
        }
        
        // 3. Extract DOI and convert to URL
        String doi = extractDoi(line);
        if (doi != null) {
            return "https://doi.org/" + doi;
        }
        
        // 4. Extract arXiv ID and convert to URL
        Matcher arxivMatcher = ARXIV_PATTERN.matcher(line);
        if (arxivMatcher.find()) {
            return "https://arxiv.org/abs/" + arxivMatcher.group(1);
        }
        
        // 5. Extract PMID and convert to URL
        Matcher pmidMatcher = PMID_PATTERN.matcher(line);
        if (pmidMatcher.find()) {
            return "https://pubmed.ncbi.nlm.nih.gov/" + pmidMatcher.group(1);
        }
        
        return null;
    }

    // Helper methods for parsing
    
    private boolean isNewPaperLine(String line) {
        return line.toLowerCase().contains("title:") || 
               line.matches("^\\d+\\.\\s.*") || 
               (line.length() > 20 && !line.toLowerCase().contains("author") && 
                !containsUrl(line) && !containsDoi(line));
    }
    
    private boolean containsUrl(String line) {
        return URL_PATTERN.matcher(line).find() || 
               line.contains("arxiv.org") || 
               line.contains("doi.org") || 
               line.contains("pubmed");
    }
    
    private boolean containsDoi(String line) {
        return DOI_PATTERN.matcher(line).find() || 
               line.toLowerCase().contains("doi:");
    }
    
    private boolean containsVenue(String line) {
        return VENUE_PATTERN.matcher(line).find();
    }
    
    private boolean containsYear(String line) {
        return YEAR_PATTERN.matcher(line).find();
    }
    
    private String extractTitle(String line) {
        String title = line.replaceAll("^\\d+\\.\\s*", "")
                          .replaceAll("(?i)title:\\s*", "")
                          .replaceAll("[\"']", "")
                          .trim();
        return title.length() > 5 ? title : null;
    }
    
    private String extractAuthors(String line) {
        String authors = line.replaceAll("(?i)authors?:\\s*", "")
                            .replaceAll("(?i)by\\s*", "")
                            .trim();
        return authors.length() > 2 ? authors : null;
    }
    
    private String extractDoi(String line) {
        Matcher doiMatcher = DOI_PATTERN.matcher(line);
        if (doiMatcher.find()) {
            return doiMatcher.group();
        }
        return null;
    }
    
    private String extractVenue(String line) {
        Matcher venueMatcher = VENUE_PATTERN.matcher(line);
        if (venueMatcher.find()) {
            return venueMatcher.group(1).trim();
        }
        return null;
    }
    
    private Integer extractYear(String line) {
        Matcher yearMatcher = YEAR_PATTERN.matcher(line);
        if (yearMatcher.find()) {
            return Integer.parseInt(yearMatcher.group());
        }
        return null;
    }
    
    private List<String> parseAuthors(String authors) {
        List<String> authorList = new ArrayList<>();
        if (authors != null && !authors.trim().isEmpty()) {
            String[] authorArray = authors.split(",|and|&");
            for (String author : authorArray) {
                String cleanAuthor = author.trim();
                if (cleanAuthor.length() > 1) {
                    authorList.add(cleanAuthor);
                }
            }
        }
        return authorList;
    }
    
    private String generatePaperId(String title, String doi) {
        if (doi != null && !doi.trim().isEmpty()) {
            return "perplexity-" + doi.replaceAll("[^a-zA-Z0-9]", "-");
        }
        if (title != null && !title.trim().isEmpty()) {
            return "perplexity-" + UUID.nameUUIDFromBytes(title.getBytes()).toString();
        }
        return "perplexity-" + UUID.randomUUID().toString();
    }
    
    private ZonedDateTime estimatePublicationDate(Integer year) {
        if (year != null) {
            return ZonedDateTime.of(year, 6, 15, 0, 0, 0, 0, ZoneId.systemDefault()); // Mid-year estimate
        }
        return ZonedDateTime.now();
    }
    
    private double calculateRelevanceScore(RelationshipType type, String doi, String url, 
                                         String venue, int authorCount) {
        double baseScore = type.getImportanceScore();
        
        // Boost for having DOI (indicates peer review)
        if (doi != null && !doi.trim().isEmpty()) {
            baseScore += 0.1;
        }
        
        // Boost for having URL (indicates accessibility)
        if (url != null && !url.trim().isEmpty()) {
            baseScore += 0.05;
        }
        
        // Boost for having venue information
        if (venue != null && !venue.trim().isEmpty()) {
            baseScore += 0.05;
        }
        
        // Boost for multiple authors (indicates collaboration)
        if (authorCount > 1) {
            baseScore += 0.05;
        }
        
        return Math.min(baseScore, 1.0);
    }
    
    private String cleanTitle(String title) {
        if (title == null) return null;
        return title.replaceAll("\\s+", " ").trim();
    }
    
    private String cleanDescription(String description) {
        if (description == null) return null;
        return description.replaceAll("\\s+", " ").trim();
    }
    
    private String cleanVenue(String venue) {
        if (venue == null) return null;
        return venue.replaceAll("\\s+", " ").trim();
    }
    
    private String cleanDoi(String doi) {
        if (doi == null) return null;
        return doi.trim().replaceAll("^(doi:|DOI:)\\s*", "");
    }
    
    private String cleanUrl(String url) {
        if (url == null) return null;
        return url.trim().replaceAll("[,.;)]$", "");
    }
}
