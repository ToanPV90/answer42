create schema if not exists "answer42";

create type "answer42"."operation_type_upper" as enum ('PAPER_UPLOAD', 'GENERATE_SUMMARY', 'CONCEPT_EXPLANATION', 'STUDY_GUIDE_CREATION', 'QA_SESSION', 'PERPLEXITY_QUERY', 'CROSS_REFERENCE_CHAT', 'PAPER_CHAT', 'RESEARCH_EXPLORER_CHAT', 'DEEP_SUMMARY', 'METHODOLOGY_ANALYSIS', 'RESULTS_INTERPRETATION', 'CRITICAL_EVALUATION', 'RESEARCH_IMPLICATIONS', 'COMPARISON_MATRIX', 'VENN_DIAGRAM_ANALYSIS', 'TIMELINE_ANALYSIS', 'METRICS_COMPARISON', 'RESEARCH_EXPLORER_CHAT_P', 'RESEARCH_EXPLORER_CHAT_C', 'RESEARCH_EXPLORER', 'PAPER', 'PAPER_TEXT_EXTRACTION', 'METADATA_ENHANCEMENT', 'CONTENT_SUMMARIZATION', 'QUALITY_CHECKING', 'CITATION_FORMATTING', 'RESEARCH_DISCOVERY', 'FULL_PIPELINE_PROCESSING', 'TOKEN_USAGE_TRACKING');

create table "answer42"."agent_memory_store" (
    "key" character varying(255) not null,
    "created_at" timestamp with time zone not null,
    "updated_at" timestamp with time zone not null,
    "data" jsonb not null
);


create table "answer42"."analysis_results" (
    "id" uuid not null,
    "task_id" uuid not null,
    "user_id" uuid not null,
    "paper_id" uuid not null,
    "analysis_type" character varying(255) not null,
    "content" text not null,
    "sections" jsonb default '[]'::jsonb,
    "citations" jsonb default '[]'::jsonb,
    "visual_elements" jsonb default '[]'::jsonb,
    "metadata" jsonb default '{}'::jsonb,
    "created_at" timestamp with time zone not null,
    "is_archived" boolean not null,
    "updated_at" timestamp with time zone not null
);


create table "answer42"."analysis_tasks" (
    "id" uuid not null,
    "user_id" uuid not null,
    "paper_id" uuid not null,
    "analysis_type" character varying(255) not null,
    "status" character varying(255) not null,
    "result_id" uuid,
    "error_message" text,
    "created_at" timestamp with time zone not null,
    "updated_at" timestamp with time zone not null,
    "task_id" uuid not null
);


create table "answer42"."chat_messages" (
    "id" uuid not null default uuid_generate_v4(),
    "session_id" uuid not null,
    "role" character varying(255) not null,
    "content" text not null,
    "citations" jsonb default '[]'::jsonb,
    "metadata" jsonb default '{}'::jsonb,
    "created_at" timestamp with time zone not null,
    "sequence_number" integer not null default 0,
    "message_type" character varying(255) not null default 'message'::text,
    "is_edited" boolean not null default false,
    "token_count" integer,
    "updated_at" timestamp with time zone not null
);


create table "answer42"."chat_sessions" (
    "id" uuid not null default uuid_generate_v4(),
    "user_id" uuid,
    "mode" character varying(255) not null default 'general'::text,
    "context" jsonb default '{}'::jsonb,
    "created_at" timestamp with time zone not null,
    "updated_at" timestamp with time zone not null,
    "provider" character varying(255) not null,
    "title" character varying(255)
);


create table "answer42"."citation_verifications" (
    "id" uuid not null default uuid_generate_v4(),
    "citation_id" uuid not null,
    "paper_id" uuid not null,
    "doi" text,
    "verified" boolean default false,
    "verification_source" text,
    "confidence" numeric(3,2) default 0,
    "verification_date" timestamp with time zone default now(),
    "created_at" timestamp with time zone not null default now(),
    "updated_at" timestamp with time zone not null default now(),
    "semantic_scholar_id" text,
    "arxiv_id" text,
    "merged_metadata" jsonb default '{}'::jsonb
);


create table "answer42"."citations" (
    "id" uuid not null default uuid_generate_v4(),
    "paper_id" uuid not null,
    "citation_data" jsonb not null,
    "raw_text" text,
    "created_at" timestamp with time zone not null,
    "updated_at" timestamp with time zone not null
);


create table "answer42"."concept_maps" (
    "id" uuid not null default uuid_generate_v4(),
    "paper_id" uuid not null,
    "user_id" uuid not null,
    "title" text not null,
    "description" text,
    "map_data" jsonb not null,
    "image_url" text,
    "tags" text[],
    "metadata" jsonb default '{}'::jsonb,
    "created_at" timestamp with time zone not null,
    "updated_at" timestamp with time zone not null
);


create table "answer42"."credit_balances" (
    "id" uuid not null default gen_random_uuid(),
    "user_id" uuid not null,
    "balance" integer not null default 0,
    "used_this_period" integer not null default 0,
    "next_reset_date" timestamp with time zone not null,
    "created_at" timestamp with time zone not null,
    "updated_at" timestamp with time zone not null
);


create table "answer42"."credit_packages" (
    "id" uuid not null default gen_random_uuid(),
    "name" text not null,
    "credits" integer not null,
    "price_usd" numeric(10,2) not null,
    "price_sats" bigint,
    "is_active" boolean not null default true,
    "created_at" timestamp with time zone not null,
    "updated_at" timestamp with time zone not null
);


create table "answer42"."credit_transactions" (
    "id" uuid not null default gen_random_uuid(),
    "user_id" uuid not null,
    "transaction_type" character varying(255) not null,
    "amount" integer not null,
    "balance_after" integer not null,
    "operation_type" character varying(255),
    "description" character varying(255) not null,
    "reference_id" character varying(255),
    "created_at" timestamp with time zone not null
);


create table "answer42"."discovered_papers" (
    "id" uuid not null default gen_random_uuid(),
    "doi" character varying(255),
    "semantic_scholar_id" character varying(100),
    "arxiv_id" character varying(50),
    "pubmed_id" character varying(50),
    "title" character varying(255) not null,
    "authors" jsonb not null default '[]'::jsonb,
    "abstract" text,
    "publication_date" timestamp with time zone,
    "publication_year" integer,
    "journal" character varying(255),
    "venue" character varying(255),
    "publisher" character varying(500),
    "volume" character varying(50),
    "issue" character varying(50),
    "pages" character varying(50),
    "url" text,
    "pdf_url" character varying(255),
    "open_access_pdf_url" text,
    "is_open_access" boolean default false,
    "citation_count" integer default 0,
    "reference_count" integer default 0,
    "influential_citation_count" integer default 0,
    "relevance_score" double precision default 0.0,
    "source_reliability_score" double precision default 0.0,
    "data_completeness_score" double precision default 0.0,
    "discovery_source" character varying(255) not null,
    "discovery_context" jsonb,
    "crossref_metadata" jsonb,
    "semantic_scholar_metadata" jsonb,
    "perplexity_metadata" jsonb,
    "fields_of_study" jsonb default '[]'::jsonb,
    "subjects" jsonb default '[]'::jsonb,
    "topics" jsonb default '[]'::jsonb,
    "first_discovered_at" timestamp with time zone default now(),
    "created_at" timestamp with time zone default now(),
    "updated_at" timestamp with time zone default now(),
    "access_url" character varying(255),
    "confidence_score" double precision,
    "discovery_metadata" jsonb,
    "discovery_session_id" character varying(255),
    "duplicate_of_id" uuid,
    "external_id" character varying(255),
    "is_archived" boolean,
    "is_duplicate" boolean,
    "is_verified" boolean,
    "last_accessed_at" timestamp with time zone,
    "open_access" boolean,
    "paper_abstract" text,
    "relationship_type" character varying(255) not null,
    "source_specific_data" jsonb,
    "user_notes" text,
    "user_rating" integer,
    "venue_type" character varying(255),
    "verification_score" double precision,
    "year" integer,
    "source_paper_id" uuid,
    "user_id" uuid
);


create table "answer42"."discovery_feedback" (
    "id" uuid not null default gen_random_uuid(),
    "user_id" uuid not null,
    "discovery_result_id" uuid not null,
    "discovered_paper_id" uuid not null,
    "source_paper_id" uuid not null,
    "feedback_type" character varying(255) not null,
    "relevance_rating" integer,
    "quality_rating" integer,
    "usefulness_rating" integer,
    "feedback_text" character varying(255),
    "feedback_context" jsonb,
    "clicked_paper" boolean default false,
    "downloaded_paper" boolean default false,
    "bookmarked_paper" boolean default false,
    "shared_paper" boolean default false,
    "time_spent_viewing_seconds" integer default 0,
    "ip_address" character varying(255),
    "user_agent" character varying(255),
    "session_id" character varying(255),
    "created_at" timestamp with time zone not null
);


