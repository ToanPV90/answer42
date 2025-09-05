-- =============================================================================
-- Answer42 Database Cleanup Script
-- =============================================================================
-- 
-- This script cleans up papers, projects, and all agent-populated data while
-- preserving user accounts, subscriptions, and system configuration.
--
-- IMPORTANT: This script will DELETE all:
-- - Papers and related content
-- - Projects and project-paper relationships
-- - All agent-generated data (discovered papers, analysis results, etc.)
-- - Chat sessions and messages
-- - Spring Batch job history
-- - All study materials (flashcards, concept maps, etc.)
--
-- PRESERVED data:
-- - User accounts and authentication
-- - Subscription plans and user subscriptions
-- - Credit balances and transaction history
-- - User settings and preferences
-- - System configuration (operation costs, etc.)
--
-- Usage:
-- psql -d postgres -f scripts/cleanup-database.sql
-- 
-- Note: Answer42 uses 'postgres' as the default database name with Supabase.
-- If using a different database name, replace 'postgres' with your database name.
--
-- =============================================================================

-- Start transaction for atomic cleanup
BEGIN;

-- Set search path to answer42 schema
SET search_path TO answer42, public;

-- =============================================================================
-- LOGGING SETUP
-- =============================================================================

-- Create temporary table for cleanup statistics
CREATE TEMP TABLE IF NOT EXISTS cleanup_stats (
    table_name TEXT,
    rows_before INTEGER,
    rows_after INTEGER,
    rows_deleted INTEGER
);

-- Function to log table cleanup
CREATE OR REPLACE FUNCTION log_cleanup_stats(p_table_name TEXT) 
RETURNS VOID AS $$
DECLARE
    v_count INTEGER;
BEGIN
    EXECUTE format('SELECT COUNT(*) FROM answer42.%I', p_table_name) INTO v_count;
    INSERT INTO cleanup_stats (table_name, rows_after, rows_deleted)
    VALUES (p_table_name, v_count, 
            COALESCE((SELECT rows_before FROM cleanup_stats WHERE table_name = p_table_name), 0) - v_count);
END;
$$ LANGUAGE plpgsql;

-- Function to capture before counts
CREATE OR REPLACE FUNCTION capture_before_count(p_table_name TEXT)
RETURNS VOID AS $$
DECLARE
    v_count INTEGER;
BEGIN
    EXECUTE format('SELECT COUNT(*) FROM answer42.%I', p_table_name) INTO v_count;
    INSERT INTO cleanup_stats (table_name, rows_before, rows_after, rows_deleted)
    VALUES (p_table_name, v_count, 0, 0)
    ON CONFLICT (table_name) DO UPDATE SET rows_before = v_count;
END;
$$ LANGUAGE plpgsql;

-- =============================================================================
-- CAPTURE BEFORE COUNTS
-- =============================================================================

\echo 'Starting Answer42 database cleanup...'
\echo 'Capturing table row counts before cleanup...'

SELECT capture_before_count('papers');
SELECT capture_before_count('projects');
SELECT capture_before_count('project_papers');
SELECT capture_before_count('paper_content');
SELECT capture_before_count('paper_sections');
SELECT capture_before_count('paper_tags');
SELECT capture_before_count('paper_bookmarks');
SELECT capture_before_count('discovered_papers');
SELECT capture_before_count('paper_relationships');
SELECT capture_before_count('discovery_results');
SELECT capture_before_count('discovery_feedback');
SELECT capture_before_count('tasks');
SELECT capture_before_count('agent_memory_store');
SELECT capture_before_count('token_metrics');
SELECT capture_before_count('summaries');
SELECT capture_before_count('analysis_results');
SELECT capture_before_count('analysis_tasks');
SELECT capture_before_count('citation_verifications');
SELECT capture_before_count('citations');
SELECT capture_before_count('metadata_verifications');
SELECT capture_before_count('chat_sessions');
SELECT capture_before_count('chat_messages');
SELECT capture_before_count('notes');
SELECT capture_before_count('flashcards');
SELECT capture_before_count('concept_maps');
SELECT capture_before_count('practice_questions');
SELECT capture_before_count('visualization_states');
SELECT capture_before_count('tags');
SELECT capture_before_count('batch_job_execution');
SELECT capture_before_count('batch_step_execution');
SELECT capture_before_count('batch_job_instance');

-- =============================================================================
-- CLEANUP PHASE 1: DEPENDENT TABLES (Foreign Key References)
-- =============================================================================

\echo 'Phase 1: Cleaning up dependent tables...'

-- 1. Chat system cleanup
\echo '  - Cleaning chat messages and sessions...'
DELETE FROM answer42.chat_messages;
SELECT log_cleanup_stats('chat_messages');

