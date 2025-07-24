package com.samjdtechnologies.answer42.service.discovery.sources;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.samjdtechnologies.answer42.config.ThreadConfig;
import com.samjdtechnologies.answer42.model.db.Paper;
import com.samjdtechnologies.answer42.model.discovery.DiscoveredPaper;
import com.samjdtechnologies.answer42.model.discovery.DiscoveryConfiguration;
import com.samjdtechnologies.answer42.model.enums.DiscoverySource;
import com.samjdtechnologies.answer42.model.enums.RelationshipType;
import com.samjdtechnologies.answer42.util.LoggingUtil;

/**
 * Crossref Discovery Service - integrates with Crossref API for citation network discovery.
 * Provides comprehensive bibliographic discovery through citation analysis and author networks.
 */
@Service
public class CrossrefDiscoveryService {

    private static final Logger LOG = LoggerFactory.getLogger(CrossrefDiscoveryService.class);
    private static final String CROSSREF_BASE_URL = "https://api.crossref.org";
    private static final int DEFAULT_RESULTS_LIMIT = 20;
    private static final String USER_AGENT = "Answer42/1.0 (https://answer42.com; mailto:support@answer42.com)";

    private final RestTemplate restTemplate;
    private final ThreadConfig threadConfig;

    public CrossrefDiscoveryService(RestTemplate restTemplate, ThreadConfig threadConfig) {
        this.restTemplate = restTemplate;
        this.threadConfig = threadConfig;
    }

    /**
     * Discover related papers using Crossref API with multiple discovery strategies.
     */
    public List<DiscoveredPaper> discoverRelatedPapers(Paper sourcePaper, DiscoveryConfiguration config) {
        LoggingUtil.info(LOG, "discoverRelatedPapers", 
            "Starting Crossref discovery for paper %s", sourcePaper.getId());

        Instant startTime = Instant.now();
        List<DiscoveredPaper> allDiscovered = new ArrayList<>();

        try {
            // Execute multiple discovery strategies in parallel
            CompletableFuture<List<DiscoveredPaper>> citationsFuture = 
                discoverCitationNetworkAsync(sourcePaper, config);
            
            CompletableFuture<List<DiscoveredPaper>> authorsFuture = 
                discoverAuthorNetworkAsync(sourcePaper, config);
            
            CompletableFuture<List<DiscoveredPaper>> venuesFuture = 
                discoverVenueNetworkAsync(sourcePaper, config);

            // Wait for all discovery operations with timeout
            CompletableFuture.allOf(citationsFuture, authorsFuture, venuesFuture)
                .orTimeout(config.getEffectiveTimeoutSeconds(), java.util.concurrent.TimeUnit.SECONDS)
                .join();

            // Collect results
            allDiscovered.addAll(citationsFuture.join());
            allDiscovered.addAll(authorsFuture.join());
            allDiscovered.addAll(venuesFuture.join());

            // Calculate relevance scores
            allDiscovered.forEach(paper -> calculateRelevanceScore(paper, sourcePaper));

            LoggingUtil.info(LOG, "discoverRelatedPapers", 
                "Completed Crossref discovery for paper %s: found %d related papers in %dms", 
                sourcePaper.getId(), allDiscovered.size(), 
                Duration.between(startTime, Instant.now()).toMillis());

            return allDiscovered;

        } catch (Exception e) {
            LoggingUtil.error(LOG, "discoverRelatedPapers", 
                "Crossref discovery failed for paper %s", e, sourcePaper.getId());
            return allDiscovered; // Return partial results
        }
    }

    /**
     * Discover citation networks (forward and backward citations).
     */
    private CompletableFuture<List<DiscoveredPaper>> discoverCitationNetworkAsync(
            Paper sourcePaper, DiscoveryConfiguration config) {
        
        return CompletableFuture.supplyAsync(() -> {
            List<DiscoveredPaper> citations = new ArrayList<>();

            try {
                // Forward citations (papers that cite this work)
                if (sourcePaper.getDoi() != null && !sourcePaper.getDoi().trim().isEmpty()) {
                    List<DiscoveredPaper> forwardCitations = findForwardCitations(sourcePaper.getDoi(), config);
                    citations.addAll(forwardCitations);
                }

                // Backward citations through bibliographic search
                List<DiscoveredPaper> backwardCitations = findBackwardCitations(sourcePaper, config);
                citations.addAll(backwardCitations);

                LoggingUtil.debug(LOG, "discoverCitationNetworkAsync", 
                    "Found %d citation network papers for %s", citations.size(), sourcePaper.getId());

            } catch (Exception e) {
                LoggingUtil.error(LOG, "discoverCitationNetworkAsync", 
                    "Citation network discovery failed for paper %s", e, sourcePaper.getId());
            }

            return citations;
        }, threadConfig.taskExecutor());
    }

