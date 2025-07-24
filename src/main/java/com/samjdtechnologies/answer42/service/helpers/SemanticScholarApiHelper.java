package com.samjdtechnologies.answer42.service.helpers;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.samjdtechnologies.answer42.model.db.Paper;
import com.samjdtechnologies.answer42.model.discovery.DiscoveredPaper;
import com.samjdtechnologies.answer42.model.discovery.DiscoveryConfiguration;
import com.samjdtechnologies.answer42.model.enums.DiscoverySource;
import com.samjdtechnologies.answer42.model.enums.RelationshipType;
import com.samjdtechnologies.answer42.model.semanticscholar.SemanticScholarAuthor;
import com.samjdtechnologies.answer42.model.semanticscholar.SemanticScholarPaper;
import com.samjdtechnologies.answer42.model.semanticscholar.SemanticScholarRecommendationsResponse;
import com.samjdtechnologies.answer42.model.semanticscholar.SemanticScholarResponse;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Helper class for Semantic Scholar API interactions.
 * Handles HTTP requests, rate limiting, and data conversion for Semantic Scholar API.
 */
@Component
public class SemanticScholarApiHelper {

    private static final Logger LOG = LoggerFactory.getLogger(SemanticScholarApiHelper.class);
    private static final String SEMANTIC_SCHOLAR_BASE_URL = "https://api.semanticscholar.org/graph/v1";
    private static final String USER_AGENT = "Answer42/1.0 (https://answer42.com; support@answer42.com)";
    private static final int RATE_LIMIT_DELAY_MS = 200;

    private final RestTemplate restTemplate;

    public SemanticScholarApiHelper(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Search papers by title with semantic similarity.
     */
    public List<DiscoveredPaper> searchByTitle(String title, DiscoveryConfiguration config, int maxResults) {
        List<DiscoveredPaper> searchResults = new ArrayList<>();

        try {
            rateLimitDelay();

            String query = java.net.URLEncoder.encode(title, "UTF-8");
            String url = String.format("%s/paper/search?query=%s&limit=%d&fields=paperId,title,authors,venue,year,citationCount,influentialCitationCount,publicationDate,abstract,externalIds,url", 
                SEMANTIC_SCHOLAR_BASE_URL, query, maxResults);

            HttpHeaders headers = createApiHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<SemanticScholarResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, SemanticScholarResponse.class);

            if (response.getBody() != null && response.getBody().getData() != null) {
                for (SemanticScholarPaper paper : response.getBody().getData()) {
                    DiscoveredPaper discoveredPaper = convertToDiscoveredPaper(paper, DiscoverySource.SEMANTIC_SCHOLAR);
                    if (discoveredPaper != null) {
                        discoveredPaper.setRelationshipType(RelationshipType.SEMANTIC_SIMILARITY);
                        searchResults.add(discoveredPaper);
                    }
                }
            }

        } catch (Exception e) {
            LoggingUtil.error(LOG, "searchByTitle", 
                "Failed to search papers by title: %s", e, title);
        }

        return searchResults;
    }

    /**
     * Get paper recommendations based on paper ID.
     */
    public List<DiscoveredPaper> getPaperRecommendations(String paperId, DiscoveryConfiguration config, int maxResults) {
        List<DiscoveredPaper> recommendations = new ArrayList<>();

        try {
            rateLimitDelay();

            String url = String.format("%s/paper/%s/recommendations?limit=%d&fields=paperId,title,authors,venue,year,citationCount,influentialCitationCount,publicationDate,abstract,externalIds,url", 
                SEMANTIC_SCHOLAR_BASE_URL, paperId, maxResults);

            HttpHeaders headers = createApiHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<SemanticScholarRecommendationsResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, SemanticScholarRecommendationsResponse.class);

            if (response.getBody() != null && response.getBody().getRecommendedPapers() != null) {
                for (SemanticScholarPaper paper : response.getBody().getRecommendedPapers()) {
                    DiscoveredPaper discoveredPaper = convertToDiscoveredPaper(paper, DiscoverySource.SEMANTIC_SCHOLAR);
                    if (discoveredPaper != null) {
                        discoveredPaper.setRelationshipType(RelationshipType.SEMANTIC_SIMILARITY);
                        recommendations.add(discoveredPaper);
                    }
                }
            }

        } catch (Exception e) {
            LoggingUtil.error(LOG, "getPaperRecommendations", 
                "Failed to get recommendations for paper: %s", e, paperId);
        }