create table "answer42"."discovery_results" (
    "id" uuid not null default gen_random_uuid(),
    "source_paper_id" uuid not null,
    "user_id" uuid not null,
    "discovery_configuration" jsonb not null,
    "discovery_scope" character varying(50) not null,
    "status" character varying(50) not null default 'PENDING'::character varying,
    "started_at" timestamp with time zone not null,
    "completed_at" timestamp with time zone,
    "execution_duration_ms" bigint,
    "total_papers_discovered" integer default 0,
    "crossref_papers_count" integer default 0,
    "semantic_scholar_papers_count" integer default 0,
    "perplexity_papers_count" integer default 0,
    "average_relevance_score" double precision default 0.0,
    "average_quality_score" double precision default 0.0,
    "high_confidence_papers_count" integer default 0,
    "crossref_success" boolean default false,
    "semantic_scholar_success" boolean default false,
    "perplexity_success" boolean default false,
    "error_message" text,
    "error_details" jsonb,
    "processing_metadata" jsonb,
    "api_usage_stats" jsonb,
    "agent_task_id" uuid not null,
    "created_at" timestamp with time zone not null,
    "updated_at" timestamp with time zone not null,
    "ai_synthesis_enabled" boolean,
    "ai_synthesis_time_ms" bigint,
    "api_calls_made" integer,
    "cache_hit_count" integer,
    "cache_miss_count" integer,
    "configuration_name" character varying(100) not null,
    "crossref_papers_found" integer,
    "discovery_end_time" timestamp(6) with time zone,
    "discovery_start_time" timestamp(6) with time zone not null,
    "discovery_statistics" text,
    "errors" text,
    "overall_confidence_score" double precision,
    "perplexity_papers_found" integer,
    "requires_user_review" boolean,
    "semantic_scholar_papers_found" integer,
    "sources_used" text,
    "total_papers_after_filtering" integer,
    "total_processing_time_ms" bigint,
    "warnings" text
);


create table "answer42"."flashcards" (
    "id" uuid not null default uuid_generate_v4(),
    "paper_id" uuid not null,
    "user_id" uuid not null,
    "question" text not null,
    "answer" text not null,
    "section" text,
    "tags" text[],
    "difficulty" text,
    "last_reviewed" timestamp with time zone,
    "review_count" integer default 0,
    "metadata" jsonb default '{}'::jsonb,
    "created_at" timestamp with time zone not null,
    "updated_at" timestamp with time zone not null
);


create table "answer42"."invoices" (
    "id" uuid not null default uuid_generate_v4(),
    "subscription_id" uuid not null,
    "amount" numeric(10,2) not null,
    "currency" text not null,
    "status" text not null,
    "invoice_url" text,
    "paid_at" timestamp with time zone not null,
    "created_at" timestamp with time zone not null
);


create table "answer42"."metadata_verifications" (
    "id" uuid not null default uuid_generate_v4(),
    "paper_id" uuid not null,
    "source" text not null,
    "confidence" numeric(3,2) default 0,
    "metadata" jsonb,
    "matched_by" text,
    "identifier_used" text,
    "verified_at" timestamp with time zone not null,
    "created_at" timestamp with time zone not null,
    "updated_at" timestamp with time zone not null
);


create table "answer42"."mode_operation_mapping" (
    "mode" text not null,
    "operation_type" answer42.operation_type_upper not null
);


create table "answer42"."notes" (
    "id" uuid not null default uuid_generate_v4(),
    "paper_id" uuid not null,
    "user_id" uuid not null,
    "content" text not null,
    "location" jsonb default '{}'::jsonb,
    "created_at" timestamp with time zone not null,
    "updated_at" timestamp with time zone not null
);


create table "answer42"."operation_costs" (
    "id" uuid not null default gen_random_uuid(),
    "operation_type" answer42.operation_type_upper not null,
    "basic_cost" integer not null,
    "pro_cost" integer not null,
    "description" text not null,
    "created_at" timestamp with time zone not null default now(),
    "updated_at" timestamp with time zone not null default now(),
    "scholar_cost" integer
);


create table "answer42"."paper_bookmarks" (
    "id" uuid not null,
    "created_at" timestamp with time zone not null,
    "notes" character varying(1000),
    "tags" character varying(500),
    "updated_at" timestamp with time zone,
    "discovered_paper_id" uuid not null,
    "user_id" uuid not null
);


create table "answer42"."paper_content" (
    "id" uuid not null default gen_random_uuid(),
    "paper_id" uuid not null,
    "content" text not null,
    "created_at" timestamp with time zone default now(),
    "updated_at" timestamp with time zone default now()
);


create table "answer42"."paper_relationships" (
    "id" uuid not null default gen_random_uuid(),
    "source_paper_id" uuid not null,
    "discovered_paper_id" uuid not null,
    "relationship_type" character varying(50) not null,
    "relationship_strength" double precision default 0.0,
    "confidence_score" double precision default 0.0,
    "discovery_source" character varying(50) not null,
    "discovery_method" character varying(100),
    "discovery_context" jsonb,
    "evidence" jsonb,
    "reasoning" text,
    "created_at" timestamp with time zone default now(),
    "updated_at" timestamp with time zone default now()
);


create table "answer42"."paper_sections" (
    "id" uuid not null default gen_random_uuid(),
    "paper_id" uuid not null,
    "title" text not null,
    "content" text not null,
    "index" integer not null,
    "created_at" timestamp with time zone default now(),
    "updated_at" timestamp with time zone default now()
);


create table "answer42"."paper_tags" (
    "paper_id" uuid not null,
    "tag_id" uuid not null,
    "created_at" timestamp with time zone default now()
);


create table "answer42"."papers" (
    "crossref_score" double precision,
    "crossref_verified" boolean,
    "is_public" boolean,
    "metadata_confidence" double precision,
    "publication_date" date,
    "quality_score" double precision,
    "references_count" integer,
    "semantic_scholar_score" double precision,
    "semantic_scholar_verified" boolean,
    "year" integer,
    "created_at" timestamp(6) with time zone,
    "crossref_last_verified" timestamp(6) with time zone,
    "file_size" bigint,
    "semantic_scholar_last_verified" timestamp(6) with time zone,
    "updated_at" timestamp(6) with time zone,
    "id" uuid not null,
    "user_id" uuid,
    "paper_abstract" text,
    "arxiv_id" character varying(255),
    "crossref_doi" character varying(255),
    "doi" character varying(255),
    "file_path" character varying(255) not null,
    "file_type" character varying(255),
    "journal" character varying(255),
    "metadata_source" character varying(255),
    "processing_status" character varying(255),
    "semantic_scholar_id" character varying(255),
    "status" character varying(255),
    "summary_brief" text,
    "summary_detailed" text,
    "summary_standard" text,
    "text_content" text,
    "title" character varying(255) not null,
    "authors" jsonb not null,
    "citations" jsonb,
    "crossref_metadata" jsonb,
    "glossary" jsonb,
    "key_findings" jsonb,
    "main_concepts" jsonb,
    "metadata" jsonb,
    "metadata_source_details" jsonb,
    "methodology_details" jsonb,
    "quality_feedback" jsonb,
    "research_questions" jsonb,
    "semantic_scholar_metadata" jsonb,
    "topics" jsonb,
    "glossart" jsonb
);


create table "answer42"."payment_methods" (
    "id" uuid not null default uuid_generate_v4(),
    "user_id" uuid,
    "provider" text not null,
    "type" text not null,
    "last_four" text,
    "is_default" boolean default false,
    "provider_data" jsonb default '{}'::jsonb,
    "created_at" timestamp with time zone default now(),
    "updated_at" timestamp with time zone default now()
);


create table "answer42"."practice_questions" (
    "id" uuid not null default uuid_generate_v4(),
    "paper_id" uuid,
    "user_id" uuid,
    "question_text" text not null,
    "question_type" text not null,
    "correct_answer" text not null,
    "options" jsonb,
    "explanation" text,
    "difficulty" text,
    "section" text,
    "tags" text[],
    "metadata" jsonb default '{}'::jsonb,
    "created_at" timestamp with time zone default now(),
    "updated_at" timestamp with time zone default now()
);