DELETE FROM answer42.chat_sessions;
SELECT log_cleanup_stats('chat_sessions');

-- 2. Study materials and user-generated content
\echo '  - Cleaning study materials...'
DELETE FROM answer42.flashcards;
SELECT log_cleanup_stats('flashcards');

DELETE FROM answer42.concept_maps;
SELECT log_cleanup_stats('concept_maps');

DELETE FROM answer42.practice_questions;
SELECT log_cleanup_stats('practice_questions');

DELETE FROM answer42.notes;
SELECT log_cleanup_stats('notes');

DELETE FROM answer42.visualization_states;
SELECT log_cleanup_stats('visualization_states');

-- 3. Paper bookmarks and relationships
\echo '  - Cleaning paper bookmarks and tags...'
DELETE FROM answer42.paper_bookmarks;
SELECT log_cleanup_stats('paper_bookmarks');

DELETE FROM answer42.paper_tags;
SELECT log_cleanup_stats('paper_tags');

-- Clean up orphaned tags (tags not used by any papers)
DELETE FROM answer42.tags 
WHERE id NOT IN (SELECT DISTINCT tag_id FROM answer42.paper_tags);
SELECT log_cleanup_stats('tags');

-- 4. Project-paper relationships
\echo '  - Cleaning project-paper relationships...'
DELETE FROM answer42.project_papers;
SELECT log_cleanup_stats('project_papers');

-- 5. Discovery system cleanup
\echo '  - Cleaning discovery feedback and relationships...'
DELETE FROM answer42.discovery_feedback;
SELECT log_cleanup_stats('discovery_feedback');

DELETE FROM answer42.paper_relationships;
SELECT log_cleanup_stats('paper_relationships');

DELETE FROM answer42.discovery_results;
SELECT log_cleanup_stats('discovery_results');

DELETE FROM answer42.discovered_papers;
SELECT log_cleanup_stats('discovered_papers');

-- =============================================================================
-- CLEANUP PHASE 2: AGENT-POPULATED TABLES
-- =============================================================================

\echo 'Phase 2: Cleaning agent-populated data...'

-- 1. Analysis system cleanup
\echo '  - Cleaning analysis results and tasks...'
DELETE FROM answer42.analysis_results;
SELECT log_cleanup_stats('analysis_results');

DELETE FROM answer42.analysis_tasks;
SELECT log_cleanup_stats('analysis_tasks');

-- 2. Citation and metadata verification cleanup
\echo '  - Cleaning citation and metadata verifications...'
DELETE FROM answer42.citation_verifications;
SELECT log_cleanup_stats('citation_verifications');

DELETE FROM answer42.citations;
SELECT log_cleanup_stats('citations');

DELETE FROM answer42.metadata_verifications;
SELECT log_cleanup_stats('metadata_verifications');

-- 3. Summaries cleanup
\echo '  - Cleaning AI-generated summaries...'
DELETE FROM answer42.summaries;
SELECT log_cleanup_stats('summaries');

-- 4. Agent task tracking and memory
\echo '  - Cleaning agent tasks and memory...'
DELETE FROM answer42.tasks;
SELECT log_cleanup_stats('tasks');

DELETE FROM answer42.agent_memory_store;
SELECT log_cleanup_stats('agent_memory_store');

-- 5. Token metrics and usage tracking
\echo '  - Cleaning token metrics...'
DELETE FROM answer42.token_metrics;
SELECT log_cleanup_stats('token_metrics');

-- =============================================================================
-- CLEANUP PHASE 3: PAPER CONTENT AND STRUCTURE
-- =============================================================================

\echo 'Phase 3: Cleaning paper content and structure...'

-- 1. Paper sections and content
\echo '  - Cleaning paper sections and content...'
DELETE FROM answer42.paper_sections;
SELECT log_cleanup_stats('paper_sections');

DELETE FROM answer42.paper_content;
SELECT log_cleanup_stats('paper_content');

-- 2. Core papers table
\echo '  - Cleaning papers...'
DELETE FROM answer42.papers;
SELECT log_cleanup_stats('papers');

-- =============================================================================
-- CLEANUP PHASE 4: PROJECTS
-- =============================================================================

\echo 'Phase 4: Cleaning projects...'

DELETE FROM answer42.projects;
SELECT log_cleanup_stats('projects');

-- =============================================================================
-- CLEANUP PHASE 5: SPRING BATCH CLEANUP
-- =============================================================================

\echo 'Phase 5: Cleaning Spring Batch job history...'

-- Clean Spring Batch execution context and parameters
DELETE FROM answer42.batch_step_execution_context;
DELETE FROM answer42.batch_job_execution_context;
DELETE FROM answer42.batch_job_execution_params;

