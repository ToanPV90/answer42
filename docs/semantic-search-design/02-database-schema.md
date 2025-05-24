# 2. Database Schema

## pgvector Extension Setup

### Installation and Configuration

**Enable pgvector Extension**
```sql
-- Enable pgvector extension in answer42 schema
CREATE EXTENSION IF NOT EXISTS vector;

-- Verify installation
SELECT * FROM pg_extension WHERE extname = 'vector';
```

**Configuration Settings**
```sql
-- Optimize for vector operations
SET maintenance_work_mem = '2GB';
SET max_parallel_maintenance_workers = 4;
SET effective_cache_size = '4GB';
```

## Papers Table Enhancements

### Vector Column Additions

```sql
-- Add embedding columns to existing papers table
ALTER TABLE answer42.papers 
ADD COLUMN content_embedding vector(1536),
ADD COLUMN abstract_embedding vector(1536),
ADD COLUMN title_embedding vector(1536),
ADD COLUMN concepts_embedding vector(1536),
ADD COLUMN methodology_embedding vector(1536),
ADD COLUMN findings_embedding vector(1536);

-- Add metadata columns for embedding management
ALTER TABLE answer42.papers
ADD COLUMN embeddings_generated_at TIMESTAMP WITH TIME ZONE,
ADD COLUMN embeddings_version INTEGER DEFAULT 1,
ADD COLUMN embedding_model VARCHAR(50) DEFAULT 'text-embedding-ada-002';
```

### Vector Indexes for Optimal Performance

```sql
-- Create IVFFlat indexes for similarity search
-- Lists parameter should be roughly sqrt(number_of_rows)

-- Content embedding index (most comprehensive)
CREATE INDEX papers_content_embedding_idx 
    ON answer42.papers USING ivfflat (content_embedding vector_cosine_ops) 
    WITH (lists = 100);

-- Abstract embedding index (primary search)
CREATE INDEX papers_abstract_embedding_idx 
    ON answer42.papers USING ivfflat (abstract_embedding vector_cosine_ops) 
    WITH (lists = 100);

-- Title embedding index (quick filtering)
CREATE INDEX papers_title_embedding_idx 
    ON answer42.papers USING ivfflat (title_embedding vector_cosine_ops) 
    WITH (lists = 100);

-- Concept embedding index (domain-specific)
CREATE INDEX papers_concepts_embedding_idx 
    ON answer42.papers USING ivfflat (concepts_embedding vector_cosine_ops) 
    WITH (lists = 100);

-- Methodology embedding index (approach-based)
CREATE INDEX papers_methodology_embedding_idx 
    ON answer42.papers USING ivfflat (methodology_embedding vector_cosine_ops) 
    WITH (lists = 100);

-- Findings embedding index (outcome-based)
CREATE INDEX papers_findings_embedding_idx 
    ON answer42.papers USING ivfflat (findings_embedding vector_cosine_ops) 
    WITH (lists = 100);
```

### Supporting Indexes

```sql
-- Composite indexes for filtered similarity search
CREATE INDEX papers_embedding_status_idx 
    ON answer42.papers (embeddings_generated_at, embedding_model) 
    WHERE content_embedding IS NOT NULL;

-- User access control with embeddings
CREATE INDEX papers_user_embeddings_idx 
    ON answer42.papers (user_id, embeddings_generated_at) 
    WHERE content_embedding IS NOT NULL;

-- Public papers with embeddings
CREATE INDEX papers_public_embeddings_idx 
    ON answer42.papers (is_public, embeddings_generated_at) 
    WHERE is_public = true AND content_embedding IS NOT NULL;
```

## Paper Sections Table Enhancements

### Section-Level Vector Storage

```sql
-- Add embedding and classification columns to paper_sections
ALTER TABLE answer42.paper_sections 
ADD COLUMN content_embedding vector(1536),
ADD COLUMN section_type VARCHAR(50),
ADD COLUMN section_confidence DOUBLE PRECISION DEFAULT 0.0,
ADD COLUMN embeddings_generated_at TIMESTAMP WITH TIME ZONE,
ADD COLUMN embedding_model VARCHAR(50) DEFAULT 'text-embedding-ada-002';

-- Create index for section-based similarity search
CREATE INDEX paper_sections_embedding_idx 
    ON answer42.paper_sections USING ivfflat (content_embedding vector_cosine_ops) 
    WITH (lists = 50);

-- Index for section type filtering
CREATE INDEX paper_sections_type_idx 
    ON answer42.paper_sections (section_type);

-- Composite index for paper + section search
CREATE INDEX paper_sections_paper_type_idx 
    ON answer42.paper_sections (paper_id, section_type) 
    WHERE content_embedding IS NOT NULL;
```