        return recommendations;
    }

    /**
     * Get citations for a paper (papers that cite this one).
     */
    public List<DiscoveredPaper> getPaperCitations(String paperId, DiscoveryConfiguration config, int maxResults) {
        List<DiscoveredPaper> citations = new ArrayList<>();

        try {
            rateLimitDelay();

            String url = String.format("%s/paper/%s/citations?limit=%d&fields=paperId,title,authors,venue,year,citationCount,influentialCitationCount,publicationDate,abstract,externalIds,url", 
                SEMANTIC_SCHOLAR_BASE_URL, paperId, maxResults);

            HttpHeaders headers = createApiHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<SemanticScholarResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, SemanticScholarResponse.class);

            if (response.getBody() != null && response.getBody().getData() != null) {
                for (SemanticScholarPaper paper : response.getBody().getData()) {
                    DiscoveredPaper discoveredPaper = convertToDiscoveredPaper(paper, DiscoverySource.SEMANTIC_SCHOLAR);
                    if (discoveredPaper != null) {
                        discoveredPaper.setRelationshipType(RelationshipType.CITES);
                        citations.add(discoveredPaper);
                    }
                }
            }

        } catch (Exception e) {
            LoggingUtil.error(LOG, "getPaperCitations", 
                "Failed to get citations for paper: %s", e, paperId);
        }

        return citations;
    }

    /**
     * Get references for a paper (papers this one cites).
     */
    public List<DiscoveredPaper> getPaperReferences(String paperId, DiscoveryConfiguration config, int maxResults) {
        List<DiscoveredPaper> references = new ArrayList<>();

        try {
            rateLimitDelay();

            String url = String.format("%s/paper/%s/references?limit=%d&fields=paperId,title,authors,venue,year,citationCount,influentialCitationCount,publicationDate,abstract,externalIds,url", 
                SEMANTIC_SCHOLAR_BASE_URL, paperId, maxResults);

            HttpHeaders headers = createApiHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<SemanticScholarResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, SemanticScholarResponse.class);

            if (response.getBody() != null && response.getBody().getData() != null) {
                for (SemanticScholarPaper paper : response.getBody().getData()) {
                    DiscoveredPaper discoveredPaper = convertToDiscoveredPaper(paper, DiscoverySource.SEMANTIC_SCHOLAR);
                    if (discoveredPaper != null) {
                        discoveredPaper.setRelationshipType(RelationshipType.CITED_BY);
                        references.add(discoveredPaper);
                    }
                }
            }

        } catch (Exception e) {
            LoggingUtil.error(LOG, "getPaperReferences", 
                "Failed to get references for paper: %s", e, paperId);
        }

        return references;
    }

    /**
     * Find papers by author name.
     */
    public List<DiscoveredPaper> findPapersByAuthor(String authorName, DiscoveryConfiguration config, int maxResults) {
        List<DiscoveredPaper> papers = new ArrayList<>();

        try {
            rateLimitDelay();

            String query = java.net.URLEncoder.encode(String.format("author:%s", authorName), "UTF-8");
            String url = String.format("%s/paper/search?query=%s&limit=%d&fields=paperId,title,authors,venue,year,citationCount,influentialCitationCount,publicationDate,abstract,externalIds,url", 
                SEMANTIC_SCHOLAR_BASE_URL, query, maxResults);

            HttpHeaders headers = createApiHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<SemanticScholarResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, SemanticScholarResponse.class);

            if (response.getBody() != null && response.getBody().getData() != null) {
                for (SemanticScholarPaper paper : response.getBody().getData()) {
                    DiscoveredPaper discoveredPaper = convertToDiscoveredPaper(paper, DiscoverySource.SEMANTIC_SCHOLAR);
                    if (discoveredPaper != null) {
                        discoveredPaper.setRelationshipType(RelationshipType.AUTHOR_NETWORK);
                        papers.add(discoveredPaper);
                    }
                }
            }

        } catch (Exception e) {
            LoggingUtil.error(LOG, "findPapersByAuthor", 
                "Failed to find papers by author: %s", e, authorName);
        }

        return papers;
    }

    /**
     * Get paper details by DOI.
     */
    public DiscoveredPaper getPaperByDoi(String doi, DiscoveryConfiguration config) {
        try {
            rateLimitDelay();

            String url = String.format("%s/paper/DOI:%s?fields=paperId,title,authors,venue,year,citationCount,influentialCitationCount,publicationDate,abstract,externalIds,url", 
                SEMANTIC_SCHOLAR_BASE_URL, java.net.URLEncoder.encode(doi, "UTF-8"));

            HttpHeaders headers = createApiHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<SemanticScholarPaper> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, SemanticScholarPaper.class);

            if (response.getBody() != null) {
                return convertToDiscoveredPaper(response.getBody(), DiscoverySource.SEMANTIC_SCHOLAR);
            }

        } catch (RestClientException e) {
            // Paper not found is common and expected
            LoggingUtil.debug(LOG, "getPaperByDoi", 
                "Paper not found for DOI: %s", doi);
        } catch (Exception e) {
            LoggingUtil.error(LOG, "getPaperByDoi", 
                "Failed to get paper by DOI: %s", e, doi);
        }

        return null;
    }

    /**
     * Convert Semantic Scholar paper to DiscoveredPaper.
     */
    public DiscoveredPaper convertToDiscoveredPaper(SemanticScholarPaper paper, DiscoverySource source) {
        try {
            if (paper.getTitle() == null || paper.getTitle().trim().isEmpty()) {
                return null; // Skip papers without titles
            }

            List<String> authors = extractAuthors(paper);
            String journal = paper.getVenue();
            ZonedDateTime publishedDate = extractPublishedDateTime(paper);
            Integer year = paper.getYear();
            String doi = paper.getExternalIds() != null ? paper.getExternalIds().getDOI() : null;
            Integer citationCount = paper.getCitationCount();
            Double influenceScore = paper.getInfluentialCitationCount() != null ? 
                paper.getInfluentialCitationCount().doubleValue() : 0.0;

            return DiscoveredPaper.builder()
                .id(paper.getPaperId())
                .title(paper.getTitle())
                .authors(authors)
                .journal(journal)
                .venue(journal)
                .publishedDate(publishedDate)
                .year(year)
                .doi(doi)
                .abstractText(paper.getAbstractText())
                .citationCount(citationCount)
                .influenceScore(influenceScore)
                .source(source)
                .relevanceScore(0.5) // Initial score, will be calculated later
                .relationshipType(RelationshipType.SEMANTIC_SIMILARITY)
                .url(paper.getUrl())
                .build();

        } catch (Exception e) {
            LoggingUtil.warn(LOG, "convertToDiscoveredPaper", 
                "Failed to convert Semantic Scholar paper to DiscoveredPaper", e);
            return null;
        }
    }

    /**
     * Calculate relevance scores based on multiple factors.
     */
    public void calculateRelevanceScores(List<DiscoveredPaper> papers, Paper sourcePaper) {
        for (DiscoveredPaper paper : papers) {
            calculateRelevanceScore(paper, sourcePaper);
        }
    }

    /**
     * Create appropriate headers for Semantic Scholar API requests.
     */
    private HttpHeaders createApiHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", USER_AGENT);
        headers.set("Accept", "application/json");
        return headers;
    }

    /**
     * Apply rate limiting delay.
     */
    private void rateLimitDelay() {
        try {
            Thread.sleep(RATE_LIMIT_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LoggingUtil.warn(LOG, "rateLimitDelay", "Rate limit delay interrupted", e);
        }
    }

    /**
     * Extract authors from Semantic Scholar paper.
     */
    private List<String> extractAuthors(SemanticScholarPaper paper) {
        List<String> authors = new ArrayList<>();
        
        if (paper.getAuthors() != null) {
            for (SemanticScholarAuthor author : paper.getAuthors()) {
                if (author.getName() != null) {
                    authors.add(author.getName());
                }
            }
        }
        
        return authors;
    }

    /**
     * Extract published date from Semantic Scholar paper.
     */
    private ZonedDateTime extractPublishedDateTime(SemanticScholarPaper paper) {
        try {
            if (paper.getPublicationDate() != null && !paper.getPublicationDate().trim().isEmpty()) {
                // Parse date in format "YYYY-MM-DD"
                return ZonedDateTime.parse(paper.getPublicationDate() + "T00:00:00");
            }
        } catch (Exception e) {
            LoggingUtil.debug(LOG, "extractPublishedDateTime", "Failed to parse date", e);
        }
        
        return null;
    }

    /**
     * Calculate relevance score for a discovered paper.
     */
    private void calculateRelevanceScore(DiscoveredPaper paper, Paper sourcePaper) {
        double relevanceScore = 0.5; // Base score
        
        try {
            // Influence factor (0-0.4)
            if (paper.getInfluenceScore() != null && paper.getInfluenceScore() > 0) {
                relevanceScore += Math.min(paper.getInfluenceScore() / 50.0, 0.4);
            }
            
            // Citation count factor (0-0.3)
            if (paper.getCitationCount() != null && paper.getCitationCount() > 0) {
                relevanceScore += Math.min(paper.getCitationCount() / 100.0, 0.3);
            }
            
            // Author overlap factor (0-0.3)
            if (sourcePaper.getAuthors() != null && paper.getAuthors() != null) {
                long overlap = sourcePaper.getAuthors().stream()
                    .filter(paper.getAuthors()::contains)
                    .count();
                if (overlap > 0) {
                    relevanceScore += (overlap / (double) sourcePaper.getAuthors().size()) * 0.3;
                }
            }
            
            paper.setRelevanceScore(Math.min(relevanceScore, 1.0));
            
        } catch (Exception e) {
            LoggingUtil.debug(LOG, "calculateRelevanceScore", "Failed to calculate scores", e);
            paper.setRelevanceScore(0.5); // Default score
        }
    }
}