-- Clean Spring Batch executions
DELETE FROM answer42.batch_step_execution;
SELECT log_cleanup_stats('batch_step_execution');

DELETE FROM answer42.batch_job_execution;
SELECT log_cleanup_stats('batch_job_execution');

-- Clean Spring Batch instances
DELETE FROM answer42.batch_job_instance;
SELECT log_cleanup_stats('batch_job_instance');

-- Reset Spring Batch sequences
SELECT setval('answer42.batch_job_execution_seq', 1, false);
SELECT setval('answer42.batch_job_seq', 1, false);
SELECT setval('answer42.batch_step_execution_seq', 1, false);

-- =============================================================================
-- VACUUM AND ANALYZE
-- =============================================================================

\echo 'Phase 6: Optimizing database...'

-- Vacuum and analyze cleaned tables for optimal performance
VACUUM ANALYZE answer42.papers;
VACUUM ANALYZE answer42.projects;
VACUUM ANALYZE answer42.project_papers;
VACUUM ANALYZE answer42.paper_content;
VACUUM ANALYZE answer42.paper_sections;
VACUUM ANALYZE answer42.paper_tags;
VACUUM ANALYZE answer42.discovered_papers;
VACUUM ANALYZE answer42.paper_relationships;
VACUUM ANALYZE answer42.discovery_results;
VACUUM ANALYZE answer42.tasks;
VACUUM ANALYZE answer42.agent_memory_store;
VACUUM ANALYZE answer42.token_metrics;
VACUUM ANALYZE answer42.chat_sessions;
VACUUM ANALYZE answer42.chat_messages;
VACUUM ANALYZE answer42.batch_job_execution;
VACUUM ANALYZE answer42.batch_step_execution;
VACUUM ANALYZE answer42.batch_job_instance;

-- =============================================================================
-- CLEANUP STATISTICS REPORT
-- =============================================================================

\echo ''
\echo '==============================================================================='
\echo 'ANSWER42 DATABASE CLEANUP COMPLETED'
\echo '==============================================================================='
\echo ''

-- Display cleanup statistics
SELECT 
    table_name as "Table",
    rows_before as "Before",
    rows_after as "After", 
    rows_deleted as "Deleted"
FROM cleanup_stats 
WHERE rows_deleted > 0
ORDER BY rows_deleted DESC;

-- Display summary statistics
SELECT 
    'TOTAL RECORDS DELETED' as "Summary",
    SUM(rows_deleted) as "Count"
FROM cleanup_stats
WHERE rows_deleted > 0;

-- Display preserved data summary
\echo ''
\echo 'PRESERVED DATA SUMMARY:'
\echo '======================='

SELECT 'users' as "Table", COUNT(*) as "Records" FROM answer42.users
UNION ALL
SELECT 'subscriptions', COUNT(*) FROM answer42.subscriptions
UNION ALL
SELECT 'credit_balances', COUNT(*) FROM answer42.credit_balances
UNION ALL
SELECT 'credit_transactions', COUNT(*) FROM answer42.credit_transactions
UNION ALL
SELECT 'user_settings', COUNT(*) FROM answer42.user_settings
UNION ALL
SELECT 'subscription_plans', COUNT(*) FROM answer42.subscription_plans
UNION ALL
SELECT 'operation_costs', COUNT(*) FROM answer42.operation_costs
ORDER BY "Records" DESC;

-- =============================================================================
-- VERIFICATION QUERIES
-- =============================================================================

\echo ''
\echo 'VERIFICATION - These should all return 0:'
\echo '========================================'

-- Verify core tables are empty
SELECT 'papers' as "Table", COUNT(*) as "Should be 0" FROM answer42.papers
UNION ALL
SELECT 'projects', COUNT(*) FROM answer42.projects
UNION ALL
SELECT 'discovered_papers', COUNT(*) FROM answer42.discovered_papers
UNION ALL
SELECT 'tasks', COUNT(*) FROM answer42.tasks
UNION ALL
SELECT 'chat_sessions', COUNT(*) FROM answer42.chat_sessions
UNION ALL
SELECT 'batch_job_execution', COUNT(*) FROM answer42.batch_job_execution;

-- Clean up temporary objects
DROP FUNCTION IF EXISTS log_cleanup_stats(TEXT);
DROP FUNCTION IF EXISTS capture_before_count(TEXT);

\echo ''
\echo 'Database cleanup completed successfully!'
\echo 'All papers, projects, and agent-populated data have been removed.'
\echo 'User accounts, subscriptions, and system settings have been preserved.'
\echo ''

COMMIT;