### Section Type Enumeration

```sql
-- Create section type check constraint
ALTER TABLE answer42.paper_sections
ADD CONSTRAINT paper_sections_type_check 
CHECK (section_type IN (
    'abstract',
    'introduction', 
    'background',
    'literature_review',
    'methodology',
    'methods',
    'experimental_setup',
    'results',
    'findings',
    'analysis',
    'discussion',
    'conclusion',
    'future_work',
    'references',
    'appendix',
    'acknowledgments',
    'other'
));
```

## New Embedding Processing Tables

### Embedding Processing Status Table

```sql
-- Track embedding processing status and history
CREATE TABLE answer42.embedding_processing_status (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    paper_id UUID NOT NULL REFERENCES answer42.papers(id) ON DELETE CASCADE,
    processing_status VARCHAR(50) NOT NULL DEFAULT 'pending',
    embeddings_generated JSONB DEFAULT '{}',
    total_tokens_used INTEGER DEFAULT 0,
    processing_cost_credits INTEGER DEFAULT 0,
    error_message TEXT,
    error_count INTEGER DEFAULT 0,
    retry_after TIMESTAMP WITH TIME ZONE,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    CONSTRAINT embedding_status_check 
    CHECK (processing_status IN ('pending', 'processing', 'completed', 'failed', 'skipped'))
);

-- Indexes for status tracking
CREATE INDEX embedding_status_paper_id_idx 
    ON answer42.embedding_processing_status(paper_id);

CREATE INDEX embedding_status_status_idx 
    ON answer42.embedding_processing_status(processing_status);

CREATE INDEX embedding_status_created_idx 
    ON answer42.embedding_processing_status(created_at);

CREATE INDEX embedding_status_retry_idx 
    ON answer42.embedding_processing_status(retry_after) 
    WHERE processing_status = 'failed' AND retry_after IS NOT NULL;
```

### Embedding Processing Log Table

```sql
-- Detailed processing logs for debugging and analytics
CREATE TABLE answer42.embedding_processing_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    status_id UUID NOT NULL REFERENCES answer42.embedding_processing_status(id) ON DELETE CASCADE,
    embedding_type VARCHAR(50) NOT NULL,
    input_text_length INTEGER,
    tokens_used INTEGER,
    processing_time_ms INTEGER,
    embedding_generated BOOLEAN DEFAULT FALSE,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    CONSTRAINT embedding_type_check 
    CHECK (embedding_type IN (
        'title', 'abstract', 'content', 'concepts', 
        'methodology', 'findings', 'section'
    ))
);

-- Indexes for log analysis
CREATE INDEX embedding_log_status_id_idx 
    ON answer42.embedding_processing_log(status_id);

CREATE INDEX embedding_log_type_idx 
    ON answer42.embedding_processing_log(embedding_type);

CREATE INDEX embedding_log_created_idx 
    ON answer42.embedding_processing_log(created_at);
```

## Search Analytics Tables

### Search Query Tracking

```sql
-- Track semantic search queries for analytics and optimization
CREATE TABLE answer42.semantic_search_queries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES answer42.users(id) ON DELETE SET NULL,
    query_text TEXT NOT NULL,
    search_type VARCHAR(50) NOT NULL,
    similarity_threshold DOUBLE PRECISION,
    max_results INTEGER,
    results_found INTEGER,
    query_embedding vector(1536),
    execution_time_ms INTEGER,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    CONSTRAINT search_type_check 
    CHECK (search_type IN (
        'content', 'abstract', 'title', 'concepts', 
        'methodology', 'findings', 'sections', 'multi_dimensional'
    ))
);

-- Indexes for search analytics
CREATE INDEX search_queries_user_idx 
    ON answer42.semantic_search_queries(user_id);

CREATE INDEX search_queries_type_idx 
    ON answer42.semantic_search_queries(search_type);

CREATE INDEX search_queries_created_idx 
    ON answer42.semantic_search_queries(created_at);

-- Index for finding similar queries
CREATE INDEX search_queries_embedding_idx 
    ON answer42.semantic_search_queries USING ivfflat (query_embedding vector_cosine_ops) 
    WITH (lists = 20);
```