create table "answer42"."project_papers" (
    "project_id" uuid not null,
    "paper_id" uuid not null,
    "order" integer default 0,
    "added_at" timestamp with time zone default now()
);


create table "answer42"."projects" (
    "id" uuid not null default uuid_generate_v4(),
    "user_id" uuid,
    "name" character varying(255) not null,
    "description" character varying(255),
    "settings" jsonb default '{}'::jsonb,
    "created_at" timestamp with time zone default now(),
    "updated_at" timestamp with time zone default now(),
    "is_public" boolean default false
);


create table "answer42"."subscription_plans" (
    "id" character varying(255) not null,
    "name" character varying(255) not null,
    "description" character varying(255),
    "price_monthly" numeric(38,2) not null,
    "price_annually" numeric(38,2) not null,
    "features" jsonb default '{}'::jsonb,
    "base_credits" integer not null default 0,
    "rollover_limit" integer not null default 0,
    "is_active" boolean default true,
    "created_at" timestamp with time zone default now(),
    "updated_at" timestamp with time zone default now(),
    "is_free" boolean default false,
    "default_ai_tier" character varying(255)
);


create table "answer42"."subscriptions" (
    "id" uuid not null default uuid_generate_v4(),
    "user_id" uuid,
    "plan_id" character varying(255) not null,
    "status" character varying(255) not null,
    "current_period_start" timestamp with time zone,
    "current_period_end" timestamp with time zone,
    "payment_provider" character varying(255) not null,
    "payment_provider_id" character varying(255),
    "created_at" timestamp with time zone default now(),
    "updated_at" timestamp with time zone default now()
);


create table "answer42"."summaries" (
    "id" uuid not null default uuid_generate_v4(),
    "paper_id" uuid,
    "brief" text,
    "standard" text,
    "detailed" text,
    "created_at" timestamp with time zone default now(),
    "updated_at" timestamp with time zone default now(),
    "content" text
);


create table "answer42"."tags" (
    "id" uuid not null default uuid_generate_v4(),
    "user_id" uuid,
    "name" text not null,
    "color" text default '#6B7280'::text,
    "created_at" timestamp with time zone default now()
);


create table "answer42"."tasks" (
    "id" character varying(255) not null,
    "agent_id" character varying(255) not null,
    "user_id" uuid not null,
    "input" jsonb not null,
    "status" character varying(255) not null,
    "error" character varying(255),
    "result" jsonb,
    "created_at" timestamp with time zone default now(),
    "started_at" timestamp with time zone,
    "completed_at" timestamp with time zone
);


create table "answer42"."user_operations" (
    "id" uuid not null default uuid_generate_v4(),
    "user_id" uuid not null,
    "operation_type" text not null,
    "created_at" timestamp with time zone default now(),
    "metadata" jsonb default '{}'::jsonb,
    "reference_id" text,
    "ai_tier" text,
    "credit_cost" integer
);


create table "answer42"."user_roles" (
    "user_id" uuid not null,
    "role" character varying(255),
    "id" uuid not null default gen_random_uuid()
);


create table "answer42"."user_settings" (
    "user_id" uuid not null,
    "auto_generate_study_materials" boolean default true,
    "created_at" timestamp with time zone not null default now(),
    "updated_at" timestamp with time zone not null default now(),
    "email_notifications" boolean default true,
    "system_notifications" boolean default true,
    "id" uuid not null,
    "academic_field" character varying(255),
    "email_notifications_enabled" boolean not null,
    "study_material_generation_enabled" boolean not null,
    "system_notifications_enabled" boolean not null,
    "openai_api_key" character varying(255),
    "perplexity_api_key" character varying(255),
    "anthropic_api_key" character varying(255)
);


create table "answer42"."users" (
    "enabled" boolean not null,
    "id" uuid not null,
    "email" character varying(255),
    "password" character varying(255) not null,
    "username" character varying(255) not null,
    "created_at" timestamp with time zone,
    "last_login" timestamp with time zone
);


create table "answer42"."visualization_states" (
    "id" uuid not null default uuid_generate_v4(),
    "session_id" uuid not null,
    "user_id" uuid not null,
    "highlighted_paper_ids" text[] default ARRAY[]::text[],
    "selected_comparison_ids" text[] default ARRAY[]::text[],
    "active_tab" text default 'matrix'::text,
    "filters" jsonb default '{"sortField": "confidence", "minConfidence": 0.5, "relationTypes": [], "sortDirection": "desc"}'::jsonb,
    "layout" jsonb default '{"papersSectionCollapsed": false, "detailsSectionCollapsed": false}'::jsonb,
    "created_at" timestamp with time zone not null default now(),
    "updated_at" timestamp with time zone not null default now()
);


CREATE UNIQUE INDEX agent_memory_store_pkey ON answer42.agent_memory_store USING btree (key);

CREATE INDEX analysis_results_analysis_type_idx ON answer42.analysis_results USING btree (analysis_type);

CREATE INDEX analysis_results_paper_id_idx ON answer42.analysis_results USING btree (paper_id);

CREATE UNIQUE INDEX analysis_results_pkey ON answer42.analysis_results USING btree (id);

CREATE INDEX analysis_results_user_id_idx ON answer42.analysis_results USING btree (user_id);

CREATE INDEX analysis_tasks_paper_id_idx ON answer42.analysis_tasks USING btree (paper_id);

CREATE UNIQUE INDEX analysis_tasks_pkey ON answer42.analysis_tasks USING btree (id);

CREATE INDEX analysis_tasks_status_idx ON answer42.analysis_tasks USING btree (status);

CREATE INDEX analysis_tasks_user_id_idx ON answer42.analysis_tasks USING btree (user_id);

CREATE UNIQUE INDEX chat_messages_pkey ON answer42.chat_messages USING btree (id);

CREATE INDEX chat_messages_session_id_idx ON answer42.chat_messages USING btree (session_id);

CREATE UNIQUE INDEX chat_sessions_pkey ON answer42.chat_sessions USING btree (id);

CREATE INDEX chat_sessions_user_id_idx ON answer42.chat_sessions USING btree (user_id);

CREATE INDEX citation_verifications_arxiv_id_idx ON answer42.citation_verifications USING btree (arxiv_id);

CREATE INDEX citation_verifications_citation_id_idx ON answer42.citation_verifications USING btree (citation_id);

CREATE INDEX citation_verifications_doi_idx ON answer42.citation_verifications USING btree (doi);

CREATE INDEX citation_verifications_paper_id_idx ON answer42.citation_verifications USING btree (paper_id);

CREATE UNIQUE INDEX citation_verifications_pkey ON answer42.citation_verifications USING btree (id);

CREATE INDEX citation_verifications_semantic_scholar_id_idx ON answer42.citation_verifications USING btree (semantic_scholar_id);

CREATE INDEX citations_paper_id_idx ON answer42.citations USING btree (paper_id);

CREATE UNIQUE INDEX citations_pkey ON answer42.citations USING btree (id);

CREATE INDEX concept_maps_paper_id_idx ON answer42.concept_maps USING btree (paper_id);

CREATE UNIQUE INDEX concept_maps_pkey ON answer42.concept_maps USING btree (id);

CREATE INDEX concept_maps_user_id_idx ON answer42.concept_maps USING btree (user_id);

CREATE UNIQUE INDEX credit_balances_pkey ON answer42.credit_balances USING btree (id);

CREATE INDEX credit_balances_user_id_idx ON answer42.credit_balances USING btree (user_id);

CREATE UNIQUE INDEX credit_packages_pkey ON answer42.credit_packages USING btree (id);

CREATE INDEX credit_transactions_created_at_idx ON answer42.credit_transactions USING btree (created_at);

CREATE UNIQUE INDEX credit_transactions_pkey ON answer42.credit_transactions USING btree (id);

CREATE INDEX credit_transactions_user_id_idx ON answer42.credit_transactions USING btree (user_id);

CREATE UNIQUE INDEX discovered_papers_pkey ON answer42.discovered_papers USING btree (id);

CREATE UNIQUE INDEX discovery_feedback_pkey ON answer42.discovery_feedback USING btree (id);

CREATE UNIQUE INDEX discovery_feedback_user_id_discovered_paper_id_feedback_typ_key ON answer42.discovery_feedback USING btree (user_id, discovered_paper_id, feedback_type);

CREATE UNIQUE INDEX discovery_results_pkey ON answer42.discovery_results USING btree (id);

CREATE INDEX flashcards_paper_id_idx ON answer42.flashcards USING btree (paper_id);

CREATE UNIQUE INDEX flashcards_pkey ON answer42.flashcards USING btree (id);

