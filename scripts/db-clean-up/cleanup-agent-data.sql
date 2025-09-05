-- =============================================================================
-- Answer42 Agent Data Cleanup Script
-- =============================================================================
-- 
-- This script cleans up ONLY agent-populated data while preserving:
-- - User-uploaded papers and projects
-- - User accounts and settings
-- - Chat history and user-generated content
--
-- Use this when you want to test agent processing on existing papers
-- without losing the papers themselves.
--
-- Usage:
-- psql -d postgres -f scripts/cleanup-agent-data.sql
-- 
-- Note: Answer42 uses 'postgres' as the default database name with Supabase.
-- If using a different database name, replace 'postgres' with your database name.
--
-- =============================================================================

BEGIN;

SET search_path TO answer42, public;

\echo 'Cleaning agent-populated data only...'

-- Create temp table for statistics
CREATE TEMP TABLE IF NOT EXISTS agent_cleanup_stats (
    table_name TEXT,
    rows_deleted INTEGER
);

-- Function to log deletions
CREATE OR REPLACE FUNCTION log_agent_cleanup(p_table_name TEXT, p_deleted INTEGER)
RETURNS VOID AS $$
BEGIN
    INSERT INTO agent_cleanup_stats (table_name, rows_deleted) VALUES (p_table_name, p_deleted);
END;
$$ LANGUAGE plpgsql;

-- =============================================================================
-- DISCOVERY SYSTEM CLEANUP
-- =============================================================================

\echo '  - Cleaning discovery system data...'

DELETE FROM answer42.discovery_feedback;
GET DIAGNOSTICS rows_affected = ROW_COUNT;
SELECT log_agent_cleanup('discovery_feedback', rows_affected);

DELETE FROM answer42.paper_relationships;
GET DIAGNOSTICS rows_affected = ROW_COUNT;
SELECT log_agent_cleanup('paper_relationships', rows_affected);

DELETE FROM answer42.discovery_results;
GET DIAGNOSTICS rows_affected = ROW_COUNT;
SELECT log_agent_cleanup('discovery_results', rows_affected);

DELETE FROM answer42.discovered_papers;
GET DIAGNOSTICS rows_affected = ROW_COUNT;
SELECT log_agent_cleanup('discovered_papers', rows_affected);

-- =============================================================================
-- ANALYSIS SYSTEM CLEANUP
-- =============================================================================

\echo '  - Cleaning analysis results...'

DELETE FROM answer42.analysis_results;
GET DIAGNOSTICS rows_affected = ROW_COUNT;
SELECT log_agent_cleanup('analysis_results', rows_affected);

DELETE FROM answer42.analysis_tasks;
GET DIAGNOSTICS rows_affected = ROW_COUNT;
SELECT log_agent_cleanup('analysis_tasks', rows_affected);

-- =============================================================================
-- CITATION AND METADATA CLEANUP
-- =============================================================================

\echo '  - Cleaning citation and metadata verifications...'

DELETE FROM answer42.citation_verifications;
GET DIAGNOSTICS rows_affected = ROW_COUNT;
SELECT log_agent_cleanup('citation_verifications', rows_affected);

DELETE FROM answer42.citations;
GET DIAGNOSTICS rows_affected = ROW_COUNT;
SELECT log_agent_cleanup('citations', rows_affected);

DELETE FROM answer42.metadata_verifications;
GET DIAGNOSTICS rows_affected = ROW_COUNT;
SELECT log_agent_cleanup('metadata_verifications', rows_affected);

-- =============================================================================
-- AI PROCESSING DATA CLEANUP
-- =============================================================================

\echo '  - Cleaning AI processing data...'

DELETE FROM answer42.summaries;
GET DIAGNOSTICS rows_affected = ROW_COUNT;
SELECT log_agent_cleanup('summaries', rows_affected);

DELETE FROM answer42.tasks;
GET DIAGNOSTICS rows_affected = ROW_COUNT;
SELECT log_agent_cleanup('tasks', rows_affected);