    /**
     * Discover author networks (other papers by same authors).
     */
    private CompletableFuture<List<DiscoveredPaper>> discoverAuthorNetworkAsync(
            Paper sourcePaper, DiscoveryConfiguration config) {
        
        return CompletableFuture.supplyAsync(() -> {
            List<DiscoveredPaper> authorPapers = new ArrayList<>();

            try {
                if (sourcePaper.getAuthors() != null && !sourcePaper.getAuthors().isEmpty()) {
                    // Process first few authors to avoid overwhelming results
                    int authorsToProcess = Math.min(sourcePaper.getAuthors().size(), 3);
                    
                    for (int i = 0; i < authorsToProcess; i++) {
                        String author = sourcePaper.getAuthors().get(i);
                        List<DiscoveredPaper> papers = findPapersByAuthor(author, config);
                        
                        // Filter out the source paper itself
                        papers.removeIf(paper -> 
                            sourcePaper.getTitle().equalsIgnoreCase(paper.getTitle()) ||
                            (sourcePaper.getDoi() != null && sourcePaper.getDoi().equals(paper.getDoi())));
                        
                        authorPapers.addAll(papers);
                        
                        // Limit per author to control total results
                        if (authorPapers.size() >= config.getMaxPapersForSource(DiscoverySource.CROSSREF)) {
                            break;
                        }
                    }
                }

                LoggingUtil.debug(LOG, "discoverAuthorNetworkAsync", 
                    "Found %d author network papers for %s", authorPapers.size(), sourcePaper.getId());

            } catch (Exception e) {
                LoggingUtil.error(LOG, "discoverAuthorNetworkAsync", 
                    "Author network discovery failed for paper %s", e, sourcePaper.getId());
            }

            return authorPapers;
        }, threadConfig.taskExecutor());
    }

    /**
     * Discover venue networks (other papers from same journal/conference).
     */
    private CompletableFuture<List<DiscoveredPaper>> discoverVenueNetworkAsync(
            Paper sourcePaper, DiscoveryConfiguration config) {
        
        return CompletableFuture.supplyAsync(() -> {
            List<DiscoveredPaper> venuePapers = new ArrayList<>();

            try {
                if (sourcePaper.getJournal() != null && !sourcePaper.getJournal().trim().isEmpty()) {
                    List<DiscoveredPaper> papers = findPapersByVenue(sourcePaper.getJournal(), config);
                    
                    // Filter out the source paper itself
                    papers.removeIf(paper -> 
                        sourcePaper.getTitle().equalsIgnoreCase(paper.getTitle()) ||
                        (sourcePaper.getDoi() != null && sourcePaper.getDoi().equals(paper.getDoi())));
                    
                    venuePapers.addAll(papers);
                }

                LoggingUtil.debug(LOG, "discoverVenueNetworkAsync", 
                    "Found %d venue network papers for %s", venuePapers.size(), sourcePaper.getId());

            } catch (Exception e) {
                LoggingUtil.error(LOG, "discoverVenueNetworkAsync", 
                    "Venue network discovery failed for paper %s", e, sourcePaper.getId());
            }

            return venuePapers;
        }, threadConfig.taskExecutor());
    }

    /**
     * Find papers that cite the given DOI.
     */
    private List<DiscoveredPaper> findForwardCitations(String doi, DiscoveryConfiguration config) {
        List<DiscoveredPaper> citations = new ArrayList<>();

        try {
            // Add rate limiting delay
            Thread.sleep(100); // Basic rate limiting - 10 requests per second

            String url = String.format("%s/works?query=%s&rows=%d&sort=published&order=desc", 
                CROSSREF_BASE_URL, 
                java.net.URLEncoder.encode("\"" + doi + "\"", "UTF-8"),
                Math.min(config.getMaxPapersForSource(DiscoverySource.CROSSREF) / 2, DEFAULT_RESULTS_LIMIT));

            HttpHeaders headers = createCrossrefHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<CrossrefResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, CrossrefResponse.class);

            if (response.getBody() != null && response.getBody().getMessage() != null) {
                List<CrossrefWork> works = response.getBody().getMessage().getItems();
                
                for (CrossrefWork work : works) {
                    DiscoveredPaper paper = convertToDiscoveredPaper(work, DiscoverySource.CROSSREF);
                    if (paper != null) {
                        citations.add(paper);
                    }
                }
            }

        } catch (Exception e) {
            LoggingUtil.error(LOG, "findForwardCitations", 
                "Failed to find forward citations for DOI %s", e, doi);
        }

