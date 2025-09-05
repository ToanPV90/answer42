package com.samjdtechnologies.answer42.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.samjdtechnologies.answer42.model.db.PaperTag;

/**
 * Repository for PaperTag entity operations.
 */
@Repository
public interface PaperTagRepository extends JpaRepository<PaperTag, PaperTag.PaperTagId> {

    /**
     * Find all tags for a paper.
     *
     * @param paperId the paper ID
     * @return list of paper tags
     */
    List<PaperTag> findByIdPaperId(UUID paperId);

    /**
     * Find all papers for a tag.
     *
     * @param tagId the tag ID
     * @return list of paper tags
     */
    List<PaperTag> findByIdTagId(UUID tagId);

    /**
     * Delete all tags for a paper.
     *
     * @param paperId the paper ID
     */
    void deleteByIdPaperId(UUID paperId);

    /**
     * Delete all papers for a tag.
     *
     * @param tagId the tag ID
     */
    void deleteByIdTagId(UUID tagId);

    /**
     * Count tags for a paper.
     *
     * @param paperId the paper ID
     * @return tag count
     */
    long countByIdPaperId(UUID paperId);

    /**
     * Check if paper-tag relationship exists.
     *
     * @param paperId the paper ID
     * @param tagId   the tag ID
     * @return true if exists
     */
    boolean existsByIdPaperIdAndIdTagId(UUID paperId, UUID tagId);

    /**
     * Get all tags with their usage count.
     *
     * @return list of tag usage data
     */
    @Query("SELECT pt.tag.name, COUNT(pt) as usage_count " +
           "FROM PaperTag pt " +
           "GROUP BY pt.tag.name " +
           "ORDER BY COUNT(pt) DESC")
    List<Object[]> getTagUsageStats();
}