DELETE FROM answer42.agent_memory_store;
GET DIAGNOSTICS rows_affected = ROW_COUNT;
SELECT log_agent_cleanup('agent_memory_store', rows_affected);

DELETE FROM answer42.token_metrics;
GET DIAGNOSTICS rows_affected = ROW_COUNT;
SELECT log_agent_cleanup('token_metrics', rows_affected);

-- =============================================================================
-- CLEAR AGENT-POPULATED FIELDS IN PAPERS TABLE
-- =============================================================================

\echo '  - Clearing agent-populated fields in papers...'

UPDATE answer42.papers SET
    summary_brief = NULL,
    summary_standard = NULL,
    summary_detailed = NULL,
    key_findings = NULL,
    main_concepts = NULL,
    glossary = NULL,
    quality_score = NULL,
    quality_feedback = NULL,
    methodology_details = NULL,
    research_questions = NULL,
    crossref_metadata = NULL,
    semantic_scholar_metadata = NULL,
    crossref_verified = false,
    semantic_scholar_verified = false,
    crossref_score = NULL,
    semantic_scholar_score = NULL,
    metadata_confidence = NULL,
    crossref_last_verified = NULL,
    semantic_scholar_last_verified = NULL,
    processing_status = 'UPLOADED'
WHERE processing_status != 'UPLOADED';

GET DIAGNOSTICS rows_affected = ROW_COUNT;
SELECT log_agent_cleanup('papers_agent_fields_reset', rows_affected);

-- =============================================================================
-- SPRING BATCH CLEANUP (Agent job history)
-- =============================================================================

\echo '  - Cleaning Spring Batch agent job history...'

DELETE FROM answer42.batch_step_execution_context;
DELETE FROM answer42.batch_job_execution_context;
DELETE FROM answer42.batch_job_execution_params;

DELETE FROM answer42.batch_step_execution;
GET DIAGNOSTICS rows_affected = ROW_COUNT;
SELECT log_agent_cleanup('batch_step_execution', rows_affected);

DELETE FROM answer42.batch_job_execution;
GET DIAGNOSTICS rows_affected = ROW_COUNT;
SELECT log_agent_cleanup('batch_job_execution', rows_affected);

DELETE FROM answer42.batch_job_instance;
GET DIAGNOSTICS rows_affected = ROW_COUNT;
SELECT log_agent_cleanup('batch_job_instance', rows_affected);

-- Reset sequences
SELECT setval('answer42.batch_job_execution_seq', 1, false);
SELECT setval('answer42.batch_job_seq', 1, false);
SELECT setval('answer42.batch_step_execution_seq', 1, false);

-- =============================================================================
-- CLEANUP REPORT
-- =============================================================================

\echo ''
\echo '==============================================================================='
\echo 'AGENT DATA CLEANUP COMPLETED'
\echo '==============================================================================='

-- Show deletion statistics
SELECT 
    table_name as "Table Cleaned",
    rows_deleted as "Rows Deleted"
FROM agent_cleanup_stats
WHERE rows_deleted > 0
ORDER BY rows_deleted DESC;

-- Show total deletions
SELECT 
    'TOTAL AGENT DATA DELETED' as "Summary",
    SUM(rows_deleted) as "Total Rows"
FROM agent_cleanup_stats;

-- Show preserved data
\echo ''
\echo 'PRESERVED USER DATA:'
\echo '===================='

SELECT 'papers' as "Table", COUNT(*) as "Records" FROM answer42.papers
UNION ALL
SELECT 'projects', COUNT(*) FROM answer42.projects
UNION ALL
SELECT 'chat_sessions', COUNT(*) FROM answer42.chat_sessions
UNION ALL
SELECT 'users', COUNT(*) FROM answer42.users
ORDER BY "Records" DESC;

-- Clean up temp function
DROP FUNCTION IF EXISTS log_agent_cleanup(TEXT, INTEGER);

\echo ''
\echo 'Agent data cleanup completed successfully!'
\echo 'Papers and projects preserved - ready for re-processing by agents.'
\echo ''

COMMIT;
