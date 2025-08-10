--
-- PostgreSQL database dump
--

-- Dumped from database version 15.8
-- Dumped by pg_dump version 16.8 (Ubuntu 16.8-0ubuntu0.24.04.1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: answer42; Type: SCHEMA; Schema: -; Owner: postgres
--

CREATE SCHEMA answer42;


ALTER SCHEMA answer42 OWNER TO postgres;

--
-- Name: operation_type_upper; Type: TYPE; Schema: answer42; Owner: postgres
--

CREATE TYPE answer42.operation_type_upper AS ENUM (
    'PAPER_UPLOAD',
    'GENERATE_SUMMARY',
    'CONCEPT_EXPLANATION',
    'STUDY_GUIDE_CREATION',
    'QA_SESSION',
    'PERPLEXITY_QUERY',
    'CROSS_REFERENCE_CHAT',
    'PAPER_CHAT',
    'RESEARCH_EXPLORER_CHAT',
    'DEEP_SUMMARY',
    'METHODOLOGY_ANALYSIS',
    'RESULTS_INTERPRETATION',
    'CRITICAL_EVALUATION',
    'RESEARCH_IMPLICATIONS',
    'COMPARISON_MATRIX',
    'VENN_DIAGRAM_ANALYSIS',
    'TIMELINE_ANALYSIS',
    'METRICS_COMPARISON',
    'RESEARCH_EXPLORER_CHAT_P',
    'RESEARCH_EXPLORER_CHAT_C',
    'RESEARCH_EXPLORER',
    'PAPER',
    'PAPER_TEXT_EXTRACTION',
    'METADATA_ENHANCEMENT',
    'CONTENT_SUMMARIZATION',
    'QUALITY_CHECKING',
    'CITATION_FORMATTING',
    'RESEARCH_DISCOVERY',
    'FULL_PIPELINE_PROCESSING',
    'TOKEN_USAGE_TRACKING'
);


ALTER TYPE answer42.operation_type_upper OWNER TO postgres;

--
-- Name: add_credits(uuid, integer, text, text, text); Type: FUNCTION; Schema: answer42; Owner: postgres
--

CREATE FUNCTION answer42.add_credits(p_user_id uuid, p_amount integer, p_transaction_type text, p_description text, p_reference_id text) RETURNS boolean
    LANGUAGE plpgsql
    AS $$
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
$$;


ALTER FUNCTION answer42.add_credits(p_user_id uuid, p_amount integer, p_transaction_type text, p_description text, p_reference_id text) OWNER TO postgres;

--
-- Name: get_operation_type_from_mode(text); Type: FUNCTION; Schema: answer42; Owner: postgres
--

CREATE FUNCTION answer42.get_operation_type_from_mode(mode_name text) RETURNS text
    LANGUAGE plpgsql
    AS $$
DECLARE
  op_type text;
BEGIN
  SELECT operation_type::text INTO op_type
  FROM answer42.mode_operation_mapping
  WHERE mode = mode_name;
  
  -- If no mapping found, return the input (assuming it's already an operation type)
  RETURN COALESCE(op_type, mode_name);
END;
$$;


ALTER FUNCTION answer42.get_operation_type_from_mode(mode_name text) OWNER TO postgres;

--
-- Name: handle_updated_at(); Type: FUNCTION; Schema: answer42; Owner: postgres
--

CREATE FUNCTION answer42.handle_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$;


ALTER FUNCTION answer42.handle_updated_at() OWNER TO postgres;

--
-- Name: maintain_single_active_session(); Type: FUNCTION; Schema: answer42; Owner: postgres
--

CREATE FUNCTION answer42.maintain_single_active_session() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
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
$$;


ALTER FUNCTION answer42.maintain_single_active_session() OWNER TO postgres;

--
-- Name: reset_and_allocate_credits(); Type: FUNCTION; Schema: answer42; Owner: postgres
--

CREATE FUNCTION answer42.reset_and_allocate_credits() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
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
$$;


ALTER FUNCTION answer42.reset_and_allocate_credits() OWNER TO postgres;

--
-- Name: sync_paper_status(); Type: FUNCTION; Schema: answer42; Owner: postgres
--

CREATE FUNCTION answer42.sync_paper_status() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
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
$$;


ALTER FUNCTION answer42.sync_paper_status() OWNER TO postgres;

--
-- Name: update_active_sessions_updated_at(); Type: FUNCTION; Schema: answer42; Owner: postgres
--

CREATE FUNCTION answer42.update_active_sessions_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$;


ALTER FUNCTION answer42.update_active_sessions_updated_at() OWNER TO postgres;

--
-- Name: update_session_last_message_timestamp(); Type: FUNCTION; Schema: answer42; Owner: postgres
--

CREATE FUNCTION answer42.update_session_last_message_timestamp() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  UPDATE answer42.chat_sessions
  SET last_message_at = NEW.created_at
  WHERE id = NEW.session_id;
  RETURN NEW;
END;
$$;


ALTER FUNCTION answer42.update_session_last_message_timestamp() OWNER TO postgres;

--
-- Name: update_user_settings_updated_at(); Type: FUNCTION; Schema: answer42; Owner: postgres
--

CREATE FUNCTION answer42.update_user_settings_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$;


ALTER FUNCTION answer42.update_user_settings_updated_at() OWNER TO postgres;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: agent_memory_store; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.agent_memory_store (
    key character varying(255) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    data jsonb NOT NULL
);


ALTER TABLE answer42.agent_memory_store OWNER TO postgres;

--
-- Name: COLUMN agent_memory_store.key; Type: COMMENT; Schema: answer42; Owner: postgres
--

COMMENT ON COLUMN answer42.agent_memory_store.key IS 'Unique key for the memory entry';


--
-- Name: COLUMN agent_memory_store.created_at; Type: COMMENT; Schema: answer42; Owner: postgres
--

COMMENT ON COLUMN answer42.agent_memory_store.created_at IS 'Time when the memory entry was created';


--
-- Name: COLUMN agent_memory_store.updated_at; Type: COMMENT; Schema: answer42; Owner: postgres
--

COMMENT ON COLUMN answer42.agent_memory_store.updated_at IS 'Time when the memory entry was last updated';


--
-- Name: COLUMN agent_memory_store.data; Type: COMMENT; Schema: answer42; Owner: postgres
--

COMMENT ON COLUMN answer42.agent_memory_store.data IS 'JSON data for the memory entry';


--
-- Name: analysis_results; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.analysis_results (
    id uuid NOT NULL,
    task_id uuid NOT NULL,
    user_id uuid NOT NULL,
    paper_id uuid NOT NULL,
    analysis_type character varying(255) NOT NULL,
    content text NOT NULL,
    sections jsonb DEFAULT '[]'::jsonb,
    citations jsonb DEFAULT '[]'::jsonb,
    visual_elements jsonb DEFAULT '[]'::jsonb,
    metadata jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone NOT NULL,
    is_archived boolean NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT analysis_results_analysis_type_check CHECK (((analysis_type)::text = ANY ((ARRAY['DEEP_SUMMARY'::character varying, 'METHODOLOGY_ANALYSIS'::character varying, 'RESULTS_INTERPRETATION'::character varying, 'CRITICAL_EVALUATION'::character varying, 'RESEARCH_IMPLICATIONS'::character varying])::text[])))
);


ALTER TABLE answer42.analysis_results OWNER TO postgres;

--
-- Name: analysis_tasks; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.analysis_tasks (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    paper_id uuid NOT NULL,
    analysis_type character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    result_id uuid,
    error_message text,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    task_id uuid NOT NULL,
    CONSTRAINT analysis_tasks_analysis_type_check CHECK (((analysis_type)::text = ANY ((ARRAY['DEEP_SUMMARY'::character varying, 'METHODOLOGY_ANALYSIS'::character varying, 'RESULTS_INTERPRETATION'::character varying, 'CRITICAL_EVALUATION'::character varying, 'RESEARCH_IMPLICATIONS'::character varying])::text[]))),
    CONSTRAINT analysis_tasks_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PROCESSING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying])::text[])))
);


ALTER TABLE answer42.analysis_tasks OWNER TO postgres;

--
-- Name: chat_messages; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.chat_messages (
    id uuid DEFAULT extensions.uuid_generate_v4() NOT NULL,
    session_id uuid NOT NULL,
    role character varying(255) NOT NULL,
    content text NOT NULL,
    citations jsonb DEFAULT '[]'::jsonb,
    metadata jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone NOT NULL,
    sequence_number integer DEFAULT 0 NOT NULL,
    message_type character varying(255) DEFAULT 'message'::text NOT NULL,
    is_edited boolean DEFAULT false NOT NULL,
    token_count integer,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT chat_messages_role_check CHECK (((role)::text = ANY (ARRAY['user'::text, 'assistant'::text, 'system'::text])))
);


ALTER TABLE answer42.chat_messages OWNER TO postgres;

--
-- Name: chat_sessions; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.chat_sessions (
    id uuid DEFAULT extensions.uuid_generate_v4() NOT NULL,
    user_id uuid,
    mode character varying(255) DEFAULT 'general'::text NOT NULL,
    context jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    provider character varying(255) NOT NULL,
    title character varying(255)
);


ALTER TABLE answer42.chat_sessions OWNER TO postgres;

--
-- Name: citation_verifications; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.citation_verifications (
    id uuid DEFAULT extensions.uuid_generate_v4() NOT NULL,
    citation_id uuid NOT NULL,
    paper_id uuid NOT NULL,
    doi text,
    verified boolean DEFAULT false,
    verification_source text,
    confidence numeric(3,2) DEFAULT 0,
    verification_date timestamp with time zone DEFAULT now(),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    semantic_scholar_id text,
    arxiv_id text,
    merged_metadata jsonb DEFAULT '{}'::jsonb
);


ALTER TABLE answer42.citation_verifications OWNER TO postgres;

--
-- Name: citations; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.citations (
    id uuid DEFAULT extensions.uuid_generate_v4() NOT NULL,
    paper_id uuid NOT NULL,
    citation_data jsonb NOT NULL,
    raw_text text,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);


ALTER TABLE answer42.citations OWNER TO postgres;

--
-- Name: concept_maps; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.concept_maps (
    id uuid DEFAULT extensions.uuid_generate_v4() NOT NULL,
    paper_id uuid NOT NULL,
    user_id uuid NOT NULL,
    title text NOT NULL,
    description text,
    map_data jsonb NOT NULL,
    image_url text,
    tags text[],
    metadata jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);


ALTER TABLE answer42.concept_maps OWNER TO postgres;

--
-- Name: credit_balances; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.credit_balances (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    balance integer DEFAULT 0 NOT NULL,
    used_this_period integer DEFAULT 0 NOT NULL,
    next_reset_date timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT check_balance_non_negative CHECK ((balance >= 0)),
    CONSTRAINT positive_balance CHECK ((balance >= 0)),
    CONSTRAINT positive_used CHECK ((used_this_period >= 0))
);


ALTER TABLE answer42.credit_balances OWNER TO postgres;

--
-- Name: credit_packages; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.credit_packages (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name text NOT NULL,
    credits integer NOT NULL,
    price_usd numeric(10,2) NOT NULL,
    price_sats bigint,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT positive_credits CHECK ((credits > 0)),
    CONSTRAINT positive_price CHECK ((price_usd >= (0)::numeric))
);


ALTER TABLE answer42.credit_packages OWNER TO postgres;

--
-- Name: credit_transactions; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.credit_transactions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    transaction_type character varying(255) NOT NULL,
    amount integer NOT NULL,
    balance_after integer NOT NULL,
    operation_type character varying(255),
    description character varying(255) NOT NULL,
    reference_id character varying(255),
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT balance_after_non_negative CHECK ((balance_after >= 0))
);


ALTER TABLE answer42.credit_transactions OWNER TO postgres;