### Search Results Tracking

```sql
-- Track which papers were returned for each search
CREATE TABLE answer42.semantic_search_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    query_id UUID NOT NULL REFERENCES answer42.semantic_search_queries(id) ON DELETE CASCADE,
    paper_id UUID NOT NULL REFERENCES answer42.papers(id) ON DELETE CASCADE,
    similarity_score DOUBLE PRECISION NOT NULL,
    rank_position INTEGER NOT NULL,
    match_type VARCHAR(50) NOT NULL,
    clicked BOOLEAN DEFAULT FALSE,
    clicked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Indexes for result analysis
CREATE INDEX search_results_query_idx 
    ON answer42.semantic_search_results(query_id);

CREATE INDEX search_results_paper_idx 
    ON answer42.semantic_search_results(paper_id);

CREATE INDEX search_results_score_idx 
    ON answer42.semantic_search_results(similarity_score DESC);

CREATE UNIQUE INDEX search_results_query_paper_idx 
    ON answer42.semantic_search_results(query_id, paper_id);
```

## Database Views for Common Queries

### Papers with Embeddings View

```sql
-- View for papers with all embeddings available
CREATE VIEW answer42.papers_with_embeddings AS
SELECT 
    p.*,
    eps.processing_status as embedding_status,
    eps.embeddings_generated,
    eps.total_tokens_used as embedding_tokens_used,
    eps.completed_at as embeddings_completed_at
FROM answer42.papers p
LEFT JOIN answer42.embedding_processing_status eps ON p.id = eps.paper_id
WHERE p.content_embedding IS NOT NULL;
```

### Section Embeddings Summary View

```sql
-- View for section embedding statistics per paper
CREATE VIEW answer42.paper_section_embedding_summary AS
SELECT 
    ps.paper_id,
    COUNT(*) as total_sections,
    COUNT(ps.content_embedding) as sections_with_embeddings,
    ROUND(
        COUNT(ps.content_embedding)::DECIMAL / COUNT(*)::DECIMAL * 100, 
        2
    ) as embedding_coverage_percent,
    ARRAY_AGG(DISTINCT ps.section_type) FILTER (WHERE ps.section_type IS NOT NULL) as section_types,
    MAX(ps.embeddings_generated_at) as last_embedding_generated
FROM answer42.paper_sections ps
GROUP BY ps.paper_id;
```

## Database Functions for Vector Operations

### Similarity Search Function

