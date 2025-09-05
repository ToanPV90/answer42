package com.samjdtechnologies.answer42.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.samjdtechnologies.answer42.model.db.Tag;

/**
 * Repository for Tag entity operations.
 */
@Repository
public interface TagRepository extends JpaRepository<Tag, UUID> {

    /**
     * Find tag by name (case-insensitive).
     *
     * @param name the tag name
     * @return optional tag
     */
    Optional<Tag> findByNameIgnoreCase(String name);

    /**
     * Check if tag exists with given name (case-insensitive).
     *
     * @param name the tag name
     * @return true if exists
     */
    boolean existsByNameIgnoreCase(String name);
}