--
-- Name: discovered_papers; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.discovered_papers (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    doi character varying(255),
    semantic_scholar_id character varying(100),
    arxiv_id character varying(50),
    pubmed_id character varying(50),
    title character varying(255) NOT NULL,
    authors jsonb DEFAULT '[]'::jsonb NOT NULL,
    abstract text,
    publication_date timestamp with time zone,
    publication_year integer,
    journal character varying(255),
    venue character varying(255),
    publisher character varying(500),
    volume character varying(50),
    issue character varying(50),
    pages character varying(50),
    url text,
    pdf_url character varying(255),
    open_access_pdf_url text,
    is_open_access boolean DEFAULT false,
    citation_count integer DEFAULT 0,
    reference_count integer DEFAULT 0,
    influential_citation_count integer DEFAULT 0,
    relevance_score double precision DEFAULT 0.0,
    source_reliability_score double precision DEFAULT 0.0,
    data_completeness_score double precision DEFAULT 0.0,
    discovery_source character varying(255) NOT NULL,
    discovery_context jsonb,
    crossref_metadata jsonb,
    semantic_scholar_metadata jsonb,
    perplexity_metadata jsonb,
    fields_of_study jsonb DEFAULT '[]'::jsonb,
    subjects jsonb DEFAULT '[]'::jsonb,
    topics jsonb DEFAULT '[]'::jsonb,
    first_discovered_at timestamp with time zone DEFAULT now(),
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    access_url character varying(255),
    confidence_score double precision,
    discovery_metadata jsonb,
    discovery_session_id character varying(255),
    duplicate_of_id uuid,
    external_id character varying(255),
    is_archived boolean,
    is_duplicate boolean,
    is_verified boolean,
    last_accessed_at timestamp with time zone,
    open_access boolean,
    paper_abstract text,
    relationship_type character varying(255) NOT NULL,
    source_specific_data jsonb,
    user_notes text,
    user_rating integer,
    venue_type character varying(255),
    verification_score double precision,
    year integer,
    source_paper_id uuid,
    user_id uuid,
    CONSTRAINT check_confidence_score_range CHECK (((confidence_score IS NULL) OR ((confidence_score >= (0.0)::double precision) AND (confidence_score <= (1.0)::double precision)))),
    CONSTRAINT check_relevance_score_range CHECK (((relevance_score >= (0.0)::double precision) AND (relevance_score <= (1.0)::double precision))),
    CONSTRAINT check_user_rating_range CHECK (((user_rating IS NULL) OR ((user_rating >= 1) AND (user_rating <= 5)))),
    CONSTRAINT discovered_papers_discovery_source_check CHECK (((discovery_source)::text = ANY (ARRAY[('CROSSREF'::character varying)::text, ('SEMANTIC_SCHOLAR'::character varying)::text, ('PERPLEXITY'::character varying)::text])))
);


ALTER TABLE answer42.discovered_papers OWNER TO postgres;

--
-- Name: TABLE discovered_papers; Type: COMMENT; Schema: answer42; Owner: postgres
--

COMMENT ON TABLE answer42.discovered_papers IS 'Papers discovered through various sources - cleaned up redundant timestamps';


--
-- Name: discovery_feedback; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.discovery_feedback (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    discovery_result_id uuid NOT NULL,
    discovered_paper_id uuid NOT NULL,
    source_paper_id uuid NOT NULL,
    feedback_type character varying(255) NOT NULL,
    relevance_rating integer,
    quality_rating integer,
    usefulness_rating integer,
    feedback_text character varying(255),
    feedback_context jsonb,
    clicked_paper boolean DEFAULT false,
    downloaded_paper boolean DEFAULT false,
    bookmarked_paper boolean DEFAULT false,
    shared_paper boolean DEFAULT false,
    time_spent_viewing_seconds integer DEFAULT 0,
    ip_address character varying(255),
    user_agent character varying(255),
    session_id character varying(255),
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT discovery_feedback_feedback_type_check CHECK (((feedback_type)::text = ANY (ARRAY[('RELEVANCE_RATING'::character varying)::text, ('USEFUL'::character varying)::text, ('NOT_USEFUL'::character varying)::text, ('ALREADY_KNOWN'::character varying)::text, ('QUALITY_ISSUE'::character varying)::text, ('INCORRECT_METADATA'::character varying)::text, ('SPAM_OR_IRRELEVANT'::character varying)::text]))),
    CONSTRAINT discovery_feedback_quality_rating_check CHECK (((quality_rating >= 1) AND (quality_rating <= 5))),
    CONSTRAINT discovery_feedback_relevance_rating_check CHECK (((relevance_rating >= 1) AND (relevance_rating <= 5))),
    CONSTRAINT discovery_feedback_usefulness_rating_check CHECK (((usefulness_rating >= 1) AND (usefulness_rating <= 5)))
);


ALTER TABLE answer42.discovery_feedback OWNER TO postgres;

--
-- Name: discovery_results; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.discovery_results (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    source_paper_id uuid NOT NULL,
    user_id uuid NOT NULL,
    discovery_configuration jsonb NOT NULL,
    discovery_scope character varying(50) NOT NULL,
    status character varying(50) DEFAULT 'PENDING'::character varying NOT NULL,
    started_at timestamp with time zone NOT NULL,
    completed_at timestamp with time zone,
    execution_duration_ms bigint,
    total_papers_discovered integer DEFAULT 0,
    crossref_papers_count integer DEFAULT 0,
    semantic_scholar_papers_count integer DEFAULT 0,
    perplexity_papers_count integer DEFAULT 0,
    average_relevance_score double precision DEFAULT 0.0,
    average_quality_score double precision DEFAULT 0.0,
    high_confidence_papers_count integer DEFAULT 0,
    crossref_success boolean DEFAULT false,
    semantic_scholar_success boolean DEFAULT false,
    perplexity_success boolean DEFAULT false,
    error_message text,
    error_details jsonb,
    processing_metadata jsonb,
    api_usage_stats jsonb,
    agent_task_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    ai_synthesis_enabled boolean,
    ai_synthesis_time_ms bigint,
    api_calls_made integer,
    cache_hit_count integer,
    cache_miss_count integer,
    configuration_name character varying(100) NOT NULL,
    crossref_papers_found integer,
    discovery_end_time timestamp(6) with time zone,
    discovery_start_time timestamp(6) with time zone NOT NULL,
    discovery_statistics text,
    errors text,
    overall_confidence_score double precision,
    perplexity_papers_found integer,
    requires_user_review boolean,
    semantic_scholar_papers_found integer,
    sources_used text,
    total_papers_after_filtering integer,
    total_processing_time_ms bigint,
    warnings text,
    CONSTRAINT discovery_results_discovery_scope_check CHECK (((discovery_scope)::text = ANY ((ARRAY['QUICK'::character varying, 'STANDARD'::character varying, 'COMPREHENSIVE'::character varying, 'CUSTOM'::character varying])::text[]))),
    CONSTRAINT discovery_results_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'RUNNING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying, 'CANCELLED'::character varying])::text[])))
);


ALTER TABLE answer42.discovery_results OWNER TO postgres;

--
-- Name: flashcards; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.flashcards (
    id uuid DEFAULT extensions.uuid_generate_v4() NOT NULL,
    paper_id uuid NOT NULL,
    user_id uuid NOT NULL,
    question text NOT NULL,
    answer text NOT NULL,
    section text,
    tags text[],
    difficulty text,
    last_reviewed timestamp with time zone,
    review_count integer DEFAULT 0,
    metadata jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);


ALTER TABLE answer42.flashcards OWNER TO postgres;

--
-- Name: invoices; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.invoices (
    id uuid DEFAULT extensions.uuid_generate_v4() NOT NULL,
    subscription_id uuid NOT NULL,
    amount numeric(10,2) NOT NULL,
    currency text NOT NULL,
    status text NOT NULL,
    invoice_url text,
    paid_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL
);


ALTER TABLE answer42.invoices OWNER TO postgres;

--
-- Name: metadata_verifications; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.metadata_verifications (
    id uuid DEFAULT extensions.uuid_generate_v4() NOT NULL,
    paper_id uuid NOT NULL,
    source text NOT NULL,
    confidence numeric(3,2) DEFAULT 0,
    metadata jsonb,
    matched_by text,
    identifier_used text,
    verified_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);


ALTER TABLE answer42.metadata_verifications OWNER TO postgres;

--
-- Name: mode_operation_mapping; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.mode_operation_mapping (
    mode text NOT NULL,
    operation_type answer42.operation_type_upper NOT NULL
);


ALTER TABLE answer42.mode_operation_mapping OWNER TO postgres;

--
-- Name: notes; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.notes (
    id uuid DEFAULT extensions.uuid_generate_v4() NOT NULL,
    paper_id uuid NOT NULL,
    user_id uuid NOT NULL,
    content text NOT NULL,
    location jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);


ALTER TABLE answer42.notes OWNER TO postgres;

--
-- Name: operation_costs; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.operation_costs (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    operation_type answer42.operation_type_upper NOT NULL,
    basic_cost integer NOT NULL,
    pro_cost integer NOT NULL,
    description text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    scholar_cost integer
);


ALTER TABLE answer42.operation_costs OWNER TO postgres;

--
-- Name: paper_bookmarks; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.paper_bookmarks (
    id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    notes character varying(1000),
    tags character varying(500),
    updated_at timestamp with time zone,
    discovered_paper_id uuid NOT NULL,
    user_id uuid NOT NULL
);


ALTER TABLE answer42.paper_bookmarks OWNER TO postgres;

--
-- Name: paper_content; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.paper_content (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    paper_id uuid NOT NULL,
    content text NOT NULL,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);


ALTER TABLE answer42.paper_content OWNER TO postgres;

--
-- Name: paper_relationships; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.paper_relationships (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    source_paper_id uuid NOT NULL,
    discovered_paper_id uuid NOT NULL,
    relationship_type character varying(50) NOT NULL,
    relationship_strength double precision DEFAULT 0.0,
    confidence_score double precision DEFAULT 0.0,
    discovery_source character varying(50) NOT NULL,
    discovery_method character varying(100),
    discovery_context jsonb,
    evidence jsonb,
    reasoning text,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    CONSTRAINT paper_relationships_confidence_score_check CHECK (((confidence_score >= (0.0)::double precision) AND (confidence_score <= (1.0)::double precision))),
    CONSTRAINT paper_relationships_discovery_source_check CHECK (((discovery_source)::text = ANY ((ARRAY['CROSSREF'::character varying, 'SEMANTIC_SCHOLAR'::character varying, 'PERPLEXITY'::character varying])::text[]))),
    CONSTRAINT paper_relationships_relationship_strength_check CHECK (((relationship_strength >= (0.0)::double precision) AND (relationship_strength <= (1.0)::double precision))),
    CONSTRAINT paper_relationships_relationship_type_check CHECK (((relationship_type)::text = ANY ((ARRAY['CITES'::character varying, 'CITED_BY'::character varying, 'SEMANTIC_SIMILARITY'::character varying, 'AUTHOR_CONNECTION'::character varying, 'VENUE_SIMILARITY'::character varying, 'TOPIC_SIMILARITY'::character varying, 'METHODOLOGY_SIMILARITY'::character varying, 'TEMPORAL_RELATIONSHIP'::character varying])::text[])))
);


ALTER TABLE answer42.paper_relationships OWNER TO postgres;

--
-- Name: paper_sections; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.paper_sections (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    paper_id uuid NOT NULL,
    title text NOT NULL,
    content text NOT NULL,
    index integer NOT NULL,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);


ALTER TABLE answer42.paper_sections OWNER TO postgres;

--
-- Name: paper_tags; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.paper_tags (
    paper_id uuid NOT NULL,
    tag_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now()
);


ALTER TABLE answer42.paper_tags OWNER TO postgres;

--
-- Name: papers; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.papers (
    crossref_score double precision,
    crossref_verified boolean,
    is_public boolean,
    metadata_confidence double precision,
    publication_date date,
    quality_score double precision,
    references_count integer,
    semantic_scholar_score double precision,
    semantic_scholar_verified boolean,
    year integer,
    created_at timestamp(6) with time zone,
    crossref_last_verified timestamp(6) with time zone,
    file_size bigint,
    semantic_scholar_last_verified timestamp(6) with time zone,
    updated_at timestamp(6) with time zone,
    id uuid NOT NULL,
    user_id uuid,
    paper_abstract text,
    arxiv_id character varying(255),
    crossref_doi character varying(255),
    doi character varying(255),
    file_path character varying(255) NOT NULL,
    file_type character varying(255),
    journal character varying(255),
    metadata_source character varying(255),
    processing_status character varying(255),
    semantic_scholar_id character varying(255),
    status character varying(255),
    summary_brief text,
    summary_detailed text,
    summary_standard text,
    text_content text,
    title character varying(255) NOT NULL,
    authors jsonb NOT NULL,
    citations jsonb,
    crossref_metadata jsonb,
    glossary jsonb,
    key_findings jsonb,
    main_concepts jsonb,
    metadata jsonb,
    metadata_source_details jsonb,
    methodology_details jsonb,
    quality_feedback jsonb,
    research_questions jsonb,
    semantic_scholar_metadata jsonb,
    topics jsonb,
    glossart jsonb
);