CREATE INDEX flashcards_user_id_idx ON answer42.flashcards USING btree (user_id);

CREATE INDEX idx_chat_messages_session_created ON answer42.chat_messages USING btree (session_id, created_at DESC);

CREATE INDEX idx_chat_messages_session_sequence ON answer42.chat_messages USING btree (session_id, sequence_number);

CREATE INDEX idx_credit_transactions_user_date ON answer42.credit_transactions USING btree (user_id, created_at DESC);

CREATE INDEX idx_discovered_papers_arxiv_id ON answer42.discovered_papers USING btree (arxiv_id) WHERE (arxiv_id IS NOT NULL);

CREATE INDEX idx_discovered_papers_citation_count ON answer42.discovered_papers USING btree (citation_count DESC);

CREATE INDEX idx_discovered_papers_created_at ON answer42.discovered_papers USING btree (created_at DESC);

CREATE INDEX idx_discovered_papers_data_completeness_score ON answer42.discovered_papers USING btree (data_completeness_score DESC);

CREATE INDEX idx_discovered_papers_discovery_source ON answer42.discovered_papers USING btree (discovery_source);

CREATE INDEX idx_discovered_papers_doi ON answer42.discovered_papers USING btree (doi) WHERE (doi IS NOT NULL);

CREATE INDEX idx_discovered_papers_first_discovered_at ON answer42.discovered_papers USING btree (first_discovered_at DESC);

CREATE INDEX idx_discovered_papers_metadata_gin ON answer42.discovered_papers USING gin (discovery_metadata);

CREATE INDEX idx_discovered_papers_publication_date ON answer42.discovered_papers USING btree (publication_date DESC);

CREATE INDEX idx_discovered_papers_publication_year ON answer42.discovered_papers USING btree (publication_year DESC);

CREATE INDEX idx_discovered_papers_relevance_score ON answer42.discovered_papers USING btree (relevance_score DESC);

CREATE INDEX idx_discovered_papers_semantic_scholar_id ON answer42.discovered_papers USING btree (semantic_scholar_id) WHERE (semantic_scholar_id IS NOT NULL);

CREATE INDEX idx_discovered_papers_source_reliability_score ON answer42.discovered_papers USING btree (source_reliability_score DESC);

CREATE INDEX idx_discovered_papers_title ON answer42.discovered_papers USING gin (to_tsvector('english'::regconfig, (title)::text));

CREATE INDEX idx_discovered_papers_user_source ON answer42.discovered_papers USING btree (user_id, source_paper_id) WHERE (user_id IS NOT NULL);

CREATE INDEX idx_discovery_feedback_created_at ON answer42.discovery_feedback USING btree (created_at DESC);

CREATE INDEX idx_discovery_feedback_discovered_paper ON answer42.discovery_feedback USING btree (discovered_paper_id);

CREATE INDEX idx_discovery_feedback_source_paper ON answer42.discovery_feedback USING btree (source_paper_id);

CREATE INDEX idx_discovery_feedback_type ON answer42.discovery_feedback USING btree (feedback_type);

CREATE INDEX idx_discovery_feedback_user ON answer42.discovery_feedback USING btree (user_id);

CREATE INDEX idx_discovery_results_agent_task ON answer42.discovery_results USING btree (agent_task_id) WHERE (agent_task_id IS NOT NULL);

CREATE INDEX idx_discovery_results_scope ON answer42.discovery_results USING btree (discovery_scope);

CREATE INDEX idx_discovery_results_source_paper ON answer42.discovery_results USING btree (source_paper_id);

CREATE INDEX idx_discovery_results_started_at ON answer42.discovery_results USING btree (started_at DESC);

CREATE INDEX idx_discovery_results_status ON answer42.discovery_results USING btree (status);

CREATE INDEX idx_discovery_results_user ON answer42.discovery_results USING btree (user_id) WHERE (user_id IS NOT NULL);

CREATE INDEX idx_discovery_results_user_status ON answer42.discovery_results USING btree (user_id, status) WHERE (user_id IS NOT NULL);

CREATE INDEX idx_paper_relationships_discovered_paper ON answer42.paper_relationships USING btree (discovered_paper_id);

CREATE INDEX idx_paper_relationships_source_discovery ON answer42.paper_relationships USING btree (source_paper_id, discovery_source);

CREATE INDEX idx_paper_relationships_source_paper ON answer42.paper_relationships USING btree (source_paper_id);

CREATE INDEX idx_paper_relationships_strength ON answer42.paper_relationships USING btree (relationship_strength DESC);

CREATE INDEX idx_paper_relationships_type ON answer42.paper_relationships USING btree (relationship_type);

CREATE INDEX idx_papers_abstract_search ON answer42.papers USING gin (to_tsvector('english'::regconfig, paper_abstract));

CREATE INDEX idx_papers_metadata_gin ON answer42.papers USING gin (metadata);

CREATE INDEX idx_papers_title_search ON answer42.papers USING gin (to_tsvector('english'::regconfig, (title)::text));

CREATE UNIQUE INDEX invoices_pkey ON answer42.invoices USING btree (id);

CREATE INDEX metadata_verifications_paper_id_idx ON answer42.metadata_verifications USING btree (paper_id);

CREATE INDEX metadata_verifications_paper_id_source_idx ON answer42.metadata_verifications USING btree (paper_id, source);

CREATE UNIQUE INDEX metadata_verifications_pkey ON answer42.metadata_verifications USING btree (id);

CREATE INDEX metadata_verifications_source_idx ON answer42.metadata_verifications USING btree (source);

CREATE UNIQUE INDEX mode_operation_mapping_new_pkey ON answer42.mode_operation_mapping USING btree (mode, operation_type);

CREATE INDEX notes_paper_id_idx ON answer42.notes USING btree (paper_id);

CREATE UNIQUE INDEX notes_pkey ON answer42.notes USING btree (id);

CREATE UNIQUE INDEX operation_costs_new_pkey ON answer42.operation_costs USING btree (id);

CREATE UNIQUE INDEX paper_bookmarks_pkey ON answer42.paper_bookmarks USING btree (id);

CREATE INDEX paper_content_paper_id_idx ON answer42.paper_content USING btree (paper_id);

CREATE UNIQUE INDEX paper_content_paper_id_key ON answer42.paper_content USING btree (paper_id);

CREATE UNIQUE INDEX paper_content_pkey ON answer42.paper_content USING btree (id);

CREATE UNIQUE INDEX paper_relationships_pkey ON answer42.paper_relationships USING btree (id);

CREATE UNIQUE INDEX paper_relationships_source_paper_id_discovered_paper_id_rel_key ON answer42.paper_relationships USING btree (source_paper_id, discovered_paper_id, relationship_type, discovery_source);

CREATE INDEX paper_sections_paper_id_idx ON answer42.paper_sections USING btree (paper_id);

CREATE UNIQUE INDEX paper_sections_pkey ON answer42.paper_sections USING btree (id);

CREATE UNIQUE INDEX paper_tags_pkey ON answer42.paper_tags USING btree (paper_id, tag_id);

CREATE UNIQUE INDEX papers_pkey ON answer42.papers USING btree (id);

CREATE UNIQUE INDEX payment_methods_pkey ON answer42.payment_methods USING btree (id);

CREATE INDEX payment_methods_user_id_idx ON answer42.payment_methods USING btree (user_id);

CREATE INDEX practice_questions_paper_id_idx ON answer42.practice_questions USING btree (paper_id);

CREATE UNIQUE INDEX practice_questions_pkey ON answer42.practice_questions USING btree (id);

CREATE INDEX practice_questions_user_id_idx ON answer42.practice_questions USING btree (user_id);

CREATE UNIQUE INDEX project_papers_pkey ON answer42.project_papers USING btree (project_id, paper_id);

CREATE UNIQUE INDEX projects_pkey ON answer42.projects USING btree (id);

CREATE INDEX projects_user_id_idx ON answer42.projects USING btree (user_id);

CREATE UNIQUE INDEX subscription_plans_pkey ON answer42.subscription_plans USING btree (id);

CREATE UNIQUE INDEX subscriptions_pkey ON answer42.subscriptions USING btree (id);

CREATE INDEX subscriptions_user_id_idx ON answer42.subscriptions USING btree (user_id);

CREATE UNIQUE INDEX summaries_pkey ON answer42.summaries USING btree (id);

CREATE UNIQUE INDEX tags_pkey ON answer42.tags USING btree (id);

CREATE INDEX tasks_agent_id_idx ON answer42.tasks USING btree (agent_id);

