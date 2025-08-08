package com.samjdtechnologies.answer42.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.samjdtechnologies.answer42.model.db.Citation;

/**
 * Repository for Citation entities providing database access methods.
 */
@Repository
public interface CitationRepository extends JpaRepository<Citation, UUID> {
    
    /**
     * Find all citations for a specific paper.
     */
    List<Citation> findByPaperIdOrderByCreatedAt(UUID paperId);
    
    /**
     * Find citations by paper ID with optional limit.
     */
    @Query("SELECT c FROM Citation c WHERE c.paperId = :paperId ORDER BY c.createdAt DESC")
    List<Citation> findByPaperIdLimited(@Param("paperId") UUID paperId, 
                                       @Param("limit") int limit);
    
    /**
     * Count citations for a specific paper.
     */
    long countByPaperId(UUID paperId);
    
    /**
     * Check if citations exist for a paper.
     */
    boolean existsByPaperId(UUID paperId);
    
    /**
     * Find citations that have structured data (not just raw text).
     */
    @Query("SELECT c FROM Citation c WHERE c.paperId = :paperId " +
           "AND JSON_EXTRACT(c.citationData, '$.title') IS NOT NULL " +
           "ORDER BY c.createdAt")
    List<Citation> findStructuredCitationsByPaperId(@Param("paperId") UUID paperId);
    
    /**
     * Find citations by DOI.
     */
    @Query("SELECT c FROM Citation c WHERE JSON_EXTRACT(c.citationData, '$.doi') = :doi")
    List<Citation> findByDoi(@Param("doi") String doi);
    
    /**
     * Find citations by publication year range.
     */
    @Query("SELECT c FROM Citation c WHERE c.paperId = :paperId " +
           "AND CAST(JSON_EXTRACT(c.citationData, '$.year') AS INTEGER) BETWEEN :startYear AND :endYear " +
           "ORDER BY CAST(JSON_EXTRACT(c.citationData, '$.year') AS INTEGER) DESC")
    List<Citation> findByPaperIdAndYearRange(@Param("paperId") UUID paperId, 
                                           @Param("startYear") int startYear, 
                                           @Param("endYear") int endYear);
    
    /**
     * Find citations with confidence above threshold.
     */
    @Query("SELECT c FROM Citation c WHERE c.paperId = :paperId " +
           "AND CAST(JSON_EXTRACT(c.citationData, '$.confidence') AS DOUBLE) >= :threshold " +
           "ORDER BY CAST(JSON_EXTRACT(c.citationData, '$.confidence') AS DOUBLE) DESC")
    List<Citation> findByPaperIdWithHighConfidence(@Param("paperId") UUID paperId, 
                                                  @Param("threshold") double threshold);
    
    /**
     * Search citations by title (case-insensitive).
     */
    @Query("SELECT c FROM Citation c WHERE c.paperId = :paperId " +
           "AND LOWER(JSON_EXTRACT(c.citationData, '$.title')) LIKE LOWER(CONCAT('%', :title, '%'))")
    List<Citation> findByPaperIdAndTitleContaining(@Param("paperId") UUID paperId, 
                                                  @Param("title") String title);
    
    /**
     * Search citations by author name (case-insensitive) using native PostgreSQL query.
     */
    @Query(value = "SELECT * FROM answer42.citations c WHERE c.paper_id = :paperId " +
           "AND LOWER(c.citation_data::text) LIKE LOWER(CONCAT('%', :authorName, '%'))",
           nativeQuery = true)
    List<Citation> findByPaperIdAndAuthorContaining(@Param("paperId") UUID paperId, 
                                                   @Param("authorName") String authorName);
    
    /**
     * Delete all citations for a specific paper.
     */
    void deleteByPaperId(UUID paperId);
    
    /**
     * Get citation statistics for a paper.
     */
    @Query("SELECT " +
           "COUNT(*) AS total, " +
           "COUNT(CASE WHEN JSON_EXTRACT(c.citationData, '$.title') IS NOT NULL THEN 1 END) AS structured, " +
           "AVG(CAST(JSON_EXTRACT(c.citationData, '$.confidence') AS DOUBLE)) AS avgConfidence, " +
           "MIN(CAST(JSON_EXTRACT(c.citationData, '$.year') AS INTEGER)) AS oldestYear, " +
           "MAX(CAST(JSON_EXTRACT(c.citationData, '$.year') AS INTEGER)) AS newestYear " +
           "FROM Citation c WHERE c.paperId = :paperId")
    Optional<Object[]> getCitationStatistics(@Param("paperId") UUID paperId);
    
}