ALTER TABLE answer42.papers OWNER TO postgres;

--
-- Name: payment_methods; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.payment_methods (
    id uuid DEFAULT extensions.uuid_generate_v4() NOT NULL,
    user_id uuid,
    provider text NOT NULL,
    type text NOT NULL,
    last_four text,
    is_default boolean DEFAULT false,
    provider_data jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);


ALTER TABLE answer42.payment_methods OWNER TO postgres;

--
-- Name: practice_questions; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.practice_questions (
    id uuid DEFAULT extensions.uuid_generate_v4() NOT NULL,
    paper_id uuid,
    user_id uuid,
    question_text text NOT NULL,
    question_type text NOT NULL,
    correct_answer text NOT NULL,
    options jsonb,
    explanation text,
    difficulty text,
    section text,
    tags text[],
    metadata jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);


ALTER TABLE answer42.practice_questions OWNER TO postgres;

--
-- Name: project_papers; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.project_papers (
    project_id uuid NOT NULL,
    paper_id uuid NOT NULL,
    "order" integer DEFAULT 0,
    added_at timestamp with time zone DEFAULT now()
);


ALTER TABLE answer42.project_papers OWNER TO postgres;

--
-- Name: projects; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.projects (
    id uuid DEFAULT extensions.uuid_generate_v4() NOT NULL,
    user_id uuid,
    name character varying(255) NOT NULL,
    description character varying(255),
    settings jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    is_public boolean DEFAULT false
);


ALTER TABLE answer42.projects OWNER TO postgres;

--
-- Name: subscription_plans; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.subscription_plans (
    id character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    description character varying(255),
    price_monthly numeric(38,2) NOT NULL,
    price_annually numeric(38,2) NOT NULL,
    features jsonb DEFAULT '{}'::jsonb,
    base_credits integer DEFAULT 0 NOT NULL,
    rollover_limit integer DEFAULT 0 NOT NULL,
    is_active boolean DEFAULT true,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    is_free boolean DEFAULT false,
    default_ai_tier character varying(255)
);


ALTER TABLE answer42.subscription_plans OWNER TO postgres;

--
-- Name: subscriptions; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.subscriptions (
    id uuid DEFAULT extensions.uuid_generate_v4() NOT NULL,
    user_id uuid,
    plan_id character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    current_period_start timestamp with time zone,
    current_period_end timestamp with time zone,
    payment_provider character varying(255) NOT NULL,
    payment_provider_id character varying(255),
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);


ALTER TABLE answer42.subscriptions OWNER TO postgres;

--
-- Name: summaries; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.summaries (
    id uuid DEFAULT extensions.uuid_generate_v4() NOT NULL,
    paper_id uuid,
    brief text,
    standard text,
    detailed text,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    content text
);


ALTER TABLE answer42.summaries OWNER TO postgres;

--
-- Name: tags; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.tags (
    id uuid DEFAULT extensions.uuid_generate_v4() NOT NULL,
    user_id uuid,
    name text NOT NULL,
    color text DEFAULT '#6B7280'::text,
    created_at timestamp with time zone DEFAULT now()
);


ALTER TABLE answer42.tags OWNER TO postgres;

--
-- Name: tasks; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.tasks (
    id character varying(255) NOT NULL,
    agent_id character varying(255) NOT NULL,
    user_id uuid NOT NULL,
    input jsonb NOT NULL,
    status character varying(255) NOT NULL,
    error character varying(255),
    result jsonb,
    created_at timestamp with time zone DEFAULT now(),
    started_at timestamp with time zone,
    completed_at timestamp with time zone
);


ALTER TABLE answer42.tasks OWNER TO postgres;

--
-- Name: COLUMN tasks.id; Type: COMMENT; Schema: answer42; Owner: postgres
--

COMMENT ON COLUMN answer42.tasks.id IS 'Unique ID for the task';


--
-- Name: COLUMN tasks.agent_id; Type: COMMENT; Schema: answer42; Owner: postgres
--

COMMENT ON COLUMN answer42.tasks.agent_id IS 'ID of the agent handling this task';


--
-- Name: COLUMN tasks.user_id; Type: COMMENT; Schema: answer42; Owner: postgres
--

COMMENT ON COLUMN answer42.tasks.user_id IS 'ID of the user who initiated the task';


--
-- Name: COLUMN tasks.input; Type: COMMENT; Schema: answer42; Owner: postgres
--

COMMENT ON COLUMN answer42.tasks.input IS 'Input data for the task';


--
-- Name: COLUMN tasks.status; Type: COMMENT; Schema: answer42; Owner: postgres
--

COMMENT ON COLUMN answer42.tasks.status IS 'Current status of the task (pending, processing, completed, failed)';


--
-- Name: COLUMN tasks.error; Type: COMMENT; Schema: answer42; Owner: postgres
--

COMMENT ON COLUMN answer42.tasks.error IS 'Error message if task failed';


--
-- Name: COLUMN tasks.result; Type: COMMENT; Schema: answer42; Owner: postgres
--

COMMENT ON COLUMN answer42.tasks.result IS 'Result data from the task execution';


--
-- Name: COLUMN tasks.created_at; Type: COMMENT; Schema: answer42; Owner: postgres
--

COMMENT ON COLUMN answer42.tasks.created_at IS 'Time when the task was created';


--
-- Name: COLUMN tasks.started_at; Type: COMMENT; Schema: answer42; Owner: postgres
--

COMMENT ON COLUMN answer42.tasks.started_at IS 'Time when the task started processing';


--
-- Name: COLUMN tasks.completed_at; Type: COMMENT; Schema: answer42; Owner: postgres
--

COMMENT ON COLUMN answer42.tasks.completed_at IS 'Time when the task was completed';


--
-- Name: timeline_relationships; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.timeline_relationships (
    id uuid DEFAULT extensions.uuid_generate_v4() NOT NULL,
    source uuid NOT NULL,
    target uuid NOT NULL,
    type public.timeline_relationship_type NOT NULL,
    description text,
    created_at timestamp with time zone DEFAULT now()
);


ALTER TABLE answer42.timeline_relationships OWNER TO postgres;

--
-- Name: user_operations; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.user_operations (
    id uuid DEFAULT extensions.uuid_generate_v4() NOT NULL,
    user_id uuid NOT NULL,
    operation_type text NOT NULL,
    created_at timestamp with time zone DEFAULT now(),
    metadata jsonb DEFAULT '{}'::jsonb,
    reference_id text,
    ai_tier text,
    credit_cost integer,
    CONSTRAINT check_credit_cost_non_negative CHECK (((credit_cost IS NULL) OR (credit_cost >= 0)))
);


ALTER TABLE answer42.user_operations OWNER TO postgres;

--
-- Name: user_roles; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.user_roles (
    user_id uuid NOT NULL,
    role character varying(255),
    id uuid DEFAULT gen_random_uuid() NOT NULL
);


ALTER TABLE answer42.user_roles OWNER TO postgres;

--
-- Name: TABLE user_roles; Type: COMMENT; Schema: answer42; Owner: postgres
--

COMMENT ON TABLE answer42.user_roles IS 'User role assignments with proper primary key';


--
-- Name: COLUMN user_roles.id; Type: COMMENT; Schema: answer42; Owner: postgres
--

COMMENT ON COLUMN answer42.user_roles.id IS 'Primary key for user role assignments';


--
-- Name: user_settings; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.user_settings (
    user_id uuid NOT NULL,
    auto_generate_study_materials boolean DEFAULT true,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    email_notifications boolean DEFAULT true,
    system_notifications boolean DEFAULT true,
    id uuid NOT NULL,
    academic_field character varying(255),
    email_notifications_enabled boolean NOT NULL,
    study_material_generation_enabled boolean NOT NULL,
    system_notifications_enabled boolean NOT NULL,
    openai_api_key character varying(255),
    perplexity_api_key character varying(255),
    anthropic_api_key character varying(255)
);


ALTER TABLE answer42.user_settings OWNER TO postgres;

--
-- Name: COLUMN user_settings.auto_generate_study_materials; Type: COMMENT; Schema: answer42; Owner: postgres
--

COMMENT ON COLUMN answer42.user_settings.auto_generate_study_materials IS 'Controls whether study materials (flashcards, practice questions, concept maps) are automatically generated when processing papers';