CREATE UNIQUE INDEX tasks_pkey ON answer42.tasks USING btree (id);

CREATE INDEX tasks_status_idx ON answer42.tasks USING btree (status);

CREATE INDEX tasks_user_id_idx ON answer42.tasks USING btree (user_id);

CREATE UNIQUE INDEX uk8nyh4qb0a7ak578w3lvjv6wuv ON answer42.analysis_tasks USING btree (task_id);

CREATE UNIQUE INDEX ukoq2o1548h8hn2chx62ftb5tf4 ON answer42.paper_bookmarks USING btree (user_id, discovered_paper_id);

CREATE INDEX user_operations_created_at_idx ON answer42.user_operations USING btree (created_at);

CREATE INDEX user_operations_operation_type_idx ON answer42.user_operations USING btree (operation_type);

CREATE UNIQUE INDEX user_operations_pkey ON answer42.user_operations USING btree (id);

CREATE INDEX user_operations_user_id_idx ON answer42.user_operations USING btree (user_id);

CREATE UNIQUE INDEX user_roles_pkey ON answer42.user_roles USING btree (id);

CREATE UNIQUE INDEX user_settings_pkey ON answer42.user_settings USING btree (user_id);

CREATE INDEX user_settings_user_id_idx ON answer42.user_settings USING btree (user_id);

CREATE UNIQUE INDEX users_email_key ON answer42.users USING btree (email);

CREATE UNIQUE INDEX users_pkey ON answer42.users USING btree (id);

CREATE UNIQUE INDEX users_username_key ON answer42.users USING btree (username);

CREATE UNIQUE INDEX visualization_states_pkey ON answer42.visualization_states USING btree (id);

CREATE UNIQUE INDEX visualization_states_session_id_key ON answer42.visualization_states USING btree (session_id);

CREATE INDEX visualization_states_user_id_idx ON answer42.visualization_states USING btree (user_id);

alter table "answer42"."agent_memory_store" add constraint "agent_memory_store_pkey" PRIMARY KEY using index "agent_memory_store_pkey";

alter table "answer42"."analysis_results" add constraint "analysis_results_pkey" PRIMARY KEY using index "analysis_results_pkey";

alter table "answer42"."analysis_tasks" add constraint "analysis_tasks_pkey" PRIMARY KEY using index "analysis_tasks_pkey";

alter table "answer42"."chat_messages" add constraint "chat_messages_pkey" PRIMARY KEY using index "chat_messages_pkey";

alter table "answer42"."chat_sessions" add constraint "chat_sessions_pkey" PRIMARY KEY using index "chat_sessions_pkey";

alter table "answer42"."citation_verifications" add constraint "citation_verifications_pkey" PRIMARY KEY using index "citation_verifications_pkey";

alter table "answer42"."citations" add constraint "citations_pkey" PRIMARY KEY using index "citations_pkey";

alter table "answer42"."concept_maps" add constraint "concept_maps_pkey" PRIMARY KEY using index "concept_maps_pkey";

alter table "answer42"."credit_balances" add constraint "credit_balances_pkey" PRIMARY KEY using index "credit_balances_pkey";

alter table "answer42"."credit_packages" add constraint "credit_packages_pkey" PRIMARY KEY using index "credit_packages_pkey";

alter table "answer42"."credit_transactions" add constraint "credit_transactions_pkey" PRIMARY KEY using index "credit_transactions_pkey";

alter table "answer42"."discovered_papers" add constraint "discovered_papers_pkey" PRIMARY KEY using index "discovered_papers_pkey";

alter table "answer42"."discovery_feedback" add constraint "discovery_feedback_pkey" PRIMARY KEY using index "discovery_feedback_pkey";

alter table "answer42"."discovery_results" add constraint "discovery_results_pkey" PRIMARY KEY using index "discovery_results_pkey";

alter table "answer42"."flashcards" add constraint "flashcards_pkey" PRIMARY KEY using index "flashcards_pkey";

alter table "answer42"."invoices" add constraint "invoices_pkey" PRIMARY KEY using index "invoices_pkey";

alter table "answer42"."metadata_verifications" add constraint "metadata_verifications_pkey" PRIMARY KEY using index "metadata_verifications_pkey";

alter table "answer42"."mode_operation_mapping" add constraint "mode_operation_mapping_new_pkey" PRIMARY KEY using index "mode_operation_mapping_new_pkey";

alter table "answer42"."notes" add constraint "notes_pkey" PRIMARY KEY using index "notes_pkey";

alter table "answer42"."operation_costs" add constraint "operation_costs_new_pkey" PRIMARY KEY using index "operation_costs_new_pkey";

alter table "answer42"."paper_bookmarks" add constraint "paper_bookmarks_pkey" PRIMARY KEY using index "paper_bookmarks_pkey";

alter table "answer42"."paper_content" add constraint "paper_content_pkey" PRIMARY KEY using index "paper_content_pkey";

alter table "answer42"."paper_relationships" add constraint "paper_relationships_pkey" PRIMARY KEY using index "paper_relationships_pkey";

alter table "answer42"."paper_sections" add constraint "paper_sections_pkey" PRIMARY KEY using index "paper_sections_pkey";

alter table "answer42"."paper_tags" add constraint "paper_tags_pkey" PRIMARY KEY using index "paper_tags_pkey";

alter table "answer42"."papers" add constraint "papers_pkey" PRIMARY KEY using index "papers_pkey";

alter table "answer42"."payment_methods" add constraint "payment_methods_pkey" PRIMARY KEY using index "payment_methods_pkey";

alter table "answer42"."practice_questions" add constraint "practice_questions_pkey" PRIMARY KEY using index "practice_questions_pkey";

alter table "answer42"."project_papers" add constraint "project_papers_pkey" PRIMARY KEY using index "project_papers_pkey";

alter table "answer42"."projects" add constraint "projects_pkey" PRIMARY KEY using index "projects_pkey";

alter table "answer42"."subscription_plans" add constraint "subscription_plans_pkey" PRIMARY KEY using index "subscription_plans_pkey";

alter table "answer42"."subscriptions" add constraint "subscriptions_pkey" PRIMARY KEY using index "subscriptions_pkey";

alter table "answer42"."summaries" add constraint "summaries_pkey" PRIMARY KEY using index "summaries_pkey";

alter table "answer42"."tags" add constraint "tags_pkey" PRIMARY KEY using index "tags_pkey";

alter table "answer42"."tasks" add constraint "tasks_pkey" PRIMARY KEY using index "tasks_pkey";

alter table "answer42"."user_operations" add constraint "user_operations_pkey" PRIMARY KEY using index "user_operations_pkey";

alter table "answer42"."user_roles" add constraint "user_roles_pkey" PRIMARY KEY using index "user_roles_pkey";

alter table "answer42"."user_settings" add constraint "user_settings_pkey" PRIMARY KEY using index "user_settings_pkey";

alter table "answer42"."users" add constraint "users_pkey" PRIMARY KEY using index "users_pkey";

alter table "answer42"."visualization_states" add constraint "visualization_states_pkey" PRIMARY KEY using index "visualization_states_pkey";

alter table "answer42"."analysis_results" add constraint "analysis_results_analysis_type_check" CHECK (((analysis_type)::text = ANY (ARRAY[('DEEP_SUMMARY'::character varying)::text, ('METHODOLOGY_ANALYSIS'::character varying)::text, ('RESULTS_INTERPRETATION'::character varying)::text, ('CRITICAL_EVALUATION'::character varying)::text, ('RESEARCH_IMPLICATIONS'::character varying)::text]))) not valid;

alter table "answer42"."analysis_results" validate constraint "analysis_results_analysis_type_check";

alter table "answer42"."analysis_results" add constraint "answer42_analysis_results_paper_id_fkey" FOREIGN KEY (paper_id) REFERENCES answer42.papers(id) not valid;

alter table "answer42"."analysis_results" validate constraint "answer42_analysis_results_paper_id_fkey";

alter table "answer42"."analysis_results" add constraint "answer42_analysis_results_user_id_fkey" FOREIGN KEY (user_id) REFERENCES answer42.users(id) not valid;

alter table "answer42"."analysis_results" validate constraint "answer42_analysis_results_user_id_fkey";

alter table "answer42"."analysis_results" add constraint "fkrh3apk9wf23pk867vknb4imio" FOREIGN KEY (task_id) REFERENCES answer42.analysis_tasks(id) not valid;

alter table "answer42"."analysis_results" validate constraint "fkrh3apk9wf23pk867vknb4imio";