```sql
-- Function for multi-dimensional similarity search
CREATE OR REPLACE FUNCTION answer42.semantic_similarity_search(
    query_embedding vector(1536),
    search_types text[] DEFAULT ARRAY['abstract'],
    similarity_threshold double precision DEFAULT 0.5,
    max_results integer DEFAULT 20,
    user_filter uuid DEFAULT NULL,
    public_only boolean DEFAULT FALSE
)
RETURNS TABLE (
    paper_id uuid,
    title text,
    similarity_score double precision,
    match_type text
) AS $$
BEGIN
    RETURN QUERY
    WITH similarity_scores AS (
        SELECT 
            p.id as paper_id,
            p.title,
            CASE 
                WHEN 'content' = ANY(search_types) AND p.content_embedding IS NOT NULL THEN
                    1 - (p.content_embedding <=> query_embedding)
                WHEN 'abstract' = ANY(search_types) AND p.abstract_embedding IS NOT NULL THEN
                    1 - (p.abstract_embedding <=> query_embedding)
                WHEN 'title' = ANY(search_types) AND p.title_embedding IS NOT NULL THEN
                    1 - (p.title_embedding <=> query_embedding)
                WHEN 'concepts' = ANY(search_types) AND p.concepts_embedding IS NOT NULL THEN
                    1 - (p.concepts_embedding <=> query_embedding)
                WHEN 'methodology' = ANY(search_types) AND p.methodology_embedding IS NOT NULL THEN
                    1 - (p.methodology_embedding <=> query_embedding)
                WHEN 'findings' = ANY(search_types) AND p.findings_embedding IS NOT NULL THEN
                    1 - (p.findings_embedding <=> query_embedding)
                ELSE 0
            END as similarity_score,
            CASE 
                WHEN 'content' = ANY(search_types) AND p.content_embedding IS NOT NULL THEN 'content'
                WHEN 'abstract' = ANY(search_types) AND p.abstract_embedding IS NOT NULL THEN 'abstract'
                WHEN 'title' = ANY(search_types) AND p.title_embedding IS NOT NULL THEN 'title'
                WHEN 'concepts' = ANY(search_types) AND p.concepts_embedding IS NOT NULL THEN 'concepts'
                WHEN 'methodology' = ANY(search_types) AND p.methodology_embedding IS NOT NULL THEN 'methodology'
                WHEN 'findings' = ANY(search_types) AND p.findings_embedding IS NOT NULL THEN 'findings'
                ELSE 'none'
            END as match_type
        FROM answer42.papers p
        WHERE 
            (user_filter IS NULL OR p.user_id = user_filter)
            AND (NOT public_only OR p.is_public = TRUE)
            AND (
                ('content' = ANY(search_types) AND p.content_embedding IS NOT NULL) OR
                ('abstract' = ANY(search_types) AND p.abstract_embedding IS NOT NULL) OR
                ('title' = ANY(search_types) AND p.title_embedding IS NOT NULL) OR
                ('concepts' = ANY(search_types) AND p.concepts_embedding IS NOT NULL) OR
                ('methodology' = ANY(search_types) AND p.methodology_embedding IS NOT NULL) OR
                ('findings' = ANY(search_types) AND p.findings_embedding IS NOT NULL)
            )
    )
    SELECT 
        ss.paper_id,
        ss.title,
        ss.similarity_score,
        ss.match_type
    FROM similarity_scores ss
    WHERE ss.similarity_score >= similarity_threshold
      AND ss.match_type != 'none'
    ORDER BY ss.similarity_score DESC
    LIMIT max_results;
END;
$$ LANGUAGE plpgsql;
```

## Migration Scripts

### Safe Migration Strategy

```sql
-- Migration script for production deployment
BEGIN;

-- Step 1: Add columns (non-blocking)
ALTER TABLE answer42.papers 
ADD COLUMN IF NOT EXISTS content_embedding vector(1536),
ADD COLUMN IF NOT EXISTS abstract_embedding vector(1536),
ADD COLUMN IF NOT EXISTS title_embedding vector(1536),
ADD COLUMN IF NOT EXISTS concepts_embedding vector(1536),
ADD COLUMN IF NOT EXISTS methodology_embedding vector(1536),
ADD COLUMN IF NOT EXISTS findings_embedding vector(1536),
ADD COLUMN IF NOT EXISTS embeddings_generated_at TIMESTAMP WITH TIME ZONE,
ADD COLUMN IF NOT EXISTS embeddings_version INTEGER DEFAULT 1,
ADD COLUMN IF NOT EXISTS embedding_model VARCHAR(50) DEFAULT 'text-embedding-ada-002';

-- Step 2: Add columns to paper_sections
ALTER TABLE answer42.paper_sections 
ADD COLUMN IF NOT EXISTS content_embedding vector(1536),
ADD COLUMN IF NOT EXISTS section_type VARCHAR(50),
ADD COLUMN IF NOT EXISTS section_confidence DOUBLE PRECISION DEFAULT 0.0,
ADD COLUMN IF NOT EXISTS embeddings_generated_at TIMESTAMP WITH TIME ZONE,
ADD COLUMN IF NOT EXISTS embedding_model VARCHAR(50) DEFAULT 'text-embedding-ada-002';

-- Step 3: Create new tables
CREATE TABLE IF NOT EXISTS answer42.embedding_processing_status (
    -- Table definition as above
);

-- Step 4: Create indexes (may take time, consider doing concurrently)
-- Note: CONCURRENTLY option prevents blocking but requires separate statements
-- CREATE INDEX CONCURRENTLY papers_content_embedding_idx ...

COMMIT;
```

This database schema provides a robust foundation for semantic search while maintaining compatibility with Answer42's existing structure.