--
-- Name: users; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.users (
    enabled boolean NOT NULL,
    id uuid NOT NULL,
    email character varying(255),
    password character varying(255) NOT NULL,
    username character varying(255) NOT NULL,
    created_at timestamp with time zone,
    last_login timestamp with time zone,
    CONSTRAINT check_email_format CHECK (((email)::text ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'::text))
);


ALTER TABLE answer42.users OWNER TO postgres;

--
-- Name: visualization_states; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.visualization_states (
    id uuid DEFAULT extensions.uuid_generate_v4() NOT NULL,
    session_id uuid NOT NULL,
    user_id uuid NOT NULL,
    highlighted_paper_ids text[] DEFAULT ARRAY[]::text[],
    selected_comparison_ids text[] DEFAULT ARRAY[]::text[],
    active_tab text DEFAULT 'matrix'::text,
    filters jsonb DEFAULT '{"sortField": "confidence", "minConfidence": 0.5, "relationTypes": [], "sortDirection": "desc"}'::jsonb,
    layout jsonb DEFAULT '{"papersSectionCollapsed": false, "detailsSectionCollapsed": false}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE answer42.visualization_states OWNER TO postgres;

--
-- Data for Name: agent_memory_store; Type: TABLE DATA; Schema: answer42; Owner: postgres
--

COPY answer42.agent_memory_store (key, created_at, updated_at, data) FROM stdin;
\.


--
-- Data for Name: analysis_results; Type: TABLE DATA; Schema: answer42; Owner: postgres
--

COPY answer42.analysis_results (id, task_id, user_id, paper_id, analysis_type, content, sections, citations, visual_elements, metadata, created_at, is_archived, updated_at) FROM stdin;
\.


--
-- Data for Name: analysis_tasks; Type: TABLE DATA; Schema: answer42; Owner: postgres
--

COPY answer42.analysis_tasks (id, user_id, paper_id, analysis_type, status, result_id, error_message, created_at, updated_at, task_id) FROM stdin;
\.


--
-- Data for Name: chat_messages; Type: TABLE DATA; Schema: answer42; Owner: postgres
--

COPY answer42.chat_messages (id, session_id, role, content, citations, metadata, created_at, sequence_number, message_type, is_edited, token_count, updated_at) FROM stdin;
\.


--
-- Data for Name: chat_sessions; Type: TABLE DATA; Schema: answer42; Owner: postgres
--

COPY answer42.chat_sessions (id, user_id, mode, context, created_at, updated_at, provider, title) FROM stdin;
\.


--
-- Data for Name: citation_verifications; Type: TABLE DATA; Schema: answer42; Owner: postgres
--

COPY answer42.citation_verifications (id, citation_id, paper_id, doi, verified, verification_source, confidence, verification_date, created_at, updated_at, semantic_scholar_id, arxiv_id, merged_metadata) FROM stdin;
\.


--
-- Data for Name: citations; Type: TABLE DATA; Schema: answer42; Owner: postgres
--

COPY answer42.citations (id, paper_id, citation_data, raw_text, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: concept_maps; Type: TABLE DATA; Schema: answer42; Owner: postgres
--

COPY answer42.concept_maps (id, paper_id, user_id, title, description, map_data, image_url, tags, metadata, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: credit_balances; Type: TABLE DATA; Schema: answer42; Owner: postgres
--

COPY answer42.credit_balances (id, user_id, balance, used_this_period, next_reset_date, created_at, updated_at) FROM stdin;
f3fdd180-d073-4467-abc7-4ad73dd15796	bcd60249-0a7e-449e-9e46-af7aa8993c16	1100	0	2025-06-18 00:00:00+00	2025-05-18 08:12:07.602498+00	2025-05-20 01:31:13.541136+00
\.


--
-- Data for Name: credit_packages; Type: TABLE DATA; Schema: answer42; Owner: postgres
--

COPY answer42.credit_packages (id, name, credits, price_usd, price_sats, is_active, created_at, updated_at) FROM stdin;
64015492-5889-4dcb-b895-0c6b382fc48d	50 Credits	50	5.99	599000	t	2025-04-09 21:55:44.6327+00	2025-04-09 21:55:44.6327+00
a7e13a97-866e-46e1-ae9d-b06af490f4ce	100 Credits	100	9.99	999000	t	2025-04-09 21:55:44.6327+00	2025-04-09 21:55:44.6327+00
54bcbc43-e30b-4579-ad9f-975f47503983	500 Credits	500	39.99	3999000	t	2025-04-09 21:55:44.6327+00	2025-04-09 21:55:44.6327+00
\.


--
-- Data for Name: credit_transactions; Type: TABLE DATA; Schema: answer42; Owner: postgres
--

COPY answer42.credit_transactions (id, user_id, transaction_type, amount, balance_after, operation_type, description, reference_id, created_at) FROM stdin;
59d886dd-4407-46ef-9541-65e6f4f57cbc	bcd60249-0a7e-449e-9e46-af7aa8993c16	PURCHASE	1000	1100	\N	Credit package purchase	\N	2025-05-20 01:31:13.531138+00
\.


--
-- Data for Name: discovered_papers; Type: TABLE DATA; Schema: answer42; Owner: postgres
--

COPY answer42.discovered_papers (id, doi, semantic_scholar_id, arxiv_id, pubmed_id, title, authors, abstract, publication_date, publication_year, journal, venue, publisher, volume, issue, pages, url, pdf_url, open_access_pdf_url, is_open_access, citation_count, reference_count, influential_citation_count, relevance_score, source_reliability_score, data_completeness_score, discovery_source, discovery_context, crossref_metadata, semantic_scholar_metadata, perplexity_metadata, fields_of_study, subjects, topics, first_discovered_at, created_at, updated_at, access_url, confidence_score, discovery_metadata, discovery_session_id, duplicate_of_id, external_id, is_archived, is_duplicate, is_verified, last_accessed_at, open_access, paper_abstract, relationship_type, source_specific_data, user_notes, user_rating, venue_type, verification_score, year, source_paper_id, user_id) FROM stdin;
\.


--
-- Data for Name: discovery_feedback; Type: TABLE DATA; Schema: answer42; Owner: postgres
--

COPY answer42.discovery_feedback (id, user_id, discovery_result_id, discovered_paper_id, source_paper_id, feedback_type, relevance_rating, quality_rating, usefulness_rating, feedback_text, feedback_context, clicked_paper, downloaded_paper, bookmarked_paper, shared_paper, time_spent_viewing_seconds, ip_address, user_agent, session_id, created_at) FROM stdin;
\.


--
-- Data for Name: discovery_results; Type: TABLE DATA; Schema: answer42; Owner: postgres
--

COPY answer42.discovery_results (id, source_paper_id, user_id, discovery_configuration, discovery_scope, status, started_at, completed_at, execution_duration_ms, total_papers_discovered, crossref_papers_count, semantic_scholar_papers_count, perplexity_papers_count, average_relevance_score, average_quality_score, high_confidence_papers_count, crossref_success, semantic_scholar_success, perplexity_success, error_message, error_details, processing_metadata, api_usage_stats, agent_task_id, created_at, updated_at, ai_synthesis_enabled, ai_synthesis_time_ms, api_calls_made, cache_hit_count, cache_miss_count, configuration_name, crossref_papers_found, discovery_end_time, discovery_start_time, discovery_statistics, errors, overall_confidence_score, perplexity_papers_found, requires_user_review, semantic_scholar_papers_found, sources_used, total_papers_after_filtering, total_processing_time_ms, warnings) FROM stdin;
\.


--
-- Data for Name: flashcards; Type: TABLE DATA; Schema: answer42; Owner: postgres
--

COPY answer42.flashcards (id, paper_id, user_id, question, answer, section, tags, difficulty, last_reviewed, review_count, metadata, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: invoices; Type: TABLE DATA; Schema: answer42; Owner: postgres
--

COPY answer42.invoices (id, subscription_id, amount, currency, status, invoice_url, paid_at, created_at) FROM stdin;
\.


--
-- Data for Name: metadata_verifications; Type: TABLE DATA; Schema: answer42; Owner: postgres
--

COPY answer42.metadata_verifications (id, paper_id, source, confidence, metadata, matched_by, identifier_used, verified_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: mode_operation_mapping; Type: TABLE DATA; Schema: answer42; Owner: postgres
--

COPY answer42.mode_operation_mapping (mode, operation_type) FROM stdin;
PAPER	PAPER_CHAT
CROSS_REFERENCE	CROSS_REFERENCE_CHAT
RESEARCH_EXPLORER	RESEARCH_EXPLORER_CHAT
\.


--
-- Data for Name: notes; Type: TABLE DATA; Schema: answer42; Owner: postgres
--

COPY answer42.notes (id, paper_id, user_id, content, location, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: operation_costs; Type: TABLE DATA; Schema: answer42; Owner: postgres
--

COPY answer42.operation_costs (id, operation_type, basic_cost, pro_cost, description, created_at, updated_at, scholar_cost) FROM stdin;
7923ef80-0f00-4cb9-b7e4-86d1c2757f1b	PAPER_CHAT	3	5	Chat with AI about specific papers (using Claude-3-Sonnet)	2025-04-16 18:38:31.279736+00	2025-04-16 18:38:31.279736+00	7
ff092a0a-9525-4d76-ba87-707b55a0b6fe	DEEP_SUMMARY	5	8	In-depth paper summary using Claude-3-Opus	2025-04-17 06:56:30.822571+00	2025-04-17 06:56:30.822571+00	10
5ac643a6-718c-495d-8b08-ef57fb1d54b8	METHODOLOGY_ANALYSIS	6	9	Detailed analysis of research methodology using Claude-3-Opus	2025-04-17 06:56:30.822571+00	2025-04-17 06:56:30.822571+00	12
7c62986c-80ce-455c-a040-88a77c5a2e27	RESULTS_INTERPRETATION	6	9	Interpretation of research results using Claude-3-Opus	2025-04-17 06:56:30.822571+00	2025-04-17 06:56:30.822571+00	12
69d11516-c892-4339-a031-28d08a28d16b	CRITICAL_EVALUATION	7	10	Critical evaluation of paper strength and weaknesses using Claude-3-Opus	2025-04-17 06:56:30.822571+00	2025-04-17 06:56:30.822571+00	14
6ff24ed5-a556-4c81-89c4-d63e33369cd1	RESEARCH_IMPLICATIONS	5	8	Analysis of research implications and future directions using Claude-3-Opus	2025-04-17 06:56:30.822571+00	2025-04-17 06:56:30.822571+00	10
ebba37cc-1bb5-4969-be5d-d8e2c791bcc1	CROSS_REFERENCE_CHAT	5	8	Cross-reference multiple papers using Claude-3-Sonnet	2025-04-12 20:07:00.570435+00	2025-04-12 20:07:00.570435+00	12
7c3db79d-d985-4f62-ad9c-f31c82a541d7	RESEARCH_EXPLORER_CHAT	5	7	Research explorer with external sources using Claude-3-Sonnet	2025-04-16 18:38:31.279736+00	2025-04-16 18:38:31.279736+00	10
dd7d5fb0-91ca-4946-a7f1-8f53f2839e85	GENERATE_SUMMARY	2	4	Generate paper summary	2025-04-09 21:55:44.6327+00	2025-04-09 21:55:44.6327+00	6
e19ad625-d425-463f-81ef-25ceac466923	CONCEPT_EXPLANATION	3	5	Concept explanation	2025-04-09 21:55:44.6327+00	2025-04-09 21:55:44.6327+00	7
d82d86ee-0fd9-41a3-ab93-d6182896f013	STUDY_GUIDE_CREATION	5	8	Study guide creation	2025-04-09 21:55:44.6327+00	2025-04-09 21:55:44.6327+00	10
0fc59e0b-4d22-4949-9739-902844267fbe	QA_SESSION	5	7	Q&A session (5 questions)	2025-04-09 21:55:44.6327+00	2025-04-09 21:55:44.6327+00	9
982335a7-58e9-4713-ab84-22772f01db92	PERPLEXITY_QUERY	3	5	Perplexity research query	2025-04-09 21:55:44.6327+00	2025-04-09 21:55:44.6327+00	7
c32b6a2d-37a5-403f-9b5a-78d0b4ec8bb8	PAPER_UPLOAD	5	8	Paper upload and processing (5MB)	2025-04-09 21:55:44.6327+00	2025-04-09 21:55:44.6327+00	10
b35ef7fb-a036-4192-bfcc-a7deb7373cc1	COMPARISON_MATRIX	3	5	Generate comparison matrix visualization between papers	2025-04-17 06:56:30.822571+00	2025-04-17 06:56:30.822571+00	7
6ce4b04a-11f2-46b2-96d9-26a0555c1af9	VENN_DIAGRAM_ANALYSIS	3	5	Generate Venn diagram analysis of paper concepts	2025-04-17 06:56:30.822571+00	2025-04-17 06:56:30.822571+00	7
9beb103b-6ffc-4193-ae7b-4d8a4c0e7ac4	TIMELINE_ANALYSIS	4	6	Generate timeline analysis showing paper relationships	2025-04-17 06:56:30.822571+00	2025-04-17 06:56:30.822571+00	8
bfa992e6-d1e8-4dba-97f0-89f48373ba44	METRICS_COMPARISON	3	5	Quantitative metrics comparison between papers	2025-04-17 06:56:30.822571+00	2025-04-17 06:56:30.822571+00	7
9042e0a1-4021-4c07-b38e-7895546b3e73	RESEARCH_EXPLORER_CHAT_P	5	8	Research explorer with Perplexity discovery mode	2025-04-19 05:12:15.129764+00	2025-04-19 05:12:15.129764+00	12
27fd2ab5-45d2-47a5-9f1f-e396ba7a8872	RESEARCH_EXPLORER_CHAT_C	3	5	Research explorer with ChatGPT integration mode	2025-04-19 05:12:15.129764+00	2025-04-19 05:12:15.129764+00	7
e2c2752b-ddf8-437a-8c8b-62233e4f8644	PAPER_TEXT_EXTRACTION	3	5	Extract and structure text from PDF using AI	2025-05-22 23:59:25.604235+00	2025-05-22 23:59:25.604235+00	7
a035c577-9c91-4d94-a189-332ac283e53f	METADATA_ENHANCEMENT	2	3	Enhance paper metadata with external APIs	2025-05-22 23:59:25.604235+00	2025-05-22 23:59:25.604235+00	4
46d99f0f-0349-49b3-9ad8-f010557707e0	CONTENT_SUMMARIZATION	4	6	Multi-level content summarization	2025-05-22 23:59:25.604235+00	2025-05-22 23:59:25.604235+00	8
da88b891-0648-4bef-9798-16b55bf41ceb	QUALITY_CHECKING	4	6	AI-powered quality assessment and validation	2025-05-22 23:59:25.604235+00	2025-05-22 23:59:25.604235+00	8
183c684d-5e29-4471-b467-a04575999663	CITATION_FORMATTING	2	3	Extract and format citations in multiple styles	2025-05-22 23:59:25.604235+00	2025-05-22 23:59:25.604235+00	4
491e2002-3571-48eb-8ece-9fb28ca8ac8b	RESEARCH_DISCOVERY	5	7	Discover related papers and research trends	2025-05-22 23:59:25.604235+00	2025-05-22 23:59:25.604235+00	10
8eea5dda-a96d-40e2-888b-ccc3021a9bc7	FULL_PIPELINE_PROCESSING	15	22	Complete multi-agent paper processing pipeline	2025-05-22 23:59:25.604235+00	2025-05-22 23:59:25.604235+00	30
abc1f575-0cc3-437f-a041-22bb05b07129	TOKEN_USAGE_TRACKING	0	0	Token usage measurement (no additional cost)	2025-05-22 23:59:25.604235+00	2025-05-22 23:59:25.604235+00	0
\.


--
-- Data for Name: paper_bookmarks; Type: TABLE DATA; Schema: answer42; Owner: postgres
--

COPY answer42.paper_bookmarks (id, created_at, notes, tags, updated_at, discovered_paper_id, user_id) FROM stdin;
\.


--
-- Data for Name: paper_content; Type: TABLE DATA; Schema: answer42; Owner: postgres
--

COPY answer42.paper_content (id, paper_id, content, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: paper_relationships; Type: TABLE DATA; Schema: answer42; Owner: postgres
--

COPY answer42.paper_relationships (id, source_paper_id, discovered_paper_id, relationship_type, relationship_strength, confidence_score, discovery_source, discovery_method, discovery_context, evidence, reasoning, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: paper_sections; Type: TABLE DATA; Schema: answer42; Owner: postgres
--

COPY answer42.paper_sections (id, paper_id, title, content, index, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: paper_tags; Type: TABLE DATA; Schema: answer42; Owner: postgres
--

COPY answer42.paper_tags (paper_id, tag_id, created_at) FROM stdin;
\.


--
-- Data for Name: papers; Type: TABLE DATA; Schema: answer42; Owner: postgres
--

COPY answer42.papers (crossref_score, crossref_verified, is_public, metadata_confidence, publication_date, quality_score, references_count, semantic_scholar_score, semantic_scholar_verified, year, created_at, crossref_last_verified, file_size, semantic_scholar_last_verified, updated_at, id, user_id, paper_abstract, arxiv_id, crossref_doi, doi, file_path, file_type, journal, metadata_source, processing_status, semantic_scholar_id, status, summary_brief, summary_detailed, summary_standard, text_content, title, authors, citations, crossref_metadata, glossary, key_findings, main_concepts, metadata, metadata_source_details, methodology_details, quality_feedback, research_questions, semantic_scholar_metadata, topics, glossart) FROM stdin;
\N	\N	t	\N	\N	\N	\N	\N	\N	2024	2025-05-16 21:52:09.65518+00	\N	\N	\N	2025-05-16 21:52:09.65518+00	6fa0bae6-20d3-47a7-b929-f0d8cc86fc45	bcd60249-0a7e-449e-9e46-af7aa8993c16	\N	\N	\N	\N	papers/sample/neural_networks_drug_discovery.pdf	\N	Journal of Medicinal Chemistry	\N	COMPLETED	\N	PROCESSED	\N	\N	\N	This research explores the application of neural networks in accelerating drug discovery processes, with a focus on protein folding prediction and molecular docking simulations.	Neural Networks in Drug Discovery	["Maria Garcia", "David Kim"]	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N
\N	\N	t	\N	\N	\N	\N	\N	\N	2025	2025-05-16 21:52:09.65518+00	\N	\N	\N	2025-05-16 21:52:09.65518+00	13280d05-1a8f-4e6d-adba-03c5b1d79a9f	bcd60249-0a7e-449e-9e46-af7aa8993c16	\N	\N	\N	\N	papers/sample/quantum_computing_review.pdf	\N	Journal of Quantum Information	\N	COMPLETED	\N	PROCESSED	\N	\N	\N	This paper provides a comprehensive review of recent advances in quantum computing, including quantum supremacy experiments, error correction techniques, and applications in cryptography.	Advances in Quantum Computing: A Review	["James Smith", "Emily Johnson"]	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N
\N	\N	t	\N	\N	\N	\N	\N	\N	2025	2025-05-16 21:52:09.65518+00	\N	\N	\N	2025-05-16 21:52:09.65518+00	a3e296c5-9a85-4e44-81d7-6870a83080ba	bcd60249-0a7e-449e-9e46-af7aa8993c16	\N	\N	\N	\N	papers/sample/ai_ethics.pdf	\N	AI Ethics	\N	COMPLETED	\N	PROCESSED	\N	\N	\N	We discuss the ethical implications of advanced artificial intelligence systems, focusing on bias, transparency, accountability, and the potential socioeconomic impacts of widespread AI adoption.	Ethical Considerations in Artificial Intelligence	["Thomas Wright", "Ling Zhou"]	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N
\N	\N	t	\N	\N	\N	\N	\N	\N	2024	2025-05-16 21:52:09.65518+00	\N	\N	\N	2025-05-16 21:52:09.65518+00	94ecb27a-d5fa-4d65-8885-e622b83a54d5	bcd60249-0a7e-449e-9e46-af7aa8993c16	\N	\N	\N	\N	papers/sample/iot_cybersecurity.pdf	\N	IEEE Transactions on Cybersecurity	\N	COMPLETED	\N	PROCESSED	\N	\N	\N	This study analyzes security vulnerabilities in IoT devices and proposes a lightweight encryption protocol suitable for resource-constrained environments with minimal energy consumption.	Cybersecurity in Internet of Things Devices	["Jessica Brown", "Hiroshi Tanaka"]	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N
\N	\N	t	\N	\N	\N	\N	\N	\N	2025	2025-05-16 21:52:09.65518+00	\N	\N	\N	2025-05-16 21:52:09.65518+00	667f68d6-6617-4ca5-bb4b-dcfa66484fa6	bcd60249-0a7e-449e-9e46-af7aa8993c16	\N	\N	\N	\N	papers/sample/neuroplasticity_language.pdf	\N	Cognitive Neuroscience	\N	COMPLETED	\N	PROCESSED	\N	\N	\N	Our research investigates the relationship between neuroplasticity and second language acquisition in adults, using functional MRI to identify neural adaptations during intensive language learning.	Neuroplasticity and Language Acquisition	["Michael Foster", "Sophia Chen"]	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N
\N	\N	t	\N	\N	\N	\N	\N	\N	2025	2025-05-19 01:33:51.233963+00	\N	\N	\N	2025-05-19 01:33:51.233963+00	81f4a8d2-3e7c-4da9-b53a-c28f7d21c123	bcd60249-0a7e-449e-9e46-af7aa8993c16	\N	\N	\N	\N	papers/sample/climate_ml.pdf	\N	Environmental Science & Technology	\N	COMPLETED	\N	PROCESSED	\N	\N	\N	This paper explores applications of machine learning models in predicting climate change patterns and extreme weather events.	Machine Learning for Climate Change Prediction	["Robert Chen", "Sarah Williams"]	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N
\N	\N	t	\N	\N	\N	\N	\N	\N	2024	2025-05-19 01:34:03.738958+00	\N	\N	\N	2025-05-19 01:34:03.738958+00	b2c9e57f-8103-4d21-ae94-1f45d926a331	bcd60249-0a7e-449e-9e46-af7aa8993c16	\N	\N	\N	\N	papers/sample/blockchain_healthcare.pdf	\N	Journal of Medical Informatics	\N	COMPLETED	\N	PROCESSED	\N	\N	\N	We present a framework for secure healthcare data management using blockchain technology with a focus on patient privacy and data integrity.	Blockchain in Healthcare Data Management	["Patricia Miller", "Jason Garcia"]	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N
\N	\N	t	\N	\N	\N	\N	\N	\N	2025	2025-05-19 01:34:19.049441+00	\N	\N	\N	2025-05-19 01:34:19.049441+00	d3a7f892-4e6b-4c9a-953e-6fb12c8da471	bcd60249-0a7e-449e-9e46-af7aa8993c16	\N	\N	\N	\N	papers/sample/battery_tech.pdf	\N	Advanced Materials	\N	COMPLETED	\N	PROCESSED	\N	\N	\N	A comprehensive investigation of novel materials for high-capacity batteries with improved charging efficiency and reduced environmental impact.	Next-Generation Batteries: Materials and Efficiency	["Andrew Thompson", "Liu Wei"]	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N
\N	\N	t	\N	\N	\N	\N	\N	\N	2024	2025-05-19 01:34:57.657018+00	\N	\N	\N	2025-05-19 01:34:57.657018+00	9c546b89-6f92-47eb-9375-ba05c69e2b2c	bcd60249-0a7e-449e-9e46-af7aa8993c16	\N	\N	\N	\N	papers/sample/microplastics.pdf	\N	Marine Pollution Bulletin	\N	COMPLETED	\N	PROCESSED	\N	\N	\N	This research documents the presence and impact of microplastics in diverse marine ecosystems, with implications for biodiversity and food safety.	Microplastics in Marine Ecosystems	["Jennifer Lee", "Carlos Rodriguez"]	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N
\N	\N	t	\N	\N	\N	\N	\N	\N	2025	\N	\N	\N	\N	\N	626fa889-36bb-4069-a4f3-e5b126aa4f5e	bcd60249-0a7e-449e-9e46-af7aa8993c16	\N	\N	\N	\N	papers/sample/ar_education.pdf	\N	Educational Technology Research	\N	COMPLETED	\N	PROCESSED	\N	\N	\N	Examining the effectiveness of augmented reality applications in K-12 STEM education.	Augmented Reality in Education	["David Parker", "Maria Sanchez"]	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N
\N	\N	t	\N	\N	\N	\N	\N	\N	2025	\N	\N	\N	\N	\N	96429bc9-3d8c-45c8-b4bd-0ce2eaacb791	bcd60249-0a7e-449e-9e46-af7aa8993c16	\N	\N	\N	\N	papers/sample/urban_planning.pdf	\N	Journal of Urban Planning	\N	COMPLETED	\N	PROCESSED	\N	\N	\N	This study analyzes sustainable urban planning initiatives across global cities, focusing on green infrastructure.	Sustainable Urban Planning Strategies	["Emma Wilson", "Jamal Ahmed"]	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N
\N	\N	t	\N	\N	\N	\N	\N	\N	2024	\N	\N	\N	\N	2025-05-19 23:07:22.427148+00	8c795d1c-940c-4ce7-b666-b331f9faf3c0	bcd60249-0a7e-449e-9e46-af7aa8993c16	CRISPR-Cas9 applications in crop improvement.	\N	\N		papers/sample/crispr_crops.pdf	\N	Test-Plant Biotechnology Journal	\N	COMPLETED	\N	PROCESSED	\N	\N	\N	This paper reviews applications of CRISPR-Cas9 for crop improvement.	CRISPR-Cas9 Applications in Crop Improvement	["Rachel Kim", "Mark Johnson"]	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N	\N
\.


--
-- Data for Name: payment_methods; Type: TABLE DATA; Schema: answer42; Owner: postgres
--

COPY answer42.payment_methods (id, user_id, provider, type, last_four, is_default, provider_data, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: practice_questions; Type: TABLE DATA; Schema: answer42; Owner: postgres
--

COPY answer42.practice_questions (id, paper_id, user_id, question_text, question_type, correct_answer, options, explanation, difficulty, section, tags, metadata, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: project_papers; Type: TABLE DATA; Schema: answer42; Owner: postgres
--

COPY answer42.project_papers (project_id, paper_id, "order", added_at) FROM stdin;
f4be04c8-3397-4ebf-8378-134aa802ea7c	13280d05-1a8f-4e6d-adba-03c5b1d79a9f	1	2025-05-16 21:53:15.758766+00
f4be04c8-3397-4ebf-8378-134aa802ea7c	667f68d6-6617-4ca5-bb4b-dcfa66484fa6	2	2025-05-16 21:53:15.758766+00
f4be04c8-3397-4ebf-8378-134aa802ea7c	6fa0bae6-20d3-47a7-b929-f0d8cc86fc45	3	2025-05-16 21:53:15.758766+00
9f84361b-03a7-4f27-8046-cf6d96072d21	667f68d6-6617-4ca5-bb4b-dcfa66484fa6	1	2025-05-16 21:53:15.758766+00
9f84361b-03a7-4f27-8046-cf6d96072d21	94ecb27a-d5fa-4d65-8885-e622b83a54d5	2	2025-05-16 21:53:15.758766+00
d53d9638-be8f-4a36-b7a2-3e54c0d024b7	94ecb27a-d5fa-4d65-8885-e622b83a54d5	1	2025-05-16 21:53:15.758766+00
d53d9638-be8f-4a36-b7a2-3e54c0d024b7	a3e296c5-9a85-4e44-81d7-6870a83080ba	2	2025-05-16 21:53:15.758766+00
d349ba86-b528-4987-90a4-3a6b3c690e8f	13280d05-1a8f-4e6d-adba-03c5b1d79a9f	0	2025-05-19 23:48:09.907852+00
\.


--
-- Data for Name: projects; Type: TABLE DATA; Schema: answer42; Owner: postgres
--

COPY answer42.projects (id, user_id, name, description, settings, created_at, updated_at, is_public) FROM stdin;
d53d9638-be8f-4a36-b7a2-3e54c0d024b7	bcd60249-0a7e-449e-9e46-af7aa8993c16	Cybersecurity Research	Studies on cybersecurity and data protection methods	{"theme": "dark", "sortOrder": "custom", "showAbstracts": true}	2025-05-16 21:52:26.285314+00	2025-05-16 21:52:26.285314+00	t
f4be04c8-3397-4ebf-8378-134aa802ea7c	bcd60249-0a7e-449e-9e46-af7aa8993c16	AI Ethics Research	A collection of papers related to AI ethics and responsible development	{"theme": "dark", "sortOrder": "newest", "showAbstracts": true}	2025-05-16 21:52:26.285314+00	2025-05-19 23:41:57.865543+00	t
9f84361b-03a7-4f27-8046-cf6d96072d21	bcd60249-0a7e-449e-9e46-af7aa8993c16	Quantum Computing	Latest research on quantum computing and its applications	{"theme": "light", "sortOrder": "alphabetical", "showAbstracts": false}	2025-05-16 21:52:26.285314+00	2025-05-19 23:47:39.557014+00	f
d349ba86-b528-4987-90a4-3a6b3c690e8f	bcd60249-0a7e-449e-9e46-af7aa8993c16	test project	test desc	{"createdTimestamp": "2025-05-19T16:48:09.891723244-07:00[America/Vancouver]", "lastModifiedTimestamp": "2025-05-19T16:48:09.891752488-07:00[America/Vancouver]"}	2025-05-19 23:48:09.891705+00	2025-05-19 23:48:14.338173+00	t
\.


--
-- Data for Name: subscription_plans; Type: TABLE DATA; Schema: answer42; Owner: postgres
--

COPY answer42.subscription_plans (id, name, description, price_monthly, price_annually, features, base_credits, rollover_limit, is_active, created_at, updated_at, is_free, default_ai_tier) FROM stdin;
free	Free	Basic access with limited features	0.00	0.00	{"aiTier": "basic", "support": "community", "maxProjects": 1, "monthlyCredits": 10, "creditsRollover": 0, "maxPaperUploads": 3}	10	0	t	2025-04-09 21:57:32.801329+00	2025-04-09 21:57:32.801329+00	t	FREE
basic	Basic	Full access with standard AI	19.99	199.99	{"aiTier": "basic", "support": "email", "maxProjects": 5, "monthlyCredits": 100, "creditsRollover": 50, "maxPaperUploads": 50}	100	50	t	2025-04-09 21:57:32.801329+00	2025-04-09 21:57:32.801329+00	f	BASIC
pro	Pro	Full access with premium AI	29.99	299.99	{"aiTier": "pro", "support": "priority", "maxProjects": 20, "monthlyCredits": 200, "creditsRollover": 100, "maxPaperUploads": 500}	200	100	t	2025-04-09 21:57:32.801329+00	2025-04-09 21:57:32.801329+00	f	PRO
scholar	Scholar	Academic plan with advanced features	39.99	399.99	{"aiTier": "scholar", "support": "priority", "maxProjects": 100, "monthlyCredits": 300, "creditsRollover": 150, "maxPaperUploads": -1}	300	150	t	2025-04-09 21:57:32.801329+00	2025-04-09 21:57:32.801329+00	f	SCHOLAR
\.


--
-- Data for Name: subscriptions; Type: TABLE DATA; Schema: answer42; Owner: postgres
--

COPY answer42.subscriptions (id, user_id, plan_id, status, current_period_start, current_period_end, payment_provider, payment_provider_id, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: summaries; Type: TABLE DATA; Schema: answer42; Owner: postgres
--

COPY answer42.summaries (id, paper_id, brief, standard, detailed, created_at, updated_at, content) FROM stdin;
\.


--
-- Data for Name: tags; Type: TABLE DATA; Schema: answer42; Owner: postgres
--

COPY answer42.tags (id, user_id, name, color, created_at) FROM stdin;
\.


--
-- Data for Name: tasks; Type: TABLE DATA; Schema: answer42; Owner: postgres
--

COPY answer42.tasks (id, agent_id, user_id, input, status, error, result, created_at, started_at, completed_at) FROM stdin;
\.


--
-- Data for Name: timeline_relationships; Type: TABLE DATA; Schema: answer42; Owner: postgres
--

COPY answer42.timeline_relationships (id, source, target, type, description, created_at) FROM stdin;
\.


--
-- Data for Name: user_operations; Type: TABLE DATA; Schema: answer42; Owner: postgres
--

COPY answer42.user_operations (id, user_id, operation_type, created_at, metadata, reference_id, ai_tier, credit_cost) FROM stdin;
\.


--
-- Data for Name: user_roles; Type: TABLE DATA; Schema: answer42; Owner: postgres
--

COPY answer42.user_roles (user_id, role, id) FROM stdin;
bcd60249-0a7e-449e-9e46-af7aa8993c16	ROLE_USER	3d183897-3d96-47ac-bd45-9aab4e7080fd
\.


--
-- Data for Name: user_settings; Type: TABLE DATA; Schema: answer42; Owner: postgres
--

COPY answer42.user_settings (user_id, auto_generate_study_materials, created_at, updated_at, email_notifications, system_notifications, id, academic_field, email_notifications_enabled, study_material_generation_enabled, system_notifications_enabled, openai_api_key, perplexity_api_key, anthropic_api_key) FROM stdin;
bcd60249-0a7e-449e-9e46-af7aa8993c16	t	2025-05-18 20:21:20.45859+00	2025-05-18 20:21:20.458607+00	t	t	5bb9712b-9d87-4df9-bf1d-3604aa476a40	\N	t	f	t	\N	\N	\N
\.


--
-- Data for Name: users; Type: TABLE DATA; Schema: answer42; Owner: postgres
--

COPY answer42.users (enabled, id, email, password, username, created_at, last_login) FROM stdin;
t	bcd60249-0a7e-449e-9e46-af7aa8993c16	shawn@samjdtechnologies.com	$2a$10$uxcUOogzXePDqOO8Jyj.Ee7NpCsOA7I2.KnFUhGNZp2j0h6CmaNIS	shawn	2025-04-07 00:00:00+00	2025-05-24 03:44:25.399856+00
\.


--
-- Data for Name: visualization_states; Type: TABLE DATA; Schema: answer42; Owner: postgres
--

COPY answer42.visualization_states (id, session_id, user_id, highlighted_paper_ids, selected_comparison_ids, active_tab, filters, layout, created_at, updated_at) FROM stdin;
\.


--
-- Name: agent_memory_store agent_memory_store_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.agent_memory_store
    ADD CONSTRAINT agent_memory_store_pkey PRIMARY KEY (key);


--
-- Name: analysis_results analysis_results_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.analysis_results
    ADD CONSTRAINT analysis_results_pkey PRIMARY KEY (id);


--
-- Name: analysis_tasks analysis_tasks_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.analysis_tasks
    ADD CONSTRAINT analysis_tasks_pkey PRIMARY KEY (id);


--
-- Name: chat_messages chat_messages_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.chat_messages
    ADD CONSTRAINT chat_messages_pkey PRIMARY KEY (id);


--
-- Name: chat_sessions chat_sessions_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.chat_sessions
    ADD CONSTRAINT chat_sessions_pkey PRIMARY KEY (id);


--
-- Name: citation_verifications citation_verifications_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.citation_verifications
    ADD CONSTRAINT citation_verifications_pkey PRIMARY KEY (id);


--
-- Name: citations citations_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.citations
    ADD CONSTRAINT citations_pkey PRIMARY KEY (id);


--
-- Name: concept_maps concept_maps_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.concept_maps
    ADD CONSTRAINT concept_maps_pkey PRIMARY KEY (id);


--
-- Name: credit_balances credit_balances_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.credit_balances
    ADD CONSTRAINT credit_balances_pkey PRIMARY KEY (id);


--
-- Name: credit_packages credit_packages_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.credit_packages
    ADD CONSTRAINT credit_packages_pkey PRIMARY KEY (id);


--
-- Name: credit_transactions credit_transactions_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.credit_transactions
    ADD CONSTRAINT credit_transactions_pkey PRIMARY KEY (id);


--
-- Name: discovered_papers discovered_papers_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.discovered_papers
    ADD CONSTRAINT discovered_papers_pkey PRIMARY KEY (id);


--
-- Name: discovery_feedback discovery_feedback_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.discovery_feedback
    ADD CONSTRAINT discovery_feedback_pkey PRIMARY KEY (id);


--
-- Name: discovery_feedback discovery_feedback_user_id_discovered_paper_id_feedback_typ_key; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.discovery_feedback
    ADD CONSTRAINT discovery_feedback_user_id_discovered_paper_id_feedback_typ_key UNIQUE (user_id, discovered_paper_id, feedback_type);


--
-- Name: discovery_results discovery_results_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.discovery_results
    ADD CONSTRAINT discovery_results_pkey PRIMARY KEY (id);


--
-- Name: flashcards flashcards_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.flashcards
    ADD CONSTRAINT flashcards_pkey PRIMARY KEY (id);


--
-- Name: invoices invoices_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.invoices
    ADD CONSTRAINT invoices_pkey PRIMARY KEY (id);


--
-- Name: metadata_verifications metadata_verifications_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.metadata_verifications
    ADD CONSTRAINT metadata_verifications_pkey PRIMARY KEY (id);


--
-- Name: mode_operation_mapping mode_operation_mapping_new_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.mode_operation_mapping
    ADD CONSTRAINT mode_operation_mapping_new_pkey PRIMARY KEY (mode, operation_type);


--
-- Name: notes notes_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.notes
    ADD CONSTRAINT notes_pkey PRIMARY KEY (id);


--
-- Name: operation_costs operation_costs_new_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.operation_costs
    ADD CONSTRAINT operation_costs_new_pkey PRIMARY KEY (id);


--
-- Name: paper_bookmarks paper_bookmarks_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.paper_bookmarks
    ADD CONSTRAINT paper_bookmarks_pkey PRIMARY KEY (id);


--
-- Name: paper_content paper_content_paper_id_key; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.paper_content
    ADD CONSTRAINT paper_content_paper_id_key UNIQUE (paper_id);


--
-- Name: paper_content paper_content_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.paper_content
    ADD CONSTRAINT paper_content_pkey PRIMARY KEY (id);


--
-- Name: paper_relationships paper_relationships_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.paper_relationships
    ADD CONSTRAINT paper_relationships_pkey PRIMARY KEY (id);


--
-- Name: paper_relationships paper_relationships_source_paper_id_discovered_paper_id_rel_key; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.paper_relationships
    ADD CONSTRAINT paper_relationships_source_paper_id_discovered_paper_id_rel_key UNIQUE (source_paper_id, discovered_paper_id, relationship_type, discovery_source);


--
-- Name: paper_sections paper_sections_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.paper_sections
    ADD CONSTRAINT paper_sections_pkey PRIMARY KEY (id);


--
-- Name: paper_tags paper_tags_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.paper_tags
    ADD CONSTRAINT paper_tags_pkey PRIMARY KEY (paper_id, tag_id);


--
-- Name: papers papers_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.papers
    ADD CONSTRAINT papers_pkey PRIMARY KEY (id);


--
-- Name: payment_methods payment_methods_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.payment_methods
    ADD CONSTRAINT payment_methods_pkey PRIMARY KEY (id);


--
-- Name: practice_questions practice_questions_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.practice_questions
    ADD CONSTRAINT practice_questions_pkey PRIMARY KEY (id);


--
-- Name: project_papers project_papers_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.project_papers
    ADD CONSTRAINT project_papers_pkey PRIMARY KEY (project_id, paper_id);


--
-- Name: projects projects_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.projects
    ADD CONSTRAINT projects_pkey PRIMARY KEY (id);


--
-- Name: subscription_plans subscription_plans_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.subscription_plans
    ADD CONSTRAINT subscription_plans_pkey PRIMARY KEY (id);


--
-- Name: subscriptions subscriptions_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.subscriptions
    ADD CONSTRAINT subscriptions_pkey PRIMARY KEY (id);


--
-- Name: summaries summaries_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.summaries
    ADD CONSTRAINT summaries_pkey PRIMARY KEY (id);


--
-- Name: tags tags_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.tags
    ADD CONSTRAINT tags_pkey PRIMARY KEY (id);


--
-- Name: tasks tasks_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.tasks
    ADD CONSTRAINT tasks_pkey PRIMARY KEY (id);


--
-- Name: timeline_relationships timeline_relationships_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.timeline_relationships
    ADD CONSTRAINT timeline_relationships_pkey PRIMARY KEY (id);


--
-- Name: timeline_relationships timeline_relationships_source_target_type_key; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.timeline_relationships
    ADD CONSTRAINT timeline_relationships_source_target_type_key UNIQUE (source, target, type);


--
-- Name: analysis_tasks uk8nyh4qb0a7ak578w3lvjv6wuv; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.analysis_tasks
    ADD CONSTRAINT uk8nyh4qb0a7ak578w3lvjv6wuv UNIQUE (task_id);


--
-- Name: paper_bookmarks ukoq2o1548h8hn2chx62ftb5tf4; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.paper_bookmarks
    ADD CONSTRAINT ukoq2o1548h8hn2chx62ftb5tf4 UNIQUE (user_id, discovered_paper_id);


--
-- Name: user_operations user_operations_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.user_operations
    ADD CONSTRAINT user_operations_pkey PRIMARY KEY (id);


--
-- Name: user_roles user_roles_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.user_roles
    ADD CONSTRAINT user_roles_pkey PRIMARY KEY (id);


--
-- Name: user_settings user_settings_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.user_settings
    ADD CONSTRAINT user_settings_pkey PRIMARY KEY (user_id);


--
-- Name: users users_email_key; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.users
    ADD CONSTRAINT users_email_key UNIQUE (email);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: users users_username_key; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.users
    ADD CONSTRAINT users_username_key UNIQUE (username);


--
-- Name: visualization_states visualization_states_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.visualization_states
    ADD CONSTRAINT visualization_states_pkey PRIMARY KEY (id);


--
-- Name: visualization_states visualization_states_session_id_key; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.visualization_states
    ADD CONSTRAINT visualization_states_session_id_key UNIQUE (session_id);


--
-- Name: analysis_results_analysis_type_idx; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX analysis_results_analysis_type_idx ON answer42.analysis_results USING btree (analysis_type);


--
-- Name: analysis_results_paper_id_idx; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX analysis_results_paper_id_idx ON answer42.analysis_results USING btree (paper_id);


--
-- Name: analysis_results_user_id_idx; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX analysis_results_user_id_idx ON answer42.analysis_results USING btree (user_id);


--
-- Name: analysis_tasks_paper_id_idx; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX analysis_tasks_paper_id_idx ON answer42.analysis_tasks USING btree (paper_id);


--
-- Name: analysis_tasks_status_idx; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX analysis_tasks_status_idx ON answer42.analysis_tasks USING btree (status);


--
-- Name: analysis_tasks_user_id_idx; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX analysis_tasks_user_id_idx ON answer42.analysis_tasks USING btree (user_id);


--
-- Name: chat_messages_session_id_idx; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX chat_messages_session_id_idx ON answer42.chat_messages USING btree (session_id);


--
-- Name: chat_sessions_user_id_idx; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX chat_sessions_user_id_idx ON answer42.chat_sessions USING btree (user_id);


--
-- Name: citation_verifications_arxiv_id_idx; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX citation_verifications_arxiv_id_idx ON answer42.citation_verifications USING btree (arxiv_id);


--
-- Name: citation_verifications_citation_id_idx; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX citation_verifications_citation_id_idx ON answer42.citation_verifications USING btree (citation_id);


--
-- Name: citation_verifications_doi_idx; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX citation_verifications_doi_idx ON answer42.citation_verifications USING btree (doi);


--
-- Name: citation_verifications_paper_id_idx; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX citation_verifications_paper_id_idx ON answer42.citation_verifications USING btree (paper_id);


--
-- Name: citation_verifications_semantic_scholar_id_idx; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX citation_verifications_semantic_scholar_id_idx ON answer42.citation_verifications USING btree (semantic_scholar_id);


--
-- Name: citations_paper_id_idx; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX citations_paper_id_idx ON answer42.citations USING btree (paper_id);


--
-- Name: concept_maps_paper_id_idx; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX concept_maps_paper_id_idx ON answer42.concept_maps USING btree (paper_id);


--
-- Name: concept_maps_user_id_idx; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX concept_maps_user_id_idx ON answer42.concept_maps USING btree (user_id);


--
-- Name: credit_balances_user_id_idx; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX credit_balances_user_id_idx ON answer42.credit_balances USING btree (user_id);


--
-- Name: credit_transactions_created_at_idx; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX credit_transactions_created_at_idx ON answer42.credit_transactions USING btree (created_at);


--
-- Name: credit_transactions_user_id_idx; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX credit_transactions_user_id_idx ON answer42.credit_transactions USING btree (user_id);


--
-- Name: flashcards_paper_id_idx; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX flashcards_paper_id_idx ON answer42.flashcards USING btree (paper_id);


--
-- Name: flashcards_user_id_idx; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX flashcards_user_id_idx ON answer42.flashcards USING btree (user_id);


--
-- Name: idx_chat_messages_session_created; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_chat_messages_session_created ON answer42.chat_messages USING btree (session_id, created_at DESC);


--
-- Name: idx_chat_messages_session_sequence; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_chat_messages_session_sequence ON answer42.chat_messages USING btree (session_id, sequence_number);


--
-- Name: idx_credit_transactions_user_date; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_credit_transactions_user_date ON answer42.credit_transactions USING btree (user_id, created_at DESC);


--
-- Name: idx_discovered_papers_arxiv_id; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_discovered_papers_arxiv_id ON answer42.discovered_papers USING btree (arxiv_id) WHERE (arxiv_id IS NOT NULL);


--
-- Name: idx_discovered_papers_citation_count; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_discovered_papers_citation_count ON answer42.discovered_papers USING btree (citation_count DESC);


--
-- Name: idx_discovered_papers_created_at; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_discovered_papers_created_at ON answer42.discovered_papers USING btree (created_at DESC);


--
-- Name: idx_discovered_papers_data_completeness_score; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_discovered_papers_data_completeness_score ON answer42.discovered_papers USING btree (data_completeness_score DESC);


--
-- Name: idx_discovered_papers_discovery_source; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_discovered_papers_discovery_source ON answer42.discovered_papers USING btree (discovery_source);


--
-- Name: idx_discovered_papers_doi; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_discovered_papers_doi ON answer42.discovered_papers USING btree (doi) WHERE (doi IS NOT NULL);


--
-- Name: idx_discovered_papers_first_discovered_at; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_discovered_papers_first_discovered_at ON answer42.discovered_papers USING btree (first_discovered_at DESC);


--
-- Name: idx_discovered_papers_metadata_gin; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_discovered_papers_metadata_gin ON answer42.discovered_papers USING gin (discovery_metadata);


--
-- Name: idx_discovered_papers_publication_date; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_discovered_papers_publication_date ON answer42.discovered_papers USING btree (publication_date DESC);


--
-- Name: idx_discovered_papers_publication_year; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_discovered_papers_publication_year ON answer42.discovered_papers USING btree (publication_year DESC);


--
-- Name: idx_discovered_papers_relevance_score; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_discovered_papers_relevance_score ON answer42.discovered_papers USING btree (relevance_score DESC);


--
-- Name: idx_discovered_papers_semantic_scholar_id; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_discovered_papers_semantic_scholar_id ON answer42.discovered_papers USING btree (semantic_scholar_id) WHERE (semantic_scholar_id IS NOT NULL);


--
-- Name: idx_discovered_papers_source_reliability_score; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_discovered_papers_source_reliability_score ON answer42.discovered_papers USING btree (source_reliability_score DESC);


--
-- Name: idx_discovered_papers_title; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_discovered_papers_title ON answer42.discovered_papers USING gin (to_tsvector('english'::regconfig, (title)::text));


--
-- Name: idx_discovered_papers_user_source; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_discovered_papers_user_source ON answer42.discovered_papers USING btree (user_id, source_paper_id) WHERE (user_id IS NOT NULL);


--
-- Name: idx_discovery_feedback_created_at; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_discovery_feedback_created_at ON answer42.discovery_feedback USING btree (created_at DESC);


--
-- Name: idx_discovery_feedback_discovered_paper; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_discovery_feedback_discovered_paper ON answer42.discovery_feedback USING btree (discovered_paper_id);


--
-- Name: idx_discovery_feedback_source_paper; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_discovery_feedback_source_paper ON answer42.discovery_feedback USING btree (source_paper_id);


--
-- Name: idx_discovery_feedback_type; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_discovery_feedback_type ON answer42.discovery_feedback USING btree (feedback_type);


--
-- Name: idx_discovery_feedback_user; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_discovery_feedback_user ON answer42.discovery_feedback USING btree (user_id);


--
-- Name: idx_discovery_results_agent_task; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_discovery_results_agent_task ON answer42.discovery_results USING btree (agent_task_id) WHERE (agent_task_id IS NOT NULL);


--
-- Name: idx_discovery_results_scope; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_discovery_results_scope ON answer42.discovery_results USING btree (discovery_scope);


--
-- Name: idx_discovery_results_source_paper; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_discovery_results_source_paper ON answer42.discovery_results USING btree (source_paper_id);


--
-- Name: idx_discovery_results_started_at; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_discovery_results_started_at ON answer42.discovery_results USING btree (started_at DESC);


--
-- Name: idx_discovery_results_status; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_discovery_results_status ON answer42.discovery_results USING btree (status);


--
-- Name: idx_discovery_results_user; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_discovery_results_user ON answer42.discovery_results USING btree (user_id) WHERE (user_id IS NOT NULL);


--
-- Name: idx_discovery_results_user_status; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_discovery_results_user_status ON answer42.discovery_results USING btree (user_id, status) WHERE (user_id IS NOT NULL);


--
-- Name: idx_paper_relationships_discovered_paper; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_paper_relationships_discovered_paper ON answer42.paper_relationships USING btree (discovered_paper_id);


--
-- Name: idx_paper_relationships_source_discovery; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_paper_relationships_source_discovery ON answer42.paper_relationships USING btree (source_paper_id, discovery_source);


--
-- Name: idx_paper_relationships_source_paper; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_paper_relationships_source_paper ON answer42.paper_relationships USING btree (source_paper_id);


--
-- Name: idx_paper_relationships_strength; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_paper_relationships_strength ON answer42.paper_relationships USING btree (relationship_strength DESC);


--
-- Name: idx_paper_relationships_type; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_paper_relationships_type ON answer42.paper_relationships USING btree (relationship_type);


--
-- Name: idx_papers_abstract_search; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_papers_abstract_search ON answer42.papers USING gin (to_tsvector('english'::regconfig, paper_abstract));


--
-- Name: idx_papers_metadata_gin; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_papers_metadata_gin ON answer42.papers USING gin (metadata);


--
-- Name: idx_papers_title_search; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_papers_title_search ON answer42.papers USING gin (to_tsvector('english'::regconfig, (title)::text));


--
-- Name: metadata_verifications_paper_id_idx; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX metadata_verifications_paper_id_idx ON answer42.metadata_verifications USING btree (paper_id);


--
-- Name: metadata_verifications_paper_id_source_idx; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX metadata_verifications_paper_id_source_idx ON answer42.metadata_verifications USING btree (paper_id, source);


--
-- Name: metadata_verifications_source_idx; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX metadata_verifications_source_idx ON answer42.metadata_verifications USING btree (source);


--
-- Name: notes_paper_id_idx; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX notes_paper_id_idx ON answer42.notes USING btree (paper_id);


--
-- Name: paper_content_paper_id_idx; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX paper_content_paper_id_idx ON answer42.paper_content USING btree (paper_id);


--
-- Name: paper_sections_paper_id_idx; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX paper_sections_paper_id_idx ON answer42.paper_sections USING btree (paper_id);


--
-- Name: payment_methods_user_id_idx; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX payment_methods_user_id_idx ON answer42.payment_methods USING btree (user_id);


--
-- Name: practice_questions_paper_id_idx; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX practice_questions_paper_id_idx ON answer42.practice_questions USING btree (paper_id);


--
-- Name: practice_questions_user_id_idx; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX practice_questions_user_id_idx ON answer42.practice_questions USING btree (user_id);


--
-- Name: projects_user_id_idx; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX projects_user_id_idx ON answer42.projects USING btree (user_id);


--
-- Name: subscriptions_user_id_idx; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX subscriptions_user_id_idx ON answer42.subscriptions USING btree (user_id);


--
-- Name: tasks_agent_id_idx; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX tasks_agent_id_idx ON answer42.tasks USING btree (agent_id);


--
-- Name: tasks_status_idx; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX tasks_status_idx ON answer42.tasks USING btree (status);


--
-- Name: tasks_user_id_idx; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX tasks_user_id_idx ON answer42.tasks USING btree (user_id);


--
-- Name: user_operations_created_at_idx; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX user_operations_created_at_idx ON answer42.user_operations USING btree (created_at);


--
-- Name: user_operations_operation_type_idx; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX user_operations_operation_type_idx ON answer42.user_operations USING btree (operation_type);


--
-- Name: user_operations_user_id_idx; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX user_operations_user_id_idx ON answer42.user_operations USING btree (user_id);


--
-- Name: user_settings_user_id_idx; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX user_settings_user_id_idx ON answer42.user_settings USING btree (user_id);


--
-- Name: visualization_states_user_id_idx; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX visualization_states_user_id_idx ON answer42.visualization_states USING btree (user_id);


--
-- Name: chat_messages update_session_timestamp; Type: TRIGGER; Schema: answer42; Owner: postgres
--

CREATE TRIGGER update_session_timestamp AFTER INSERT ON answer42.chat_messages FOR EACH ROW EXECUTE FUNCTION answer42.update_session_last_message_timestamp();


--
-- Name: user_settings update_user_settings_updated_at_trigger; Type: TRIGGER; Schema: answer42; Owner: postgres
--

CREATE TRIGGER update_user_settings_updated_at_trigger BEFORE UPDATE ON answer42.user_settings FOR EACH ROW EXECUTE FUNCTION answer42.update_user_settings_updated_at();


--
-- Name: analysis_results answer42_analysis_results_paper_id_fkey; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.analysis_results
    ADD CONSTRAINT answer42_analysis_results_paper_id_fkey FOREIGN KEY (paper_id) REFERENCES answer42.papers(id);


--
-- Name: analysis_results answer42_analysis_results_user_id_fkey; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.analysis_results
    ADD CONSTRAINT answer42_analysis_results_user_id_fkey FOREIGN KEY (user_id) REFERENCES answer42.users(id);


--
-- Name: analysis_tasks answer42_analysis_tasks_user_id_fkey; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.analysis_tasks
    ADD CONSTRAINT answer42_analysis_tasks_user_id_fkey FOREIGN KEY (user_id) REFERENCES answer42.users(id);


--
-- Name: chat_messages answer42_chat_messages_session_id_fkey; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.chat_messages
    ADD CONSTRAINT answer42_chat_messages_session_id_fkey FOREIGN KEY (session_id) REFERENCES answer42.chat_sessions(id);


--
-- Name: chat_sessions answer42_chat_sessions_user_id_fkey; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.chat_sessions
    ADD CONSTRAINT answer42_chat_sessions_user_id_fkey FOREIGN KEY (user_id) REFERENCES answer42.users(id);


--
-- Name: citation_verifications answer42_citation_verifications_citation_id_fkey; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.citation_verifications
    ADD CONSTRAINT answer42_citation_verifications_citation_id_fkey FOREIGN KEY (citation_id) REFERENCES answer42.citations(id);


--
-- Name: credit_balances answer42_credit_balances_user_id_fkey; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.credit_balances
    ADD CONSTRAINT answer42_credit_balances_user_id_fkey FOREIGN KEY (user_id) REFERENCES answer42.users(id);


--
-- Name: credit_transactions answer42_credit_transactions_user_id_fkey; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.credit_transactions
    ADD CONSTRAINT answer42_credit_transactions_user_id_fkey FOREIGN KEY (user_id) REFERENCES answer42.users(id);


--
-- Name: invoices answer42_invoices_subscription_id_fkey; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.invoices
    ADD CONSTRAINT answer42_invoices_subscription_id_fkey FOREIGN KEY (subscription_id) REFERENCES answer42.subscriptions(id);


--
-- Name: paper_tags answer42_paper_tags_tag_id_fkey; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.paper_tags
    ADD CONSTRAINT answer42_paper_tags_tag_id_fkey FOREIGN KEY (tag_id) REFERENCES answer42.tags(id);


--
-- Name: papers answer42_papers_user_id_fkey; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.papers
    ADD CONSTRAINT answer42_papers_user_id_fkey FOREIGN KEY (user_id) REFERENCES answer42.users(id);


--
-- Name: project_papers answer42_project_papers_project_id_fkey; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.project_papers
    ADD CONSTRAINT answer42_project_papers_project_id_fkey FOREIGN KEY (project_id) REFERENCES answer42.projects(id);


--
-- Name: user_operations answer42_user_operations_user_id_fkey; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.user_operations
    ADD CONSTRAINT answer42_user_operations_user_id_fkey FOREIGN KEY (user_id) REFERENCES answer42.users(id);


--
-- Name: user_settings answer42_user_settings_user_id_fkey; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.user_settings
    ADD CONSTRAINT answer42_user_settings_user_id_fkey FOREIGN KEY (user_id) REFERENCES answer42.users(id);


--
-- Name: visualization_states answer42_visualization_states_session_id_fkey; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.visualization_states
    ADD CONSTRAINT answer42_visualization_states_session_id_fkey FOREIGN KEY (session_id) REFERENCES answer42.chat_sessions(id);


--
-- Name: visualization_states answer42_visualization_states_user_id_fkey; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.visualization_states
    ADD CONSTRAINT answer42_visualization_states_user_id_fkey FOREIGN KEY (user_id) REFERENCES answer42.users(id);


--
-- Name: discovery_feedback discovery_feedback_discovered_paper_id_fkey; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.discovery_feedback
    ADD CONSTRAINT discovery_feedback_discovered_paper_id_fkey FOREIGN KEY (discovered_paper_id) REFERENCES answer42.discovered_papers(id) ON DELETE CASCADE;


--
-- Name: discovery_feedback discovery_feedback_discovery_result_id_fkey; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.discovery_feedback
    ADD CONSTRAINT discovery_feedback_discovery_result_id_fkey FOREIGN KEY (discovery_result_id) REFERENCES answer42.discovery_results(id) ON DELETE CASCADE;


--
-- Name: discovery_feedback discovery_feedback_source_paper_id_fkey; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.discovery_feedback
    ADD CONSTRAINT discovery_feedback_source_paper_id_fkey FOREIGN KEY (source_paper_id) REFERENCES answer42.papers(id) ON DELETE CASCADE;


--
-- Name: discovery_feedback discovery_feedback_user_id_fkey; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.discovery_feedback
    ADD CONSTRAINT discovery_feedback_user_id_fkey FOREIGN KEY (user_id) REFERENCES answer42.users(id) ON DELETE CASCADE;


--
-- Name: discovery_results discovery_results_source_paper_id_fkey; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.discovery_results
    ADD CONSTRAINT discovery_results_source_paper_id_fkey FOREIGN KEY (source_paper_id) REFERENCES answer42.papers(id) ON DELETE CASCADE;


--
-- Name: discovery_results discovery_results_user_id_fkey; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.discovery_results
    ADD CONSTRAINT discovery_results_user_id_fkey FOREIGN KEY (user_id) REFERENCES answer42.users(id) ON DELETE SET NULL;


--
-- Name: paper_bookmarks fk43saq2r8j7g0h9nak3ht59qgt; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.paper_bookmarks
    ADD CONSTRAINT fk43saq2r8j7g0h9nak3ht59qgt FOREIGN KEY (discovered_paper_id) REFERENCES answer42.discovered_papers(id);


--
-- Name: analysis_tasks fk8y3vd98ki07xgv5ugx3kjcs3p; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.analysis_tasks
    ADD CONSTRAINT fk8y3vd98ki07xgv5ugx3kjcs3p FOREIGN KEY (result_id) REFERENCES answer42.analysis_results(id);


--
-- Name: discovered_papers fkbp99vpbkvgx9mbrsus6j3pxaj; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.discovered_papers
    ADD CONSTRAINT fkbp99vpbkvgx9mbrsus6j3pxaj FOREIGN KEY (user_id) REFERENCES answer42.users(id);


--
-- Name: paper_bookmarks fkcegcjikjy4n7gqd478p8ipxhx; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.paper_bookmarks
    ADD CONSTRAINT fkcegcjikjy4n7gqd478p8ipxhx FOREIGN KEY (user_id) REFERENCES answer42.users(id);


--
-- Name: analysis_tasks fkd54hw4ga7g5qg7u4tehxsnjhn; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.analysis_tasks
    ADD CONSTRAINT fkd54hw4ga7g5qg7u4tehxsnjhn FOREIGN KEY (paper_id) REFERENCES answer42.papers(id);


--
-- Name: subscriptions fkg41f5iev0mretaqhvepf0lks0; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.subscriptions
    ADD CONSTRAINT fkg41f5iev0mretaqhvepf0lks0 FOREIGN KEY (plan_id) REFERENCES answer42.subscription_plans(id);


--
-- Name: user_roles fkhfh9dx7w3ubf1co1vdev94g3f; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.user_roles
    ADD CONSTRAINT fkhfh9dx7w3ubf1co1vdev94g3f FOREIGN KEY (user_id) REFERENCES answer42.users(id);


--
-- Name: subscriptions fkhro52ohfqfbay9774bev0qinr; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.subscriptions
    ADD CONSTRAINT fkhro52ohfqfbay9774bev0qinr FOREIGN KEY (user_id) REFERENCES answer42.users(id);


--
-- Name: projects fkhswfwa3ga88vxv1pmboss6jhm; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.projects
    ADD CONSTRAINT fkhswfwa3ga88vxv1pmboss6jhm FOREIGN KEY (user_id) REFERENCES answer42.users(id);


--
-- Name: project_papers fkln7db4qdsycsqalwie85ok1x8; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.project_papers
    ADD CONSTRAINT fkln7db4qdsycsqalwie85ok1x8 FOREIGN KEY (paper_id) REFERENCES answer42.papers(id);


--
-- Name: discovered_papers fkqy4gsrdv4da5bg0m6ppnj22gc; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.discovered_papers
    ADD CONSTRAINT fkqy4gsrdv4da5bg0m6ppnj22gc FOREIGN KEY (source_paper_id) REFERENCES answer42.papers(id);


--
-- Name: analysis_results fkrh3apk9wf23pk867vknb4imio; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.analysis_results
    ADD CONSTRAINT fkrh3apk9wf23pk867vknb4imio FOREIGN KEY (task_id) REFERENCES answer42.analysis_tasks(id);


--
-- Name: paper_relationships paper_relationships_discovered_paper_id_fkey; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.paper_relationships
    ADD CONSTRAINT paper_relationships_discovered_paper_id_fkey FOREIGN KEY (discovered_paper_id) REFERENCES answer42.discovered_papers(id) ON DELETE CASCADE;


--
-- Name: paper_relationships paper_relationships_source_paper_id_fkey; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.paper_relationships
    ADD CONSTRAINT paper_relationships_source_paper_id_fkey FOREIGN KEY (source_paper_id) REFERENCES answer42.papers(id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--

