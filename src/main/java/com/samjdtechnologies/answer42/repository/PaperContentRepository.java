package com.samjdtechnologies.answer42.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.samjdtechnologies.answer42.model.db.PaperContent;

/**
 * Repository for PaperContent entity operations.
 */
@Repository
public interface PaperContentRepository extends JpaRepository<PaperContent, UUID> {

    /**
     * Find paper content by paper ID.
     *
     * @param paperId the paper ID
     * @return optional paper content
     */
    Optional<PaperContent> findByPaperId(UUID paperId);

    /**
     * Find paper content by paper entity.
     *
     * @param paper the paper entity
     * @return optional paper content
     */
    Optional<PaperContent> findByPaper(com.samjdtechnologies.answer42.model.db.Paper paper);

    /**
     * Check if content exists for a paper.
     *
     * @param paperId the paper ID
     * @return true if content exists
     */
    boolean existsByPaperId(UUID paperId);

    /**
     * Delete content by paper ID.
     *
     * @param paperId the paper ID
     */
    void deleteByPaperId(UUID paperId);

    /**
     * Find all paper content with content size greater than specified limit.
     *
     * @param minSize minimum content size
     * @return list of paper content
     */
    @Query("SELECT pc FROM PaperContent pc WHERE LENGTH(pc.content) > :minSize")
    List<PaperContent> findByContentSizeGreaterThan(@Param("minSize") int minSize);
}
