package com.samjdtechnologies.answer42.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.samjdtechnologies.answer42.model.db.Summary;

/**
 * Repository for Summary entities.
 * Provides data access methods for paper summaries.
 */
@Repository
public interface SummaryRepository extends JpaRepository<Summary, UUID> {

    /**
     * Find summary by paper ID.
     *
     * @param paperId The paper ID
     * @return Optional summary
     */
    Optional<Summary> findByPaperId(UUID paperId);

    /**
     * Check if summary exists for paper.
     *
     * @param paperId The paper ID
     * @return true if summary exists
     */
    boolean existsByPaperId(UUID paperId);

    /**
     * Delete summary by paper ID.
     *
     * @param paperId The paper ID
     */
    void deleteByPaperId(UUID paperId);

    /**
     * Find summary by paper ID with specific summary type populated.
     *
     * @param paperId The paper ID
     * @param summaryType The summary type to check
     * @return Optional summary
     */
    @Query("SELECT s FROM Summary s WHERE s.paperId = :paperId AND " +
           "((:summaryType = 'brief' AND s.brief IS NOT NULL) OR " +
           "(:summaryType = 'standard' AND s.standard IS NOT NULL) OR " +
           "(:summaryType = 'detailed' AND s.detailed IS NOT NULL) OR " +
           "(:summaryType = 'content' AND s.content IS NOT NULL))")
    Optional<Summary> findByPaperIdWithSummaryType(@Param("paperId") UUID paperId, 
                                                   @Param("summaryType") String summaryType);
}