alter table "answer42"."analysis_tasks" add constraint "analysis_tasks_analysis_type_check" CHECK (((analysis_type)::text = ANY (ARRAY[('DEEP_SUMMARY'::character varying)::text, ('METHODOLOGY_ANALYSIS'::character varying)::text, ('RESULTS_INTERPRETATION'::character varying)::text, ('CRITICAL_EVALUATION'::character varying)::text, ('RESEARCH_IMPLICATIONS'::character varying)::text]))) not valid;

alter table "answer42"."analysis_tasks" validate constraint "analysis_tasks_analysis_type_check";

alter table "answer42"."analysis_tasks" add constraint "analysis_tasks_status_check" CHECK (((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('PROCESSING'::character varying)::text, ('COMPLETED'::character varying)::text, ('FAILED'::character varying)::text]))) not valid;

alter table "answer42"."analysis_tasks" validate constraint "analysis_tasks_status_check";

alter table "answer42"."analysis_tasks" add constraint "answer42_analysis_tasks_user_id_fkey" FOREIGN KEY (user_id) REFERENCES answer42.users(id) not valid;

alter table "answer42"."analysis_tasks" validate constraint "answer42_analysis_tasks_user_id_fkey";

alter table "answer42"."analysis_tasks" add constraint "fk8y3vd98ki07xgv5ugx3kjcs3p" FOREIGN KEY (result_id) REFERENCES answer42.analysis_results(id) not valid;

alter table "answer42"."analysis_tasks" validate constraint "fk8y3vd98ki07xgv5ugx3kjcs3p";

alter table "answer42"."analysis_tasks" add constraint "fkd54hw4ga7g5qg7u4tehxsnjhn" FOREIGN KEY (paper_id) REFERENCES answer42.papers(id) not valid;

alter table "answer42"."analysis_tasks" validate constraint "fkd54hw4ga7g5qg7u4tehxsnjhn";

alter table "answer42"."analysis_tasks" add constraint "uk8nyh4qb0a7ak578w3lvjv6wuv" UNIQUE using index "uk8nyh4qb0a7ak578w3lvjv6wuv";

alter table "answer42"."chat_messages" add constraint "answer42_chat_messages_session_id_fkey" FOREIGN KEY (session_id) REFERENCES answer42.chat_sessions(id) not valid;

alter table "answer42"."chat_messages" validate constraint "answer42_chat_messages_session_id_fkey";

alter table "answer42"."chat_messages" add constraint "chat_messages_role_check" CHECK (((role)::text = ANY (ARRAY['user'::text, 'assistant'::text, 'system'::text]))) not valid;

alter table "answer42"."chat_messages" validate constraint "chat_messages_role_check";

alter table "answer42"."chat_sessions" add constraint "answer42_chat_sessions_user_id_fkey" FOREIGN KEY (user_id) REFERENCES answer42.users(id) not valid;

alter table "answer42"."chat_sessions" validate constraint "answer42_chat_sessions_user_id_fkey";

alter table "answer42"."citation_verifications" add constraint "answer42_citation_verifications_citation_id_fkey" FOREIGN KEY (citation_id) REFERENCES answer42.citations(id) not valid;

alter table "answer42"."citation_verifications" validate constraint "answer42_citation_verifications_citation_id_fkey";

alter table "answer42"."credit_balances" add constraint "answer42_credit_balances_user_id_fkey" FOREIGN KEY (user_id) REFERENCES answer42.users(id) not valid;

alter table "answer42"."credit_balances" validate constraint "answer42_credit_balances_user_id_fkey";

alter table "answer42"."credit_balances" add constraint "check_balance_non_negative" CHECK ((balance >= 0)) not valid;

alter table "answer42"."credit_balances" validate constraint "check_balance_non_negative";

alter table "answer42"."credit_balances" add constraint "positive_balance" CHECK ((balance >= 0)) not valid;

alter table "answer42"."credit_balances" validate constraint "positive_balance";

alter table "answer42"."credit_balances" add constraint "positive_used" CHECK ((used_this_period >= 0)) not valid;

alter table "answer42"."credit_balances" validate constraint "positive_used";

alter table "answer42"."credit_packages" add constraint "positive_credits" CHECK ((credits > 0)) not valid;

alter table "answer42"."credit_packages" validate constraint "positive_credits";

alter table "answer42"."credit_packages" add constraint "positive_price" CHECK ((price_usd >= (0)::numeric)) not valid;

alter table "answer42"."credit_packages" validate constraint "positive_price";

alter table "answer42"."credit_transactions" add constraint "answer42_credit_transactions_user_id_fkey" FOREIGN KEY (user_id) REFERENCES answer42.users(id) not valid;

alter table "answer42"."credit_transactions" validate constraint "answer42_credit_transactions_user_id_fkey";

alter table "answer42"."credit_transactions" add constraint "balance_after_non_negative" CHECK ((balance_after >= 0)) not valid;

alter table "answer42"."credit_transactions" validate constraint "balance_after_non_negative";

alter table "answer42"."discovered_papers" add constraint "check_confidence_score_range" CHECK (((confidence_score IS NULL) OR ((confidence_score >= (0.0)::double precision) AND (confidence_score <= (1.0)::double precision)))) not valid;

alter table "answer42"."discovered_papers" validate constraint "check_confidence_score_range";

alter table "answer42"."discovered_papers" add constraint "check_relevance_score_range" CHECK (((relevance_score >= (0.0)::double precision) AND (relevance_score <= (1.0)::double precision))) not valid;

alter table "answer42"."discovered_papers" validate constraint "check_relevance_score_range";

alter table "answer42"."discovered_papers" add constraint "check_user_rating_range" CHECK (((user_rating IS NULL) OR ((user_rating >= 1) AND (user_rating <= 5)))) not valid;

alter table "answer42"."discovered_papers" validate constraint "check_user_rating_range";

alter table "answer42"."discovered_papers" add constraint "discovered_papers_discovery_source_check" CHECK (((discovery_source)::text = ANY (ARRAY[('CROSSREF'::character varying)::text, ('SEMANTIC_SCHOLAR'::character varying)::text, ('PERPLEXITY'::character varying)::text]))) not valid;

alter table "answer42"."discovered_papers" validate constraint "discovered_papers_discovery_source_check";

alter table "answer42"."discovered_papers" add constraint "fkbp99vpbkvgx9mbrsus6j3pxaj" FOREIGN KEY (user_id) REFERENCES answer42.users(id) not valid;

alter table "answer42"."discovered_papers" validate constraint "fkbp99vpbkvgx9mbrsus6j3pxaj";

alter table "answer42"."discovered_papers" add constraint "fkqy4gsrdv4da5bg0m6ppnj22gc" FOREIGN KEY (source_paper_id) REFERENCES answer42.papers(id) not valid;

alter table "answer42"."discovered_papers" validate constraint "fkqy4gsrdv4da5bg0m6ppnj22gc";

alter table "answer42"."discovery_feedback" add constraint "discovery_feedback_discovered_paper_id_fkey" FOREIGN KEY (discovered_paper_id) REFERENCES answer42.discovered_papers(id) ON DELETE CASCADE not valid;

alter table "answer42"."discovery_feedback" validate constraint "discovery_feedback_discovered_paper_id_fkey";

alter table "answer42"."discovery_feedback" add constraint "discovery_feedback_discovery_result_id_fkey" FOREIGN KEY (discovery_result_id) REFERENCES answer42.discovery_results(id) ON DELETE CASCADE not valid;

alter table "answer42"."discovery_feedback" validate constraint "discovery_feedback_discovery_result_id_fkey";

alter table "answer42"."discovery_feedback" add constraint "discovery_feedback_feedback_type_check" CHECK (((feedback_type)::text = ANY (ARRAY[('RELEVANCE_RATING'::character varying)::text, ('USEFUL'::character varying)::text, ('NOT_USEFUL'::character varying)::text, ('ALREADY_KNOWN'::character varying)::text, ('QUALITY_ISSUE'::character varying)::text, ('INCORRECT_METADATA'::character varying)::text, ('SPAM_OR_IRRELEVANT'::character varying)::text]))) not valid;

alter table "answer42"."discovery_feedback" validate constraint "discovery_feedback_feedback_type_check";

alter table "answer42"."discovery_feedback" add constraint "discovery_feedback_quality_rating_check" CHECK (((quality_rating >= 1) AND (quality_rating <= 5))) not valid;

alter table "answer42"."discovery_feedback" validate constraint "discovery_feedback_quality_rating_check";

