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
    'PAPER'
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
    key text NOT NULL,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
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
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    is_archived boolean NOT NULL,
    last_accessed_at timestamp(6) without time zone,
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
    progress integer DEFAULT 0,
    options jsonb DEFAULT '{}'::jsonb,
    result_id uuid,
    error_message character varying(32600),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    completed_at timestamp(6) without time zone,
    task_id uuid,
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
    created_at timestamp with time zone DEFAULT now(),
    sequence_number integer DEFAULT 0 NOT NULL,
    message_type character varying(255) DEFAULT 'message'::text NOT NULL,
    is_edited boolean DEFAULT false NOT NULL,
    token_count integer,
    last_edited_at timestamp with time zone,
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
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    last_message_at timestamp with time zone DEFAULT now(),
    provider character varying(255) NOT NULL,
    title character varying(255)
);


ALTER TABLE answer42.chat_sessions OWNER TO postgres;

--
-- Name: citation_verifications; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.citation_verifications (
    id uuid DEFAULT extensions.uuid_generate_v4() NOT NULL,
    citation_id uuid,
    paper_id uuid,
    doi text,
    verified boolean DEFAULT false,
    verification_source text,
    confidence numeric(3,2) DEFAULT 0,
    verification_date timestamp with time zone DEFAULT now(),
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
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
    paper_id uuid,
    citation_data jsonb NOT NULL,
    raw_text text,
    created_at timestamp with time zone DEFAULT now()
);


ALTER TABLE answer42.citations OWNER TO postgres;

--
-- Name: concept_maps; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.concept_maps (
    id uuid DEFAULT extensions.uuid_generate_v4() NOT NULL,
    paper_id uuid,
    user_id uuid,
    title text NOT NULL,
    description text,
    map_data jsonb NOT NULL,
    image_url text,
    tags text[],
    metadata jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
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
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
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
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
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
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT balance_after_non_negative CHECK ((balance_after >= 0))
);


ALTER TABLE answer42.credit_transactions OWNER TO postgres;

--
-- Name: flashcards; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.flashcards (
    id uuid DEFAULT extensions.uuid_generate_v4() NOT NULL,
    paper_id uuid,
    user_id uuid,
    question text NOT NULL,
    answer text NOT NULL,
    section text,
    tags text[],
    difficulty text,
    last_reviewed timestamp with time zone,
    review_count integer DEFAULT 0,
    metadata jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);


ALTER TABLE answer42.flashcards OWNER TO postgres;

--
-- Name: invoices; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.invoices (
    id uuid DEFAULT extensions.uuid_generate_v4() NOT NULL,
    subscription_id uuid,
    amount numeric(10,2) NOT NULL,
    currency text NOT NULL,
    status text NOT NULL,
    invoice_url text,
    paid_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now()
);


ALTER TABLE answer42.invoices OWNER TO postgres;

--
-- Name: metadata_verifications; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.metadata_verifications (
    id uuid DEFAULT extensions.uuid_generate_v4() NOT NULL,
    paper_id uuid,
    source text NOT NULL,
    confidence numeric(3,2) DEFAULT 0,
    metadata jsonb,
    matched_by text,
    identifier_used text,
    verified_at timestamp with time zone DEFAULT now(),
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
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
    paper_id uuid,
    user_id uuid,
    content text NOT NULL,
    location jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
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
    paper_abstract character varying(255),
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
    summary_brief character varying(255),
    summary_detailed character varying(255),
    summary_standard character varying(255),
    text_content character varying(255),
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
    id text NOT NULL,
    agent_id text NOT NULL,
    user_id uuid NOT NULL,
    input jsonb NOT NULL,
    status text NOT NULL,
    error text,
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
    credit_cost integer
);


ALTER TABLE answer42.user_operations OWNER TO postgres;

--
-- Name: user_roles; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.user_roles (
    user_id uuid NOT NULL,
    role character varying(255)
);


ALTER TABLE answer42.user_roles OWNER TO postgres;

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
    created_at timestamp(6) without time zone,
    last_login timestamp(6) without time zone
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
-- Name: user_operations user_operations_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.user_operations
    ADD CONSTRAINT user_operations_pkey PRIMARY KEY (id);


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
-- Name: chat_sessions_last_message_at_idx; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX chat_sessions_last_message_at_idx ON answer42.chat_sessions USING btree (last_message_at);


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
-- Name: idx_chat_messages_session_sequence; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_chat_messages_session_sequence ON answer42.chat_messages USING btree (session_id, sequence_number);


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
-- Name: analysis_results answer42_analysis_results_task_id_fkey; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.analysis_results
    ADD CONSTRAINT answer42_analysis_results_task_id_fkey FOREIGN KEY (task_id) REFERENCES answer42.analysis_tasks(id);


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
-- Name: papers fk49jvxy1dme82b8eaeaidp90pd; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.papers
    ADD CONSTRAINT fk49jvxy1dme82b8eaeaidp90pd FOREIGN KEY (user_id) REFERENCES answer42.users(id);


--
-- Name: analysis_tasks fk8y3vd98ki07xgv5ugx3kjcs3p; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.analysis_tasks
    ADD CONSTRAINT fk8y3vd98ki07xgv5ugx3kjcs3p FOREIGN KEY (task_id) REFERENCES answer42.analysis_results(id);


--
-- Name: analysis_results fkccclrc4ifmajsdhbw7w4l94wu; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.analysis_results
    ADD CONSTRAINT fkccclrc4ifmajsdhbw7w4l94wu FOREIGN KEY (paper_id) REFERENCES answer42.papers(id);


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
-- Name: analysis_tasks fkm7hhffugu76ne2p2qcmraer72; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.analysis_tasks
    ADD CONSTRAINT fkm7hhffugu76ne2p2qcmraer72 FOREIGN KEY (result_id) REFERENCES answer42.analysis_results(id);


--
-- PostgreSQL database dump complete
--