        return citations;
    }

    /**
     * Find papers through backward citation analysis (referenced works).
     */
    private List<DiscoveredPaper> findBackwardCitations(Paper sourcePaper, DiscoveryConfiguration config) {
        List<DiscoveredPaper> citations = new ArrayList<>();

        try {
            // Use title-based search as a proxy for finding related/citing works
            if (sourcePaper.getTitle() != null && sourcePaper.getTitle().length() > 10) {
                // Extract key terms from title for more focused search
                String searchTerms = extractKeyTermsFromTitle(sourcePaper.getTitle());
                
                citations.addAll(findPapersByKeywords(searchTerms, config, DiscoverySource.CROSSREF));
            }

        } catch (Exception e) {
            LoggingUtil.error(LOG, "findBackwardCitations", 
                "Failed to find backward citations for paper %s", e, sourcePaper.getId());
        }

        return citations;
    }

    /**
     * Find papers by author name.
     */
    private List<DiscoveredPaper> findPapersByAuthor(String authorName, DiscoveryConfiguration config) {
        List<DiscoveredPaper> papers = new ArrayList<>();

        try {
            Thread.sleep(100); // Rate limiting

            String url = String.format("%s/works?query.author=%s&rows=%d&sort=published&order=desc", 
                CROSSREF_BASE_URL,
                java.net.URLEncoder.encode(authorName, "UTF-8"),
                Math.min(10, config.getMaxPapersForSource(DiscoverySource.CROSSREF) / 4));

            HttpHeaders headers = createCrossrefHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<CrossrefResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, CrossrefResponse.class);

            if (response.getBody() != null && response.getBody().getMessage() != null) {
                List<CrossrefWork> works = response.getBody().getMessage().getItems();
                
                for (CrossrefWork work : works) {
                    DiscoveredPaper paper = convertToDiscoveredPaper(work, DiscoverySource.CROSSREF);
                    if (paper != null) {
                        papers.add(paper);
                    }
                }
            }

        } catch (Exception e) {
            LoggingUtil.error(LOG, "findPapersByAuthor", 
                "Failed to find papers by author %s", e, authorName);
        }

        return papers;
    }

    /**
     * Find papers by venue (journal/conference).
     */
    private List<DiscoveredPaper> findPapersByVenue(String venueName, DiscoveryConfiguration config) {
        List<DiscoveredPaper> papers = new ArrayList<>();

        try {
            Thread.sleep(100); // Rate limiting

            String url = String.format("%s/works?query.container-title=%s&rows=%d&sort=published&order=desc", 
                CROSSREF_BASE_URL,
                java.net.URLEncoder.encode(venueName, "UTF-8"),
                Math.min(15, config.getMaxPapersForSource(DiscoverySource.CROSSREF) / 3));

            HttpHeaders headers = createCrossrefHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<CrossrefResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, CrossrefResponse.class);

            if (response.getBody() != null && response.getBody().getMessage() != null) {
                List<CrossrefWork> works = response.getBody().getMessage().getItems();
                
                for (CrossrefWork work : works) {
                    DiscoveredPaper paper = convertToDiscoveredPaper(work, DiscoverySource.CROSSREF);
                    if (paper != null) {
                        papers.add(paper);
                    }
                }
            }

        } catch (Exception e) {
            LoggingUtil.error(LOG, "findPapersByVenue", 
                "Failed to find papers by venue %s", e, venueName);
        }

        return papers;
    }

    /**
     * Find papers by keywords/search terms.
     */
    private List<DiscoveredPaper> findPapersByKeywords(String keywords, DiscoveryConfiguration config, DiscoverySource source) {
        List<DiscoveredPaper> papers = new ArrayList<>();

        try {
            Thread.sleep(100); // Rate limiting

            String url = String.format("%s/works?query=%s&rows=%d&sort=published&order=desc", 
                CROSSREF_BASE_URL,
                java.net.URLEncoder.encode(keywords, "UTF-8"),
                Math.min(10, config.getMaxPapersForSource(DiscoverySource.CROSSREF) / 4));

            HttpHeaders headers = createCrossrefHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<CrossrefResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, CrossrefResponse.class);

            if (response.getBody() != null && response.getBody().getMessage() != null) {
                List<CrossrefWork> works = response.getBody().getMessage().getItems();
                
                for (CrossrefWork work : works) {
                    DiscoveredPaper paper = convertToDiscoveredPaper(work, source);
                    if (paper != null) {
                        papers.add(paper);
                    }
                }
            }

        } catch (Exception e) {
            LoggingUtil.error(LOG, "findPapersByKeywords", 
                "Failed to find papers by keywords %s", e, keywords);
        }

        return papers;
    }

    /**
     * Convert Crossref work to DiscoveredPaper.
     */
    private DiscoveredPaper convertToDiscoveredPaper(CrossrefWork work, DiscoverySource source) {
        try {
            if (work.getTitle() == null || work.getTitle().isEmpty()) {
                return null; // Skip papers without titles
            }

            String title = work.getTitle().get(0);
            List<String> authors = extractAuthors(work);
            String journal = extractJournal(work);
            ZonedDateTime publishedDate = extractPublishedDateTime(work);
            Integer year = extractYear(work);
            String doi = work.getDOI();
            Integer citationCount = work.getReferencedByCount();

            return DiscoveredPaper.builder()
                .title(title)
                .authors(authors)
                .journal(journal)
                .venue(journal) // Same as journal for Crossref
                .publishedDate(publishedDate)
                .year(year)
                .doi(doi)
                .citationCount(citationCount)
                .source(source)
                .relevanceScore(0.5) // Initial score, will be calculated later
                .influenceScore(0.0) // Default influence score
                .relationshipType(RelationshipType.CITED_BY)
                .url(work.getURL())
                .build();

        } catch (Exception e) {
            LoggingUtil.warn(LOG, "convertToDiscoveredPaper", 
                "Failed to convert Crossref work to DiscoveredPaper", e);
            return null;
        }
    }

    /**
     * Extract authors from Crossref work.
     */
    private List<String> extractAuthors(CrossrefWork work) {
        List<String> authors = new ArrayList<>();
        
        if (work.getAuthor() != null) {
            for (CrossrefAuthor author : work.getAuthor()) {
                if (author.getGiven() != null && author.getFamily() != null) {
                    authors.add(author.getGiven() + " " + author.getFamily());
                } else if (author.getFamily() != null) {
                    authors.add(author.getFamily());
                }
            }
        }
        
        return authors;
    }

    /**
     * Extract journal/container title from Crossref work.
     */
    private String extractJournal(CrossrefWork work) {
        if (work.getContainerTitle() != null && !work.getContainerTitle().isEmpty()) {
            return work.getContainerTitle().get(0);
        }
        return null;
    }

    /**
     * Extract published date as ZonedDateTime from Crossref work.
     */
    private ZonedDateTime extractPublishedDateTime(CrossrefWork work) {
        try {
            if (work.getPublished() != null && work.getPublished().getDateParts() != null &&
                !work.getPublished().getDateParts().isEmpty() &&
                !work.getPublished().getDateParts().get(0).isEmpty()) {
                
                List<Integer> dateParts = work.getPublished().getDateParts().get(0);
                int year = dateParts.get(0);
                int month = dateParts.size() > 1 ? dateParts.get(1) : 1;
                int day = dateParts.size() > 2 ? dateParts.get(2) : 1;
                
                return ZonedDateTime.of(year, month, day, 0, 0, 0, 0, ZoneId.systemDefault()); 
                
            }
        } catch (Exception e) {
            LoggingUtil.debug(LOG, "extractPublishedDateTime", "Failed to parse date", e);
        }
        
        return null;
    }

    /**
     * Extract year from Crossref work.
     */
    private Integer extractYear(CrossrefWork work) {
        try {
            if (work.getPublished() != null && work.getPublished().getDateParts() != null &&
                !work.getPublished().getDateParts().isEmpty() &&
                !work.getPublished().getDateParts().get(0).isEmpty()) {
                
                List<Integer> dateParts = work.getPublished().getDateParts().get(0);
                return dateParts.get(0);
            }
        } catch (Exception e) {
            LoggingUtil.debug(LOG, "extractYear", "Failed to parse year", e);
        }
        
        return null;
    }

    /**
     * Extract key terms from paper title for search.
     */
    private String extractKeyTermsFromTitle(String title) {
        // Simple keyword extraction - remove common words and take significant terms
        String[] commonWords = {"the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with", "by"};
        
        String[] words = title.toLowerCase().split("\\s+");
        List<String> keyWords = new ArrayList<>();
        
        for (String word : words) {
            word = word.replaceAll("[^a-zA-Z0-9]", "");
            if (word.length() > 3 && !java.util.Arrays.asList(commonWords).contains(word)) {
                keyWords.add(word);
            }
        }
        
        // Return first 5 key terms
        return String.join(" ", keyWords.subList(0, Math.min(5, keyWords.size())));
    }

    /**
     * Calculate relevance score for discovered paper.
     */
    private void calculateRelevanceScore(DiscoveredPaper paper, Paper sourcePaper) {
        double score = 0.5; // Base score
        
        try {
            // Citation count factor (0-0.3)
            if (paper.getCitationCount() != null && paper.getCitationCount() > 0) {
                score += Math.min(paper.getCitationCount() / 100.0, 0.3);
            }
            
            // Recency factor (0-0.2)
            if (paper.getPublishedDate() != null) {
                long yearsOld = java.time.temporal.ChronoUnit.YEARS.between(
                    paper.getPublishedDate().toLocalDate(), LocalDate.now());
                if (yearsOld <= 5) {
                    score += (5 - yearsOld) / 25.0; // More recent = higher score
                }
            }
            
            // Author overlap factor (0-0.2)
            if (sourcePaper.getAuthors() != null && paper.getAuthors() != null) {
                long overlap = sourcePaper.getAuthors().stream()
                    .filter(paper.getAuthors()::contains)
                    .count();
                if (overlap > 0) {
                    score += (overlap / (double) sourcePaper.getAuthors().size()) * 0.2;
                }
            }
            
            // Venue match factor (0-0.1)
            if (sourcePaper.getJournal() != null && paper.getJournal() != null &&
                sourcePaper.getJournal().equalsIgnoreCase(paper.getJournal())) {
                score += 0.1;
            }
            
            paper.setRelevanceScore(Math.min(score, 1.0));
            
        } catch (Exception e) {
            LoggingUtil.debug(LOG, "calculateRelevanceScore", "Failed to calculate relevance score", e);
            paper.setRelevanceScore(0.5); // Default score
        }
    }

    /**
     * Create appropriate headers for Crossref API requests.
     */
    private HttpHeaders createCrossrefHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", USER_AGENT);
        headers.set("Accept", "application/json");
        return headers;
    }

    // Data classes for Crossref API response parsing
    public static class CrossrefResponse {
        private String status;
        private CrossrefMessage message;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public CrossrefMessage getMessage() { return message; }
        public void setMessage(CrossrefMessage message) { this.message = message; }
    }

    public static class CrossrefMessage {
        private List<CrossrefWork> items;

        public List<CrossrefWork> getItems() { return items; }
        public void setItems(List<CrossrefWork> items) { this.items = items; }
    }

    public static class CrossrefWork {
        private String DOI;
        private List<String> title;
        private List<CrossrefAuthor> author;
        private List<String> containerTitle;
        private String publisher;
        private CrossrefDate published;
        private String URL;
        private Integer referencedByCount;

        // Getters and setters
        public String getDOI() { return DOI; }
        public void setDOI(String DOI) { this.DOI = DOI; }
        public List<String> getTitle() { return title; }
        public void setTitle(List<String> title) { this.title = title; }
        public List<CrossrefAuthor> getAuthor() { return author; }
        public void setAuthor(List<CrossrefAuthor> author) { this.author = author; }
        public List<String> getContainerTitle() { return containerTitle; }
        public void setContainerTitle(List<String> containerTitle) { this.containerTitle = containerTitle; }
        public String getPublisher() { return publisher; }
        public void setPublisher(String publisher) { this.publisher = publisher; }
        public CrossrefDate getPublished() { return published; }
        public void setPublished(CrossrefDate published) { this.published = published; }
        public String getURL() { return URL; }
        public void setURL(String URL) { this.URL = URL; }
        public Integer getReferencedByCount() { return referencedByCount; }
        public void setReferencedByCount(Integer referencedByCount) { this.referencedByCount = referencedByCount; }
    }

    public static class CrossrefAuthor {
        private String given;
        private String family;

        public String getGiven() { return given; }
        public void setGiven(String given) { this.given = given; }
        public String getFamily() { return family; }
        public void setFamily(String family) { this.family = family; }
    }

    public static class CrossrefDate {
        private List<List<Integer>> dateParts;

        public List<List<Integer>> getDateParts() { return dateParts; }
        public void setDateParts(List<List<Integer>> dateParts) { this.dateParts = dateParts; }
    }
}
