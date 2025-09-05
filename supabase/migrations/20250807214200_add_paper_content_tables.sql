-- Migration: Add paper content storage tables
-- Created: 2025-08-07

-- Create paper_content table for storing full paper text
CREATE TABLE IF NOT EXISTS answer42.paper_content (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    paper_id UUID NOT NULL REFERENCES answer42.papers(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Create paper_sections table for storing structured paper sections
CREATE TABLE IF NOT EXISTS answer42.paper_sections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    paper_id UUID NOT NULL REFERENCES answer42.papers(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    index INTEGER NOT NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Create tags table for categorizing papers
CREATE TABLE IF NOT EXISTS answer42.tags (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Create paper_tags junction table for many-to-many relationship
CREATE TABLE IF NOT EXISTS answer42.paper_tags (
    paper_id UUID NOT NULL REFERENCES answer42.papers(id) ON DELETE CASCADE,
    tag_id UUID NOT NULL REFERENCES answer42.tags(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (paper_id, tag_id)
);

-- Create paper_relationships table for tracking relationships between papers and discovered papers
CREATE TABLE IF NOT EXISTS answer42.paper_relationships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_paper_id UUID NOT NULL REFERENCES answer42.papers(id) ON DELETE CASCADE,
    discovered_paper_id UUID NOT NULL REFERENCES answer42.discovered_papers(id) ON DELETE CASCADE,
    relationship_type VARCHAR(50) NOT NULL,
    relationship_strength DECIMAL(3,2),
    confidence_score DECIMAL(3,2),
    discovery_source VARCHAR(50) NOT NULL,
    discovery_method VARCHAR(100),
    discovery_context JSONB,
    evidence JSONB,
    reasoning TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_paper_content_paper_id ON answer42.paper_content(paper_id);
CREATE INDEX IF NOT EXISTS idx_paper_sections_paper_id ON answer42.paper_sections(paper_id);
CREATE INDEX IF NOT EXISTS idx_paper_sections_title ON answer42.paper_sections(title);
CREATE INDEX IF NOT EXISTS idx_tags_name ON answer42.tags(name);
CREATE INDEX IF NOT EXISTS idx_paper_tags_paper_id ON answer42.paper_tags(paper_id);
CREATE INDEX IF NOT EXISTS idx_paper_tags_tag_id ON answer42.paper_tags(tag_id);
CREATE INDEX IF NOT EXISTS idx_paper_relationships_source_paper ON answer42.paper_relationships(source_paper_id);
CREATE INDEX IF NOT EXISTS idx_paper_relationships_discovered_paper ON answer42.paper_relationships(discovered_paper_id);
CREATE INDEX IF NOT EXISTS idx_paper_relationships_type ON answer42.paper_relationships(relationship_type);
CREATE INDEX IF NOT EXISTS idx_paper_relationships_source ON answer42.paper_relationships(discovery_source);
CREATE INDEX IF NOT EXISTS idx_paper_relationships_confidence ON answer42.paper_relationships(confidence_score);

-- Add triggers for updated_at timestamps
CREATE OR REPLACE FUNCTION answer42.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_paper_content_updated_at BEFORE UPDATE ON answer42.paper_content FOR EACH ROW EXECUTE FUNCTION answer42.update_updated_at_column();
CREATE TRIGGER update_paper_sections_updated_at BEFORE UPDATE ON answer42.paper_sections FOR EACH ROW EXECUTE FUNCTION answer42.update_updated_at_column();
CREATE TRIGGER update_tags_updated_at BEFORE UPDATE ON answer42.tags FOR EACH ROW EXECUTE FUNCTION answer42.update_updated_at_column();
CREATE TRIGGER update_paper_relationships_updated_at BEFORE UPDATE ON answer42.paper_relationships FOR EACH ROW EXECUTE FUNCTION answer42.update_updated_at_column();

-- Add comments for documentation
COMMENT ON TABLE answer42.paper_content IS 'Stores the full text content of papers';
COMMENT ON TABLE answer42.paper_sections IS 'Stores structured sections of papers (abstract, introduction, methods, etc.)';
COMMENT ON TABLE answer42.tags IS 'Tag definitions for categorizing papers';
COMMENT ON TABLE answer42.paper_tags IS 'Many-to-many relationship between papers and tags';
COMMENT ON TABLE answer42.paper_relationships IS 'Tracks relationships between source papers and discovered related papers';

COMMENT ON COLUMN answer42.paper_content.content IS 'Full text content of the paper';
COMMENT ON COLUMN answer42.paper_sections.title IS 'Section title (e.g., Abstract, Introduction, Methods)';
COMMENT ON COLUMN answer42.paper_sections.content IS 'Content of this specific section';
COMMENT ON COLUMN answer42.paper_sections.index IS 'Order of section within the paper';
COMMENT ON COLUMN answer42.paper_relationships.relationship_type IS 'Type of relationship (citation, similarity, builds_on, etc.)';
COMMENT ON COLUMN answer42.paper_relationships.relationship_strength IS 'Strength of relationship (0.0 to 1.0)';
COMMENT ON COLUMN answer42.paper_relationships.confidence_score IS 'Confidence in the relationship assessment (0.0 to 1.0)';
COMMENT ON COLUMN answer42.paper_relationships.discovery_source IS 'Source that discovered this relationship (crossref, semantic_scholar, perplexity)';
COMMENT ON COLUMN answer42.paper_relationships.discovery_context IS 'Additional context about how the relationship was discovered';
COMMENT ON COLUMN answer42.paper_relationships.evidence IS 'Evidence supporting the relationship (citations, shared keywords, etc.)';