alter table "answer42"."discovery_feedback" add constraint "discovery_feedback_relevance_rating_check" CHECK (((relevance_rating >= 1) AND (relevance_rating <= 5))) not valid;

alter table "answer42"."discovery_feedback" validate constraint "discovery_feedback_relevance_rating_check";

alter table "answer42"."discovery_feedback" add constraint "discovery_feedback_source_paper_id_fkey" FOREIGN KEY (source_paper_id) REFERENCES answer42.papers(id) ON DELETE CASCADE not valid;

alter table "answer42"."discovery_feedback" validate constraint "discovery_feedback_source_paper_id_fkey";

alter table "answer42"."discovery_feedback" add constraint "discovery_feedback_usefulness_rating_check" CHECK (((usefulness_rating >= 1) AND (usefulness_rating <= 5))) not valid;

alter table "answer42"."discovery_feedback" validate constraint "discovery_feedback_usefulness_rating_check";

alter table "answer42"."discovery_feedback" add constraint "discovery_feedback_user_id_discovered_paper_id_feedback_typ_key" UNIQUE using index "discovery_feedback_user_id_discovered_paper_id_feedback_typ_key";

alter table "answer42"."discovery_feedback" add constraint "discovery_feedback_user_id_fkey" FOREIGN KEY (user_id) REFERENCES answer42.users(id) ON DELETE CASCADE not valid;

alter table "answer42"."discovery_feedback" validate constraint "discovery_feedback_user_id_fkey";

alter table "answer42"."discovery_results" add constraint "discovery_results_discovery_scope_check" CHECK (((discovery_scope)::text = ANY (ARRAY[('QUICK'::character varying)::text, ('STANDARD'::character varying)::text, ('COMPREHENSIVE'::character varying)::text, ('CUSTOM'::character varying)::text]))) not valid;

alter table "answer42"."discovery_results" validate constraint "discovery_results_discovery_scope_check";

alter table "answer42"."discovery_results" add constraint "discovery_results_source_paper_id_fkey" FOREIGN KEY (source_paper_id) REFERENCES answer42.papers(id) ON DELETE CASCADE not valid;

alter table "answer42"."discovery_results" validate constraint "discovery_results_source_paper_id_fkey";

alter table "answer42"."discovery_results" add constraint "discovery_results_status_check" CHECK (((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('RUNNING'::character varying)::text, ('COMPLETED'::character varying)::text, ('FAILED'::character varying)::text, ('CANCELLED'::character varying)::text]))) not valid;

alter table "answer42"."discovery_results" validate constraint "discovery_results_status_check";

alter table "answer42"."discovery_results" add constraint "discovery_results_user_id_fkey" FOREIGN KEY (user_id) REFERENCES answer42.users(id) ON DELETE SET NULL not valid;

alter table "answer42"."discovery_results" validate constraint "discovery_results_user_id_fkey";

alter table "answer42"."invoices" add constraint "answer42_invoices_subscription_id_fkey" FOREIGN KEY (subscription_id) REFERENCES answer42.subscriptions(id) not valid;

alter table "answer42"."invoices" validate constraint "answer42_invoices_subscription_id_fkey";

alter table "answer42"."paper_bookmarks" add constraint "fk43saq2r8j7g0h9nak3ht59qgt" FOREIGN KEY (discovered_paper_id) REFERENCES answer42.discovered_papers(id) not valid;

alter table "answer42"."paper_bookmarks" validate constraint "fk43saq2r8j7g0h9nak3ht59qgt";

alter table "answer42"."paper_bookmarks" add constraint "fkcegcjikjy4n7gqd478p8ipxhx" FOREIGN KEY (user_id) REFERENCES answer42.users(id) not valid;

alter table "answer42"."paper_bookmarks" validate constraint "fkcegcjikjy4n7gqd478p8ipxhx";

alter table "answer42"."paper_bookmarks" add constraint "ukoq2o1548h8hn2chx62ftb5tf4" UNIQUE using index "ukoq2o1548h8hn2chx62ftb5tf4";

alter table "answer42"."paper_content" add constraint "paper_content_paper_id_key" UNIQUE using index "paper_content_paper_id_key";

alter table "answer42"."paper_relationships" add constraint "paper_relationships_confidence_score_check" CHECK (((confidence_score >= (0.0)::double precision) AND (confidence_score <= (1.0)::double precision))) not valid;

alter table "answer42"."paper_relationships" validate constraint "paper_relationships_confidence_score_check";

alter table "answer42"."paper_relationships" add constraint "paper_relationships_discovered_paper_id_fkey" FOREIGN KEY (discovered_paper_id) REFERENCES answer42.discovered_papers(id) ON DELETE CASCADE not valid;

alter table "answer42"."paper_relationships" validate constraint "paper_relationships_discovered_paper_id_fkey";

alter table "answer42"."paper_relationships" add constraint "paper_relationships_discovery_source_check" CHECK (((discovery_source)::text = ANY (ARRAY[('CROSSREF'::character varying)::text, ('SEMANTIC_SCHOLAR'::character varying)::text, ('PERPLEXITY'::character varying)::text]))) not valid;

alter table "answer42"."paper_relationships" validate constraint "paper_relationships_discovery_source_check";

alter table "answer42"."paper_relationships" add constraint "paper_relationships_relationship_strength_check" CHECK (((relationship_strength >= (0.0)::double precision) AND (relationship_strength <= (1.0)::double precision))) not valid;

alter table "answer42"."paper_relationships" validate constraint "paper_relationships_relationship_strength_check";

alter table "answer42"."paper_relationships" add constraint "paper_relationships_relationship_type_check" CHECK (((relationship_type)::text = ANY (ARRAY[('CITES'::character varying)::text, ('CITED_BY'::character varying)::text, ('SEMANTIC_SIMILARITY'::character varying)::text, ('AUTHOR_CONNECTION'::character varying)::text, ('VENUE_SIMILARITY'::character varying)::text, ('TOPIC_SIMILARITY'::character varying)::text, ('METHODOLOGY_SIMILARITY'::character varying)::text, ('TEMPORAL_RELATIONSHIP'::character varying)::text]))) not valid;

alter table "answer42"."paper_relationships" validate constraint "paper_relationships_relationship_type_check";

alter table "answer42"."paper_relationships" add constraint "paper_relationships_source_paper_id_discovered_paper_id_rel_key" UNIQUE using index "paper_relationships_source_paper_id_discovered_paper_id_rel_key";

alter table "answer42"."paper_relationships" add constraint "paper_relationships_source_paper_id_fkey" FOREIGN KEY (source_paper_id) REFERENCES answer42.papers(id) ON DELETE CASCADE not valid;

alter table "answer42"."paper_relationships" validate constraint "paper_relationships_source_paper_id_fkey";

alter table "answer42"."paper_tags" add constraint "answer42_paper_tags_tag_id_fkey" FOREIGN KEY (tag_id) REFERENCES answer42.tags(id) not valid;

alter table "answer42"."paper_tags" validate constraint "answer42_paper_tags_tag_id_fkey";

alter table "answer42"."papers" add constraint "answer42_papers_user_id_fkey" FOREIGN KEY (user_id) REFERENCES answer42.users(id) not valid;

alter table "answer42"."papers" validate constraint "answer42_papers_user_id_fkey";

alter table "answer42"."project_papers" add constraint "answer42_project_papers_project_id_fkey" FOREIGN KEY (project_id) REFERENCES answer42.projects(id) not valid;

alter table "answer42"."project_papers" validate constraint "answer42_project_papers_project_id_fkey";

alter table "answer42"."project_papers" add constraint "fkln7db4qdsycsqalwie85ok1x8" FOREIGN KEY (paper_id) REFERENCES answer42.papers(id) not valid;

alter table "answer42"."project_papers" validate constraint "fkln7db4qdsycsqalwie85ok1x8";

alter table "answer42"."projects" add constraint "fkhswfwa3ga88vxv1pmboss6jhm" FOREIGN KEY (user_id) REFERENCES answer42.users(id) not valid;

alter table "answer42"."projects" validate constraint "fkhswfwa3ga88vxv1pmboss6jhm";

alter table "answer42"."subscriptions" add constraint "fkg41f5iev0mretaqhvepf0lks0" FOREIGN KEY (plan_id) REFERENCES answer42.subscription_plans(id) not valid;

alter table "answer42"."subscriptions" validate constraint "fkg41f5iev0mretaqhvepf0lks0";

alter table "answer42"."subscriptions" add constraint "fkhro52ohfqfbay9774bev0qinr" FOREIGN KEY (user_id) REFERENCES answer42.users(id) not valid;

