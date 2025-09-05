package com.samjdtechnologies.answer42.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.samjdtechnologies.answer42.model.db.PaperSection;

/**
 * Repository for PaperSection entity operations.
 */
@Repository
public interface PaperSectionRepository extends JpaRepository<PaperSection, UUID> {

    /**
     * Find all sections for a paper ordered by index.
     *
     * @param paperId the paper ID
     * @return list of paper sections
     */
    List<PaperSection> findByPaperIdOrderByIndexAsc(UUID paperId);

    /**
     * Find section by paper ID and title.
     *
     * @param paperId the paper ID
     * @param title   the section title
     * @return list of matching sections
     */
    List<PaperSection> findByPaperIdAndTitleContainingIgnoreCase(UUID paperId, String title);

    /**
     * Delete all sections for a paper.
     *
     * @param paperId the paper ID
     */
    void deleteByPaperId(UUID paperId);

    /**
     * Count sections for a paper.
     *
     * @param paperId the paper ID
     * @return section count
     */
    long countByPaperId(UUID paperId);

    /**
     * Find sections by title pattern across all papers.
     *
     * @param titlePattern the title pattern
     * @return list of matching sections
     */
    @Query("SELECT ps FROM PaperSection ps WHERE LOWER(ps.title) LIKE LOWER(CONCAT('%', :titlePattern, '%'))")
    List<PaperSection> findByTitleContaining(@Param("titlePattern") String titlePattern);
}
