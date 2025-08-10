-- =============================================================================
-- Answer42 Quick Database Cleanup Script
-- =============================================================================
-- 
-- This is a simplified version for rapid development cleanup.
-- Use this during development when you need to quickly reset all data.
--
-- WARNING: This will delete ALL papers, projects, and related data!
--
-- Usage:
-- psql -d postgres -f scripts/cleanup-database-quick.sql
-- 
-- Note: Answer42 uses 'postgres' as the default database name with Supabase.
-- If using a different database name, replace 'postgres' with your database name.
--
-- =============================================================================

BEGIN;

SET search_path TO answer42, public;

\echo 'Quick cleanup: Disabling foreign key constraints...'

-- Temporarily disable foreign key constraints for faster cleanup
SET session_replication_role = replica;

-- Delete all dependent data first
TRUNCATE TABLE answer42.chat_messages CASCADE;
TRUNCATE TABLE answer42.chat_sessions CASCADE;
TRUNCATE TABLE answer42.flashcards CASCADE;
TRUNCATE TABLE answer42.concept_maps CASCADE;
TRUNCATE TABLE answer42.practice_questions CASCADE;
TRUNCATE TABLE answer42.notes CASCADE;
TRUNCATE TABLE answer42.visualization_states CASCADE;
TRUNCATE TABLE answer42.paper_bookmarks CASCADE;
TRUNCATE TABLE answer42.paper_tags CASCADE;
TRUNCATE TABLE answer42.project_papers CASCADE;
TRUNCATE TABLE answer42.discovery_feedback CASCADE;
TRUNCATE TABLE answer42.paper_relationships CASCADE;
TRUNCATE TABLE answer42.discovery_results CASCADE;
TRUNCATE TABLE answer42.discovered_papers CASCADE;

-- Delete agent-populated tables
TRUNCATE TABLE answer42.analysis_results CASCADE;
TRUNCATE TABLE answer42.analysis_tasks CASCADE;
TRUNCATE TABLE answer42.citation_verifications CASCADE;
TRUNCATE TABLE answer42.citations CASCADE;
TRUNCATE TABLE answer42.metadata_verifications CASCADE;
TRUNCATE TABLE answer42.summaries CASCADE;
TRUNCATE TABLE answer42.tasks CASCADE;
TRUNCATE TABLE answer42.agent_memory_store CASCADE;
TRUNCATE TABLE answer42.token_metrics CASCADE;

-- Delete paper content
TRUNCATE TABLE answer42.paper_sections CASCADE;
TRUNCATE TABLE answer42.paper_content CASCADE;
TRUNCATE TABLE answer42.papers CASCADE;

-- Delete projects
TRUNCATE TABLE answer42.projects CASCADE;

-- Clean Spring Batch tables
TRUNCATE TABLE answer42.batch_step_execution_context CASCADE;
TRUNCATE TABLE answer42.batch_job_execution_context CASCADE;
TRUNCATE TABLE answer42.batch_job_execution_params CASCADE;
TRUNCATE TABLE answer42.batch_step_execution CASCADE;
TRUNCATE TABLE answer42.batch_job_execution CASCADE;
TRUNCATE TABLE answer42.batch_job_instance CASCADE;

-- Clean up orphaned tags
DELETE FROM answer42.tags 
WHERE id NOT IN (SELECT DISTINCT tag_id FROM answer42.paper_tags WHERE tag_id IS NOT NULL);

-- Re-enable foreign key constraints
SET session_replication_role = DEFAULT;

-- Reset sequences
SELECT setval('answer42.batch_job_execution_seq', 1, false);
SELECT setval('answer42.batch_job_seq', 1, false);
SELECT setval('answer42.batch_step_execution_seq', 1, false);

\echo 'Quick cleanup completed!'
\echo ''
\echo 'Summary of preserved data:'
SELECT 'users' as "Preserved Table", COUNT(*) as "Records" FROM answer42.users
UNION ALL
SELECT 'subscriptions', COUNT(*) FROM answer42.subscriptions
UNION ALL
SELECT 'credit_balances', COUNT(*) FROM answer42.credit_balances;

COMMIT;
