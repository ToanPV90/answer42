package com.samjdtechnologies.answer42.util;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.samjdtechnologies.answer42.model.research.PerplexityResearchResult;
import com.samjdtechnologies.answer42.model.research.ResearchQuery;
import com.samjdtechnologies.answer42.model.research.ResearchResult;
import com.samjdtechnologies.answer42.model.research.ResearchSource;

/**
 * Utility for parsing Perplexity AI research responses into structured data.
 */
public class ResearchResponseParser {

    private static final Logger LOG = LoggerFactory.getLogger(ResearchResponseParser.class);

    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[([0-9]+)\\]");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+");
    private static final Pattern FACT_PATTERN = Pattern.compile("(?i)according to|research shows|studies indicate|data suggests");
    
    // Enhanced patterns for paper extraction
    private static final Pattern PAPER_TITLE_PATTERN = Pattern.compile("(?i)\"([^\"]+)\"\\s*(?:by|authored by|\\(\\d{4}\\))");
    private static final Pattern AUTHOR_PATTERN = Pattern.compile("(?i)(?:by|authored by|authors?)\\s+([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)*(?:\\s+et\\s+al\\.?)?)");
    private static final Pattern YEAR_PATTERN = Pattern.compile("\\((\\d{4})\\)");
    private static final Pattern DOI_PATTERN = Pattern.compile("doi:?\\s*([0-9]+\\.[0-9]+/[^\\s]+)");
    private static final Pattern JOURNAL_PATTERN = Pattern.compile("(?i)published in\\s+([^,.]+)");
    
    // Enhanced patterns for key findings
    private static final Pattern FINDING_INDICATORS = Pattern.compile("(?i)(?:found that|discovered|revealed|showed|demonstrated|concluded|results indicate|findings suggest|evidence shows)");
    private static final Pattern NUMBERED_FINDING = Pattern.compile("^\\s*([0-9]+)[\\.\\)]\\s*(.+)$", Pattern.MULTILINE);
    private static final Pattern BULLETED_FINDING = Pattern.compile("^\\s*[•\\-\\*]\\s*(.+)$", Pattern.MULTILINE);
    private static final Pattern SIGNIFICANCE_INDICATORS = Pattern.compile("(?i)(?:significant|important|crucial|key|major|notable|remarkable)");

    /**
     * Parse research response for fact verification queries.
     */
    public static ResearchResult parseFactVerificationResponse(String response, ResearchQuery query) {
        try {
            List<ResearchSource> sources = extractSources(response);
            String cleanContent = removeCitations(response);
            double confidence = calculateConfidence(response, sources);

            return ResearchResult.builder()
                .queryId(query.getQueryId())
                .queryType(query.getType())
                .content(cleanContent)
                .sources(sources)
                .confidenceScore(confidence)
                .executedAt(Instant.now())
                .build();

        } catch (Exception e) {
            LoggingUtil.error(LOG, "parseFactVerificationResponse", 
                "Failed to parse fact verification response", e);
            return ResearchResult.failed(query.getQueryId(), query.getType(), e.getMessage());
        }
    }

    /**
     * Parse research response for related papers discovery.
     */
    public static ResearchResult parseRelatedPapersResponse(String response, ResearchQuery query) {
        try {
            List<ResearchSource> sources = extractAcademicSources(response);
            String cleanContent = extractPaperSummaries(response);
            double confidence = sources.size() > 0 ? 0.85 : 0.3;

            return ResearchResult.builder()
                .queryId(query.getQueryId())
                .queryType(query.getType())
                .content(cleanContent)
                .sources(sources)
                .confidenceScore(confidence)
                .executedAt(Instant.now())
                .build();

        } catch (Exception e) {
            LoggingUtil.error(LOG, "parseRelatedPapersResponse", 
                "Failed to parse related papers response", e);
            return ResearchResult.failed(query.getQueryId(), query.getType(), e.getMessage());
        }
    }

    /**
     * Parse comprehensive research synthesis from multiple results.
     */
    public static PerplexityResearchResult.ResearchSynthesis parseSynthesis(
            List<ResearchResult> results, String synthesisResponse) {
        
        try {
            String summary = extractSummary(synthesisResponse);
            List<String> keyFindings = extractKeyFindings(synthesisResponse);
            List<String> contradictions = extractContradictions(synthesisResponse);
            List<String> researchGaps = extractResearchGaps(synthesisResponse);
            List<ResearchSource> primarySources = extractPrimarySources(results);
            double overallConfidence = calculateOverallConfidence(results);

            return PerplexityResearchResult.ResearchSynthesis.builder()
                .summary(summary)
                .keyFindings(keyFindings)
                .contradictions(contradictions)
                .researchGaps(researchGaps)
                .primarySources(primarySources)
                .overallConfidence(overallConfidence)
                .build();

        } catch (Exception e) {
            LoggingUtil.error(LOG, "parseSynthesis", 
                "Failed to parse research synthesis", e);
            return PerplexityResearchResult.ResearchSynthesis.builder()
                .summary("Synthesis parsing failed: " + e.getMessage())
                .overallConfidence(0.0)
                .build();
        }
    }

    /**
     * Parse fact verification details from response.
     */
    public static List<PerplexityResearchResult.FactVerification> parseFactVerifications(
            String response, List<String> factsToVerify) {
        
        List<PerplexityResearchResult.FactVerification> verifications = new ArrayList<>();
        
        try {
            for (String fact : factsToVerify) {
                PerplexityResearchResult.FactVerification verification = 
                    parseIndividualFactVerification(response, fact);
                if (verification != null) {
                    verifications.add(verification);
                }
            }
        } catch (Exception e) {
            LoggingUtil.error(LOG, "parseFactVerifications", 
                "Failed to parse fact verifications", e);
        }
        
        return verifications;
    }

    /**
     * Enhanced extraction of paper summaries with structured data.
     */
    private static String extractPaperSummaries(String response) {
        List<String> paperSummaries = new ArrayList<>();
        String[] sections = response.split("(?=\\n\\n|\\d+\\.|•)");
        
        for (String section : sections) {
            if (containsPaperIndicators(section)) {
                PaperSummary summary = extractStructuredPaperInfo(section);
                if (summary.isValid()) {
                    paperSummaries.add(summary.toFormattedString());
                }
            }
        }
        
        if (paperSummaries.isEmpty()) {
            // Fallback: extract any academic-looking content
            return extractAcademicContent(response);
        }
        
        return String.join("\n\n", paperSummaries);
    }

    /**
     * Enhanced extraction of key findings with significance ranking and source-based prioritization.
     */
    private static List<String> extractKeyFindings(String response) {
        List<RankedFinding> findings = new ArrayList<>();
        
        // Extract numbered findings
        Matcher numberedMatcher = NUMBERED_FINDING.matcher(response);
        while (numberedMatcher.find()) {
            String finding = numberedMatcher.group(2).trim();
            int significance = calculateFindingSignificance(finding);
            findings.add(new RankedFinding(finding, significance, "numbered"));
        }
        
        // Extract bulleted findings
        Matcher bulletMatcher = BULLETED_FINDING.matcher(response);
        while (bulletMatcher.find()) {
            String finding = bulletMatcher.group(1).trim();
            int significance = calculateFindingSignificance(finding);
            findings.add(new RankedFinding(finding, significance, "bulleted"));
        }
        
        // Extract findings indicated by specific phrases
        findings.addAll(extractFindingsByIndicators(response));
        
        // Sort by significance with source-based tie-breaking and return top findings
        return findings.stream()
            .sorted((a, b) -> {
                // Primary sort: by significance (higher is better)
                int sigCompare = Integer.compare(b.significance, a.significance);
                if (sigCompare != 0) {
                    return sigCompare;
                }
                
                // Secondary sort: by source quality (numbered > indicator > bulleted)
                int sourceRankA = getSourceRank(a.source);
                int sourceRankB = getSourceRank(b.source);
                return Integer.compare(sourceRankB, sourceRankA);
            })
            .limit(10)
            .map(f -> {
                // Log the source for debugging when appropriate
                LoggingUtil.debug(LOG, "extractKeyFindings", 
                    "Selected finding from %s source with significance %d: %s", 
                    f.source, f.significance, f.text.substring(0, Math.min(50, f.text.length())));
                return f.text;
            })
            .toList();
    }

    /**
     * Get ranking score for different finding sources (higher is better).
     */
    private static int getSourceRank(String source) {
        return switch (source) {
            case "numbered" -> 3;    // Highest priority: explicitly numbered findings
            case "indicator" -> 2;   // Medium priority: findings with indicator phrases
            case "bulleted" -> 1;    // Lower priority: bulleted findings
            default -> 0;           // Unknown sources get lowest priority
        };
    }

    private static boolean containsPaperIndicators(String section) {
        return section.toLowerCase().contains("study") ||
               section.toLowerCase().contains("research") ||
               section.toLowerCase().contains("paper") ||
               section.toLowerCase().contains("published") ||
               AUTHOR_PATTERN.matcher(section).find() ||
               YEAR_PATTERN.matcher(section).find() ||
               DOI_PATTERN.matcher(section).find();
    }

    private static PaperSummary extractStructuredPaperInfo(String section) {
        PaperSummary summary = new PaperSummary();
        
        // Extract title
        Matcher titleMatcher = PAPER_TITLE_PATTERN.matcher(section);
        if (titleMatcher.find()) {
            summary.title = titleMatcher.group(1);
        }
        
        // Extract authors
        Matcher authorMatcher = AUTHOR_PATTERN.matcher(section);
        if (authorMatcher.find()) {
            summary.authors = authorMatcher.group(1);
        }
        
        // Extract year
        Matcher yearMatcher = YEAR_PATTERN.matcher(section);
        if (yearMatcher.find()) {
            summary.year = yearMatcher.group(1);
        }
        
        // Extract journal
        Matcher journalMatcher = JOURNAL_PATTERN.matcher(section);
        if (journalMatcher.find()) {
            summary.journal = journalMatcher.group(1);
        }
        
        // Extract DOI
        Matcher doiMatcher = DOI_PATTERN.matcher(section);
        if (doiMatcher.find()) {
            summary.doi = doiMatcher.group(1);
        }
        
        // Extract key findings from this paper
        summary.findings = extractPaperFindings(section);
        
        // Extract abstract/summary if available
        summary.abstractText = extractAbstractFromSection(section);
        
        return summary;
    }

    private static List<String> extractPaperFindings(String section) {
        List<String> findings = new ArrayList<>();
        
        // Look for finding indicators
        Matcher findingMatcher = FINDING_INDICATORS.matcher(section);
        while (findingMatcher.find()) {
            int start = findingMatcher.start();
            int end = Math.min(start + 200, section.length());
            String findingText = section.substring(start, end);
            
            // Clean up the finding text
            findingText = findingText.replaceAll("\\s+", " ").trim();
            if (findingText.length() > 20) {
                findings.add(findingText);
            }
        }
        
        return findings;
    }

    private static String extractAbstractFromSection(String section) {
        // Look for abstract indicators
        String[] abstractIndicators = {"abstract:", "summary:", "in summary", "this study"};
        
        for (String indicator : abstractIndicators) {
            int index = section.toLowerCase().indexOf(indicator);
            if (index != -1) {
                int start = index + indicator.length();
                int end = Math.min(start + 300, section.length());
                return section.substring(start, end).trim();
            }
        }
        
        // Fallback: return first sentence if nothing else found
        String[] sentences = section.split("\\.");
        return sentences.length > 0 ? sentences[0] : "";
    }

    private static String extractAcademicContent(String response) {
        // Fallback method to extract any academic-looking content
        StringBuilder academicContent = new StringBuilder();
        String[] paragraphs = response.split("\n\n");
        
        for (String paragraph : paragraphs) {
            if (isAcademicParagraph(paragraph)) {
                academicContent.append(paragraph).append("\n\n");
            }
        }
        
        return academicContent.toString().trim();
    }

    private static boolean isAcademicParagraph(String paragraph) {
        String lower = paragraph.toLowerCase();
        return lower.contains("research") || lower.contains("study") || 
               lower.contains("analysis") || lower.contains("findings") ||
               lower.contains("results") || lower.contains("methodology") ||
               YEAR_PATTERN.matcher(paragraph).find();
    }

    private static int calculateFindingSignificance(String finding) {
        int significance = 0;
        String lower = finding.toLowerCase();
        
        // Check for significance indicators
        if (SIGNIFICANCE_INDICATORS.matcher(lower).find()) {
            significance += 3;
        }
        
        // Check for statistical indicators
        if (lower.contains("statistically") || lower.contains("p <") || 
            lower.contains("confidence interval")) {
            significance += 2;
        }
        
        // Check for outcome indicators
        if (lower.contains("outcome") || lower.contains("result") || 
            lower.contains("effect")) {
            significance += 2;
        }
        
        // Check for quantitative data
        if (lower.matches(".*\\d+.*%.*") || lower.contains("fold") ||
            lower.contains("times")) {
            significance += 1;
        }
        
        // Length factor (longer findings often more substantial)
        if (finding.length() > 100) {
            significance += 1;
        }
        
        return significance;
    }

    private static List<RankedFinding> extractFindingsByIndicators(String response) {
        List<RankedFinding> findings = new ArrayList<>();
        
        Matcher findingMatcher = FINDING_INDICATORS.matcher(response);
        while (findingMatcher.find()) {
            int start = findingMatcher.start();
            int end = findSentenceEnd(response, start);
            
            if (end > start) {
                String finding = response.substring(start, end).trim();
                int significance = calculateFindingSignificance(finding);
                findings.add(new RankedFinding(finding, significance, "indicator"));
            }
        }
        
        return findings;
    }

    private static int findSentenceEnd(String text, int start) {
        int nextPeriod = text.indexOf('.', start);
        int nextNewline = text.indexOf('\n', start);
        
        if (nextPeriod == -1 && nextNewline == -1) {
            return Math.min(start + 300, text.length());
        } else if (nextPeriod == -1) {
            return nextNewline;
        } else if (nextNewline == -1) {
            return nextPeriod + 1;
        } else {
            return Math.min(nextPeriod + 1, nextNewline);
        }
    }

    // Supporting classes
    private static class PaperSummary {
        String title;
        String authors;
        String year;
        String journal;
        String doi;
        String abstractText;
        List<String> findings = new ArrayList<>();
        
        boolean isValid() {
            return title != null || authors != null || (findings != null && !findings.isEmpty());
        }
        
        String toFormattedString() {
            StringBuilder sb = new StringBuilder();
            
            if (title != null) {
                sb.append("Title: ").append(title).append("\n");
            }
            if (authors != null) {
                sb.append("Authors: ").append(authors).append("\n");
            }
            if (year != null) {
                sb.append("Year: ").append(year).append("\n");
            }
            if (journal != null) {
                sb.append("Journal: ").append(journal).append("\n");
            }
            if (doi != null) {
                sb.append("DOI: ").append(doi).append("\n");
            }
            if (abstractText != null) {
                sb.append("Summary: ").append(abstractText).append("\n");
            }
            if (!findings.isEmpty()) {
                sb.append("Key Findings:\n");
                for (String finding : findings) {
                    sb.append("• ").append(finding).append("\n");
                }
            }
            
            return sb.toString();
        }
    }

    private static class RankedFinding {
        final String text;
        final int significance;
        final String source;
        
        RankedFinding(String text, int significance, String source) {
            this.text = text;
            this.significance = significance;
            this.source = source;
        }
    }

    // ... (rest of the existing helper methods remain the same)
    private static List<ResearchSource> extractSources(String response) {
        List<ResearchSource> sources = new ArrayList<>();
        List<String> urls = extractUrls(response);
        
        for (int i = 0; i < urls.size() && i < 10; i++) {
            String url = urls.get(i);
            String title = extractTitleFromContext(response, url);
            String snippet = extractSnippetFromContext(response, url);
            String domain = extractDomain(url);
            
            ResearchSource source = ResearchSource.builder()
                .title(title)
                .url(url)
                .domain(domain)
                .snippet(snippet)
                .sourceType(determineSourceType(url, domain))
                .credibilityScore(calculateCredibilityScore(url, domain))
                .accessedDate(Instant.now())
                .build();
            
            sources.add(source);
        }
        
        return sources;
    }

    private static List<ResearchSource> extractAcademicSources(String response) {
        List<ResearchSource> sources = extractSources(response);
        return sources.stream()
            .filter(ResearchSource::isAcademicSource)
            .limit(15)
            .toList();
    }

    private static List<String> extractUrls(String response) {
        List<String> urls = new ArrayList<>();
        Matcher matcher = URL_PATTERN.matcher(response);
        
        while (matcher.find()) {
            urls.add(matcher.group());
        }
        
        return urls;
    }

    private static String extractTitleFromContext(String response, String url) {
        int urlIndex = response.indexOf(url);
        if (urlIndex > 50) {
            String preceding = response.substring(Math.max(0, urlIndex - 100), urlIndex);
            String[] sentences = preceding.split("\\.");
            if (sentences.length > 0) {
                return sentences[sentences.length - 1].trim();
            }
        }
        return "Source " + (extractDomain(url));
    }

    private static String extractSnippetFromContext(String response, String url) {
        int urlIndex = response.indexOf(url);
        if (urlIndex >= 0 && urlIndex + url.length() < response.length()) {
            int start = Math.max(0, urlIndex - 150);
            int end = Math.min(response.length(), urlIndex + url.length() + 150);
            return response.substring(start, end).trim();
        }
        return "";
    }

    private static String extractDomain(String url) {
        try {
            return url.replaceAll("https?://", "").split("/")[0];
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static ResearchSource.SourceType determineSourceType(String url, String domain) {
        if (domain.contains("arxiv.org") || domain.contains("pubmed") || 
            domain.contains("scholar.google") || domain.contains("doi.org")) {
            return ResearchSource.SourceType.ACADEMIC_PAPER;
        } else if (domain.contains("gov")) {
            return ResearchSource.SourceType.GOVERNMENT_REPORT;
        } else if (domain.contains("wikipedia")) {
            return ResearchSource.SourceType.WIKIPEDIA;
        } else if (domain.contains("reuters") || domain.contains("bbc") || 
                   domain.contains("cnn") || domain.contains("nytimes")) {
            return ResearchSource.SourceType.NEWS_ARTICLE;
        }
        return ResearchSource.SourceType.WEBSITE;
    }

    private static double calculateCredibilityScore(String url, String domain) {
        if (domain.contains("arxiv.org") || domain.contains("pubmed")) return 0.95;
        if (domain.contains("gov")) return 0.9;
        if (domain.contains("edu")) return 0.85;
        if (domain.contains("wikipedia")) return 0.7;
        if (domain.contains("reuters") || domain.contains("bbc")) return 0.8;
        return 0.6;
    }

    private static String removeCitations(String response) {
        return CITATION_PATTERN.matcher(response).replaceAll("");
    }

    private static double calculateConfidence(String response, List<ResearchSource> sources) {
        double baseConfidence = 0.5;
        
        Matcher factMatcher = FACT_PATTERN.matcher(response);
        int factIndicators = 0;
        while (factMatcher.find()) {
            factIndicators++;
        }
        baseConfidence += Math.min(factIndicators * 0.1, 0.3);
        
        double avgSourceCredibility = sources.stream()
            .mapToDouble(ResearchSource::getCredibilityScore)
            .average()
            .orElse(0.5);
        baseConfidence += (avgSourceCredibility - 0.5) * 0.4;
        
        return Math.min(baseConfidence, 0.95);
    }

    private static String extractSummary(String response) {
        String[] paragraphs = response.split("\n\n");
        return paragraphs.length > 0 ? paragraphs[0] : response.substring(0, Math.min(300, response.length()));
    }

    private static List<String> extractContradictions(String response) {
        List<String> contradictions = new ArrayList<>();
        if (response.toLowerCase().contains("contradiction") || response.toLowerCase().contains("disagree")) {
            contradictions.add("Potential contradictions identified in research");
        }
        return contradictions;
    }

    private static List<String> extractResearchGaps(String response) {
        List<String> gaps = new ArrayList<>();
        if (response.toLowerCase().contains("gap") || response.toLowerCase().contains("more research needed")) {
            gaps.add("Research gaps identified");
        }
        return gaps;
    }

    private static List<ResearchSource> extractPrimarySources(List<ResearchResult> results) {
        return results.stream()
            .flatMap(result -> result.getSources().stream())
            .filter(ResearchSource::isHighCredibility)
            .limit(5)
            .toList();
    }

    private static double calculateOverallConfidence(List<ResearchResult> results) {
        return results.stream()
            .mapToDouble(ResearchResult::getConfidenceScore)
            .average()
            .orElse(0.0);
    }

    private static PerplexityResearchResult.FactVerification parseIndividualFactVerification(
            String response, String fact) {
        
        PerplexityResearchResult.FactVerification.VerificationStatus status;
        double confidence;
        
        if (response.toLowerCase().contains("confirmed") || response.toLowerCase().contains("true")) {
            status = PerplexityResearchResult.FactVerification.VerificationStatus.CONFIRMED;
            confidence = 0.9;
        } else if (response.toLowerCase().contains("false") || response.toLowerCase().contains("incorrect")) {
            status = PerplexityResearchResult.FactVerification.VerificationStatus.FALSE;
            confidence = 0.9;
        } else if (response.toLowerCase().contains("disputed") || response.toLowerCase().contains("controversial")) {
            status = PerplexityResearchResult.FactVerification.VerificationStatus.DISPUTED;
            confidence = 0.7;
        } else {
            status = PerplexityResearchResult.FactVerification.VerificationStatus.UNVERIFIED;
            confidence = 0.3;
        }
        
        return PerplexityResearchResult.FactVerification.builder()
            .fact(fact)
            .status(status)
            .evidence(response.substring(0, Math.min(500, response.length())))
            .confidenceLevel(confidence)
            .build();
    }
}