alter table "answer42"."subscriptions" validate constraint "fkhro52ohfqfbay9774bev0qinr";

alter table "answer42"."user_operations" add constraint "answer42_user_operations_user_id_fkey" FOREIGN KEY (user_id) REFERENCES answer42.users(id) not valid;

alter table "answer42"."user_operations" validate constraint "answer42_user_operations_user_id_fkey";

alter table "answer42"."user_operations" add constraint "check_credit_cost_non_negative" CHECK (((credit_cost IS NULL) OR (credit_cost >= 0))) not valid;

alter table "answer42"."user_operations" validate constraint "check_credit_cost_non_negative";

alter table "answer42"."user_roles" add constraint "fkhfh9dx7w3ubf1co1vdev94g3f" FOREIGN KEY (user_id) REFERENCES answer42.users(id) not valid;

alter table "answer42"."user_roles" validate constraint "fkhfh9dx7w3ubf1co1vdev94g3f";

alter table "answer42"."user_settings" add constraint "answer42_user_settings_user_id_fkey" FOREIGN KEY (user_id) REFERENCES answer42.users(id) not valid;

alter table "answer42"."user_settings" validate constraint "answer42_user_settings_user_id_fkey";

alter table "answer42"."users" add constraint "check_email_format" CHECK (((email)::text ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'::text)) not valid;

alter table "answer42"."users" validate constraint "check_email_format";

alter table "answer42"."users" add constraint "users_email_key" UNIQUE using index "users_email_key";

alter table "answer42"."users" add constraint "users_username_key" UNIQUE using index "users_username_key";

alter table "answer42"."visualization_states" add constraint "answer42_visualization_states_session_id_fkey" FOREIGN KEY (session_id) REFERENCES answer42.chat_sessions(id) not valid;

alter table "answer42"."visualization_states" validate constraint "answer42_visualization_states_session_id_fkey";

alter table "answer42"."visualization_states" add constraint "answer42_visualization_states_user_id_fkey" FOREIGN KEY (user_id) REFERENCES answer42.users(id) not valid;

alter table "answer42"."visualization_states" validate constraint "answer42_visualization_states_user_id_fkey";

alter table "answer42"."visualization_states" add constraint "visualization_states_session_id_key" UNIQUE using index "visualization_states_session_id_key";

set check_function_bodies = off;

CREATE OR REPLACE FUNCTION answer42.add_credits(p_user_id uuid, p_amount integer, p_transaction_type text, p_description text, p_reference_id text)
 RETURNS boolean
 LANGUAGE plpgsql
AS $function$
DECLARE
  v_balance INTEGER;
  v_balance_after INTEGER;
  v_credit_balance_exists BOOLEAN;
BEGIN
  -- Check if amount is positive
  IF p_amount <= 0 THEN
    RAISE EXCEPTION 'Credit amount must be positive';
  END IF;
  
  -- Check if user already has a credit balance
  SELECT EXISTS (
    SELECT 1 FROM answer42.credit_balances WHERE user_id = p_user_id
  ) INTO v_credit_balance_exists;
  
  -- If user doesn't have a credit balance, create one
  IF NOT v_credit_balance_exists THEN
    INSERT INTO answer42.credit_balances (
      user_id,
      balance,
      used_this_period,
      next_reset_date
    ) VALUES (
      p_user_id,
      0, -- Will be updated below
      0,
      date_trunc('month', now()) + interval '1 month'
    );
  END IF;
  
  -- Get the user's current balance
  SELECT balance INTO v_balance
  FROM answer42.credit_balances
  WHERE user_id = p_user_id
  FOR UPDATE;
  
  -- Calculate new balance
  v_balance_after := v_balance + p_amount;
  
  -- Update balance
  UPDATE answer42.credit_balances
  SET 
    balance = v_balance_after,
    updated_at = now()
  WHERE user_id = p_user_id;
  
  -- Record transaction
  INSERT INTO answer42.credit_transactions (
    user_id,
    transaction_type,
    amount,
    balance_after,
    description,
    reference_id
  ) VALUES (
    p_user_id,
    p_transaction_type,
    p_amount,
    v_balance_after,
    p_description,
    p_reference_id
  );
  
  RETURN true;
END;
$function$
;

CREATE OR REPLACE FUNCTION answer42.get_operation_type_from_mode(mode_name text)
 RETURNS text
 LANGUAGE plpgsql
AS $function$
DECLARE
  op_type text;
BEGIN
  SELECT operation_type::text INTO op_type
  FROM answer42.mode_operation_mapping
  WHERE mode = mode_name;
  
  -- If no mapping found, return the input (assuming it's already an operation type)
  RETURN COALESCE(op_type, mode_name);
END;
$function$
;

CREATE OR REPLACE FUNCTION answer42.handle_updated_at()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$function$
;

CREATE OR REPLACE FUNCTION answer42.maintain_single_active_session()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
BEGIN
  -- If new record is active, deactivate all other active sessions for this user
  IF NEW.is_active = true THEN
    UPDATE answer42.active_sessions 
    SET is_active = false
    WHERE user_id = NEW.user_id 
      AND id != NEW.id 
      AND is_active = true;
  END IF;
  RETURN NEW;
END;
$function$
;

CREATE OR REPLACE FUNCTION answer42.reset_and_allocate_credits()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
DECLARE
  v_plan_id TEXT;
  v_base_credits INTEGER;
  v_rollover_limit INTEGER;
  v_current_balance INTEGER;
  v_rollover_amount INTEGER;
  v_description TEXT;
BEGIN
  -- Get the subscription plan details
  SELECT plan_id INTO v_plan_id FROM answer42.subscriptions WHERE user_id = NEW.user_id AND status = 'active' LIMIT 1;
  
  IF v_plan_id IS NOT NULL THEN
    -- Get plan credit allocation and rollover limit
    SELECT base_credits, rollover_limit 
    INTO v_base_credits, v_rollover_limit
    FROM answer42.subscription_plans
    WHERE id = v_plan_id;
    
    -- Get current balance
    SELECT balance INTO v_current_balance
    FROM answer42.credit_balances
    WHERE user_id = NEW.user_id;
    
    -- Calculate rollover amount (capped by rollover limit)
    v_rollover_amount := LEAST(v_current_balance, v_rollover_limit);
    
    -- Set description
    v_description := 'Monthly credit allocation for ' || v_plan_id || ' plan';
    
    -- Reset balance to rollover amount + new allocation
    UPDATE answer42.credit_balances
    SET 
      balance = v_rollover_amount + v_base_credits,
      used_this_period = 0,
      next_reset_date = date_trunc('month', now()) + interval '1 month',
      updated_at = now()
    WHERE user_id = NEW.user_id;
    
    -- Record allocation transaction
    INSERT INTO answer42.credit_transactions (
      user_id,
      transaction_type,
      amount,
      balance_after,
      description,
      reference_id
    ) VALUES (
      NEW.user_id,
      'allocation',
      v_base_credits,
      v_rollover_amount + v_base_credits,
      v_description,
      NEW.id::text
    );
  END IF;
  
  RETURN NEW;
END;
$function$
;

CREATE OR REPLACE FUNCTION answer42.sync_paper_status()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
BEGIN
  IF NEW.status = 'PROCESSED' AND NEW.processing_status != 'COMPLETED' THEN
    NEW.processing_status := 'COMPLETED';
  ELSIF NEW.status = 'PROCESSING' AND NEW.processing_status != 'PARSING' THEN
    NEW.processing_status := 'PARSING';
  ELSIF NEW.status = 'FAILED' AND NEW.processing_status != 'FAILED' THEN
    NEW.processing_status := 'FAILED';
  END IF;
  RETURN NEW;
END;
$function$
;

CREATE OR REPLACE FUNCTION answer42.update_active_sessions_updated_at()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$function$
;

CREATE OR REPLACE FUNCTION answer42.update_session_last_message_timestamp()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
BEGIN
  UPDATE answer42.chat_sessions
  SET last_message_at = NEW.created_at
  WHERE id = NEW.session_id;
  RETURN NEW;
END;
$function$
;

CREATE OR REPLACE FUNCTION answer42.update_user_settings_updated_at()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$function$
;

CREATE TRIGGER update_session_timestamp AFTER INSERT ON answer42.chat_messages FOR EACH ROW EXECUTE FUNCTION answer42.update_session_last_message_timestamp();

CREATE TRIGGER update_user_settings_updated_at_trigger BEFORE UPDATE ON answer42.user_settings FOR EACH ROW EXECUTE FUNCTION answer42.update_user_settings_updated_at();


