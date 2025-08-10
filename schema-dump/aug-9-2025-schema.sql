--
-- PostgreSQL database dump
--

-- Dumped from database version 17.4
-- Dumped by pg_dump version 17.4

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
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
-- Name: calculate_total_tokens(); Type: FUNCTION; Schema: answer42; Owner: postgres
--

CREATE FUNCTION answer42.calculate_total_tokens() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    -- Calculate total tokens if not provided
    IF NEW.total_tokens = 0 OR NEW.total_tokens IS NULL THEN
        NEW.total_tokens = COALESCE(NEW.input_tokens, 0) + COALESCE(NEW.output_tokens, 0);
    END IF;
    
    -- Ensure cost is never null
    IF NEW.estimated_cost IS NULL THEN
        NEW.estimated_cost = 0.000000;
    END IF;
    
    -- Ensure success is never null
    IF NEW.success IS NULL THEN
        NEW.success = true;
    END IF;
    
    RETURN NEW;
END;
$$;


ALTER FUNCTION answer42.calculate_total_tokens() OWNER TO postgres;

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
-- Name: update_token_metrics_updated_at(); Type: FUNCTION; Schema: answer42; Owner: postgres
--

CREATE FUNCTION answer42.update_token_metrics_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;


ALTER FUNCTION answer42.update_token_metrics_updated_at() OWNER TO postgres;

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
    last_accessed_at timestamp(6) with time zone,
    CONSTRAINT analysis_results_analysis_type_check CHECK (((analysis_type)::text = ANY (ARRAY[('DEEP_SUMMARY'::character varying)::text, ('METHODOLOGY_ANALYSIS'::character varying)::text, ('RESULTS_INTERPRETATION'::character varying)::text, ('CRITICAL_EVALUATION'::character varying)::text, ('RESEARCH_IMPLICATIONS'::character varying)::text])))
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
    CONSTRAINT analysis_tasks_analysis_type_check CHECK (((analysis_type)::text = ANY (ARRAY[('DEEP_SUMMARY'::character varying)::text, ('METHODOLOGY_ANALYSIS'::character varying)::text, ('RESULTS_INTERPRETATION'::character varying)::text, ('CRITICAL_EVALUATION'::character varying)::text, ('RESEARCH_IMPLICATIONS'::character varying)::text]))),
    CONSTRAINT analysis_tasks_status_check CHECK (((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('PROCESSING'::character varying)::text, ('COMPLETED'::character varying)::text, ('FAILED'::character varying)::text])))
);


ALTER TABLE answer42.analysis_tasks OWNER TO postgres;

--
-- Name: batch_job_execution; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.batch_job_execution (
    job_execution_id bigint NOT NULL,
    version bigint,
    job_instance_id bigint NOT NULL,
    create_time timestamp without time zone NOT NULL,
    start_time timestamp without time zone,
    end_time timestamp without time zone,
    status character varying(10),
    exit_code character varying(2500),
    exit_message character varying(2500),
    last_updated timestamp without time zone
);


ALTER TABLE answer42.batch_job_execution OWNER TO postgres;

--
-- Name: batch_job_execution_context; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.batch_job_execution_context (
    job_execution_id bigint NOT NULL,
    short_context character varying(2500) NOT NULL,
    serialized_context text
);


ALTER TABLE answer42.batch_job_execution_context OWNER TO postgres;

--
-- Name: batch_job_execution_job_execution_id_seq; Type: SEQUENCE; Schema: answer42; Owner: postgres
--

CREATE SEQUENCE answer42.batch_job_execution_job_execution_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE answer42.batch_job_execution_job_execution_id_seq OWNER TO postgres;

--
-- Name: batch_job_execution_job_execution_id_seq; Type: SEQUENCE OWNED BY; Schema: answer42; Owner: postgres
--

ALTER SEQUENCE answer42.batch_job_execution_job_execution_id_seq OWNED BY answer42.batch_job_execution.job_execution_id;


--
-- Name: batch_job_execution_params; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.batch_job_execution_params (
    job_execution_id bigint NOT NULL,
    parameter_name character varying(100) NOT NULL,
    parameter_type character varying(100) NOT NULL,
    parameter_value character varying(2500),
    identifying character(1) NOT NULL
);


ALTER TABLE answer42.batch_job_execution_params OWNER TO postgres;

--
-- Name: batch_job_execution_seq; Type: SEQUENCE; Schema: answer42; Owner: postgres
--

CREATE SEQUENCE answer42.batch_job_execution_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE answer42.batch_job_execution_seq OWNER TO postgres;

--
-- Name: batch_job_instance; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.batch_job_instance (
    job_instance_id bigint NOT NULL,
    version bigint,
    job_name character varying(100) NOT NULL,
    job_key character varying(32) NOT NULL
);


ALTER TABLE answer42.batch_job_instance OWNER TO postgres;

--
-- Name: batch_job_instance_job_instance_id_seq; Type: SEQUENCE; Schema: answer42; Owner: postgres
--

CREATE SEQUENCE answer42.batch_job_instance_job_instance_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE answer42.batch_job_instance_job_instance_id_seq OWNER TO postgres;

--
-- Name: batch_job_instance_job_instance_id_seq; Type: SEQUENCE OWNED BY; Schema: answer42; Owner: postgres
--

ALTER SEQUENCE answer42.batch_job_instance_job_instance_id_seq OWNED BY answer42.batch_job_instance.job_instance_id;


--
-- Name: batch_job_seq; Type: SEQUENCE; Schema: answer42; Owner: postgres
--

CREATE SEQUENCE answer42.batch_job_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE answer42.batch_job_seq OWNER TO postgres;

--
-- Name: batch_step_execution; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.batch_step_execution (
    step_execution_id bigint NOT NULL,
    version bigint NOT NULL,
    step_name character varying(100) NOT NULL,
    job_execution_id bigint NOT NULL,
    create_time timestamp without time zone NOT NULL,
    start_time timestamp without time zone,
    end_time timestamp without time zone,
    status character varying(10),
    commit_count bigint,
    read_count bigint,
    filter_count bigint,
    write_count bigint,
    read_skip_count bigint,
    write_skip_count bigint,
    process_skip_count bigint,
    rollback_count bigint,
    exit_code character varying(2500),
    exit_message character varying(2500),
    last_updated timestamp without time zone
);


ALTER TABLE answer42.batch_step_execution OWNER TO postgres;

--
-- Name: batch_step_execution_context; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.batch_step_execution_context (
    step_execution_id bigint NOT NULL,
    short_context character varying(2500) NOT NULL,
    serialized_context text
);


ALTER TABLE answer42.batch_step_execution_context OWNER TO postgres;

--
-- Name: batch_step_execution_seq; Type: SEQUENCE; Schema: answer42; Owner: postgres
--

CREATE SEQUENCE answer42.batch_step_execution_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE answer42.batch_step_execution_seq OWNER TO postgres;

--
-- Name: batch_step_execution_step_execution_id_seq; Type: SEQUENCE; Schema: answer42; Owner: postgres
--

CREATE SEQUENCE answer42.batch_step_execution_step_execution_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE answer42.batch_step_execution_step_execution_id_seq OWNER TO postgres;

--
-- Name: batch_step_execution_step_execution_id_seq; Type: SEQUENCE OWNED BY; Schema: answer42; Owner: postgres
--

ALTER SEQUENCE answer42.batch_step_execution_step_execution_id_seq OWNED BY answer42.batch_step_execution.step_execution_id;


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
    last_edited_at timestamp(6) with time zone,
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
    doi character varying(255),
    verified boolean DEFAULT false,
    verification_source character varying(255),
    confidence double precision DEFAULT 0,
    verification_date timestamp with time zone DEFAULT now(),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    semantic_scholar_id character varying(255),
    arxiv_id character varying(255),
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
    discovered_at timestamp(6) with time zone,
    CONSTRAINT check_confidence_score_range CHECK (((confidence_score IS NULL) OR ((confidence_score >= (0.0)::double precision) AND (confidence_score <= (1.0)::double precision)))),
    CONSTRAINT check_relevance_score_range CHECK (((relevance_score >= (0.0)::double precision) AND (relevance_score <= (1.0)::double precision))),
    CONSTRAINT check_user_rating_range CHECK (((user_rating IS NULL) OR ((user_rating >= 1) AND (user_rating <= 5)))),
    CONSTRAINT discovered_papers_discovery_source_check CHECK (((discovery_source)::text = ANY (ARRAY[('CROSSREF'::character varying)::text, ('SEMANTIC_SCHOLAR'::character varying)::text, ('PERPLEXITY'::character varying)::text])))
);


ALTER TABLE answer42.discovered_papers OWNER TO postgres;

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
    CONSTRAINT discovery_results_discovery_scope_check CHECK (((discovery_scope)::text = ANY (ARRAY[('QUICK'::character varying)::text, ('STANDARD'::character varying)::text, ('COMPREHENSIVE'::character varying)::text, ('CUSTOM'::character varying)::text]))),
    CONSTRAINT discovery_results_status_check CHECK (((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('RUNNING'::character varying)::text, ('COMPLETED'::character varying)::text, ('FAILED'::character varying)::text, ('CANCELLED'::character varying)::text])))
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
    source character varying(255) NOT NULL,
    confidence double precision DEFAULT 0,
    metadata jsonb,
    matched_by character varying(255),
    identifier_used character varying(255),
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
    relationship_type character varying(255) NOT NULL,
    relationship_strength double precision DEFAULT 0.0,
    confidence_score double precision DEFAULT 0.0,
    discovery_source character varying(255) NOT NULL,
    discovery_method character varying(255),
    discovery_context jsonb,
    evidence jsonb,
    reasoning text,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    CONSTRAINT paper_relationships_confidence_score_check CHECK (((confidence_score >= (0.0)::double precision) AND (confidence_score <= (1.0)::double precision))),
    CONSTRAINT paper_relationships_discovery_source_check CHECK (((discovery_source)::text = ANY (ARRAY[('CROSSREF'::character varying)::text, ('SEMANTIC_SCHOLAR'::character varying)::text, ('PERPLEXITY'::character varying)::text]))),
    CONSTRAINT paper_relationships_relationship_strength_check CHECK (((relationship_strength >= (0.0)::double precision) AND (relationship_strength <= (1.0)::double precision))),
    CONSTRAINT paper_relationships_relationship_type_check CHECK (((relationship_type)::text = ANY (ARRAY[('CITES'::character varying)::text, ('CITED_BY'::character varying)::text, ('SEMANTIC_SIMILARITY'::character varying)::text, ('AUTHOR_CONNECTION'::character varying)::text, ('VENUE_SIMILARITY'::character varying)::text, ('TOPIC_SIMILARITY'::character varying)::text, ('METHODOLOGY_SIMILARITY'::character varying)::text, ('TEMPORAL_RELATIONSHIP'::character varying)::text])))
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
-- Name: token_metrics; Type: TABLE; Schema: answer42; Owner: postgres
--

CREATE TABLE answer42.token_metrics (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    provider character varying(50) NOT NULL,
    agent_type character varying(100) NOT NULL,
    task_id character varying(100),
    input_tokens integer DEFAULT 0 NOT NULL,
    output_tokens integer DEFAULT 0 NOT NULL,
    total_tokens integer DEFAULT 0 NOT NULL,
    estimated_cost numeric(10,6) DEFAULT 0.000000,
    processing_time_ms bigint,
    "timestamp" timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    metadata jsonb,
    session_id character varying(100),
    paper_id uuid,
    model_name character varying(100),
    temperature double precision,
    max_tokens integer,
    success boolean DEFAULT true NOT NULL,
    error_message text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE answer42.token_metrics OWNER TO postgres;

--
-- Name: TABLE token_metrics; Type: COMMENT; Schema: answer42; Owner: postgres
--

COMMENT ON TABLE answer42.token_metrics IS 'Comprehensive tracking of AI token usage, costs, and performance metrics';


--
-- Name: COLUMN token_metrics.provider; Type: COMMENT; Schema: answer42; Owner: postgres
--

COMMENT ON COLUMN answer42.token_metrics.provider IS 'AI provider used (OPENAI, ANTHROPIC, PERPLEXITY, OLLAMA)';


--
-- Name: COLUMN token_metrics.agent_type; Type: COMMENT; Schema: answer42; Owner: postgres
--

COMMENT ON COLUMN answer42.token_metrics.agent_type IS 'Type of agent that processed the request';


--
-- Name: COLUMN token_metrics.task_id; Type: COMMENT; Schema: answer42; Owner: postgres
--

COMMENT ON COLUMN answer42.token_metrics.task_id IS 'Unique identifier for the task being processed';


--
-- Name: COLUMN token_metrics.input_tokens; Type: COMMENT; Schema: answer42; Owner: postgres
--

COMMENT ON COLUMN answer42.token_metrics.input_tokens IS 'Number of tokens in the input/prompt';


--
-- Name: COLUMN token_metrics.output_tokens; Type: COMMENT; Schema: answer42; Owner: postgres
--

COMMENT ON COLUMN answer42.token_metrics.output_tokens IS 'Number of tokens in the response';


--
-- Name: COLUMN token_metrics.total_tokens; Type: COMMENT; Schema: answer42; Owner: postgres
--

COMMENT ON COLUMN answer42.token_metrics.total_tokens IS 'Total tokens used (input + output)';


--
-- Name: COLUMN token_metrics.estimated_cost; Type: COMMENT; Schema: answer42; Owner: postgres
--

COMMENT ON COLUMN answer42.token_metrics.estimated_cost IS 'Estimated cost in USD for the operation';


--
-- Name: COLUMN token_metrics.processing_time_ms; Type: COMMENT; Schema: answer42; Owner: postgres
--

COMMENT ON COLUMN answer42.token_metrics.processing_time_ms IS 'Time taken to process the request in milliseconds';


--
-- Name: COLUMN token_metrics.metadata; Type: COMMENT; Schema: answer42; Owner: postgres
--

COMMENT ON COLUMN answer42.token_metrics.metadata IS 'Additional metadata as JSONB (model parameters, etc.)';


--
-- Name: COLUMN token_metrics.session_id; Type: COMMENT; Schema: answer42; Owner: postgres
--

COMMENT ON COLUMN answer42.token_metrics.session_id IS 'Session identifier for grouping related requests';


--
-- Name: COLUMN token_metrics.success; Type: COMMENT; Schema: answer42; Owner: postgres
--

COMMENT ON COLUMN answer42.token_metrics.success IS 'Whether the operation completed successfully';


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
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    created_at timestamp(6) with time zone
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
-- Name: batch_job_execution job_execution_id; Type: DEFAULT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.batch_job_execution ALTER COLUMN job_execution_id SET DEFAULT nextval('answer42.batch_job_execution_job_execution_id_seq'::regclass);


--
-- Name: batch_job_instance job_instance_id; Type: DEFAULT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.batch_job_instance ALTER COLUMN job_instance_id SET DEFAULT nextval('answer42.batch_job_instance_job_instance_id_seq'::regclass);


--
-- Name: batch_step_execution step_execution_id; Type: DEFAULT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.batch_step_execution ALTER COLUMN step_execution_id SET DEFAULT nextval('answer42.batch_step_execution_step_execution_id_seq'::regclass);


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
-- Name: batch_job_execution_context batch_job_execution_context_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.batch_job_execution_context
    ADD CONSTRAINT batch_job_execution_context_pkey PRIMARY KEY (job_execution_id);


--
-- Name: batch_job_execution batch_job_execution_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.batch_job_execution
    ADD CONSTRAINT batch_job_execution_pkey PRIMARY KEY (job_execution_id);


--
-- Name: batch_job_instance batch_job_instance_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.batch_job_instance
    ADD CONSTRAINT batch_job_instance_pkey PRIMARY KEY (job_instance_id);


--
-- Name: batch_step_execution_context batch_step_execution_context_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.batch_step_execution_context
    ADD CONSTRAINT batch_step_execution_context_pkey PRIMARY KEY (step_execution_id);


--
-- Name: batch_step_execution batch_step_execution_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.batch_step_execution
    ADD CONSTRAINT batch_step_execution_pkey PRIMARY KEY (step_execution_id);


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
-- Name: batch_job_instance job_inst_un; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.batch_job_instance
    ADD CONSTRAINT job_inst_un UNIQUE (job_name, job_key);


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
-- Name: token_metrics token_metrics_pkey; Type: CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.token_metrics
    ADD CONSTRAINT token_metrics_pkey PRIMARY KEY (id);


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
-- Name: idx_token_metrics_agent_type; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_token_metrics_agent_type ON answer42.token_metrics USING btree (agent_type);


--
-- Name: idx_token_metrics_metadata_gin; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_token_metrics_metadata_gin ON answer42.token_metrics USING gin (metadata);


--
-- Name: idx_token_metrics_paper_id; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_token_metrics_paper_id ON answer42.token_metrics USING btree (paper_id);


--
-- Name: idx_token_metrics_provider; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_token_metrics_provider ON answer42.token_metrics USING btree (provider);


--
-- Name: idx_token_metrics_provider_agent; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_token_metrics_provider_agent ON answer42.token_metrics USING btree (provider, agent_type);


--
-- Name: idx_token_metrics_session_id; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_token_metrics_session_id ON answer42.token_metrics USING btree (session_id);


--
-- Name: idx_token_metrics_success; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_token_metrics_success ON answer42.token_metrics USING btree (success);


--
-- Name: idx_token_metrics_task_id; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_token_metrics_task_id ON answer42.token_metrics USING btree (task_id);


--
-- Name: idx_token_metrics_timestamp; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_token_metrics_timestamp ON answer42.token_metrics USING btree ("timestamp" DESC);


--
-- Name: idx_token_metrics_timestamp_success; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_token_metrics_timestamp_success ON answer42.token_metrics USING btree ("timestamp" DESC, success);


--
-- Name: idx_token_metrics_user_id; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_token_metrics_user_id ON answer42.token_metrics USING btree (user_id);


--
-- Name: idx_token_metrics_user_provider; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_token_metrics_user_provider ON answer42.token_metrics USING btree (user_id, provider);


--
-- Name: idx_token_metrics_user_timestamp; Type: INDEX; Schema: answer42; Owner: postgres
--

CREATE INDEX idx_token_metrics_user_timestamp ON answer42.token_metrics USING btree (user_id, "timestamp" DESC);


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
-- Name: token_metrics token_metrics_calculate_totals; Type: TRIGGER; Schema: answer42; Owner: postgres
--

CREATE TRIGGER token_metrics_calculate_totals BEFORE INSERT OR UPDATE ON answer42.token_metrics FOR EACH ROW EXECUTE FUNCTION answer42.calculate_total_tokens();


--
-- Name: token_metrics token_metrics_updated_at; Type: TRIGGER; Schema: answer42; Owner: postgres
--

CREATE TRIGGER token_metrics_updated_at BEFORE UPDATE ON answer42.token_metrics FOR EACH ROW EXECUTE FUNCTION answer42.update_token_metrics_updated_at();


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
-- Name: metadata_verifications fk5h3qc214q7dt4n3evo126lcnj; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.metadata_verifications
    ADD CONSTRAINT fk5h3qc214q7dt4n3evo126lcnj FOREIGN KEY (paper_id) REFERENCES answer42.papers(id);


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
-- Name: summaries fkg4r446gtbscvknw9r27tq985n; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.summaries
    ADD CONSTRAINT fkg4r446gtbscvknw9r27tq985n FOREIGN KEY (paper_id) REFERENCES answer42.papers(id);


--
-- Name: paper_content fkgy3cf7xvbb9l1sy7g0ygqx6pn; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.paper_content
    ADD CONSTRAINT fkgy3cf7xvbb9l1sy7g0ygqx6pn FOREIGN KEY (paper_id) REFERENCES answer42.papers(id);


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
-- Name: paper_sections fkoo85x9llm6p0s9h69m6u1nq2s; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.paper_sections
    ADD CONSTRAINT fkoo85x9llm6p0s9h69m6u1nq2s FOREIGN KEY (paper_id) REFERENCES answer42.papers(id);


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
-- Name: paper_tags fkyfw2ryr6v0871g165niw2ogi; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.paper_tags
    ADD CONSTRAINT fkyfw2ryr6v0871g165niw2ogi FOREIGN KEY (paper_id) REFERENCES answer42.papers(id);


--
-- Name: batch_job_execution_context job_exec_ctx_fk; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.batch_job_execution_context
    ADD CONSTRAINT job_exec_ctx_fk FOREIGN KEY (job_execution_id) REFERENCES answer42.batch_job_execution(job_execution_id);


--
-- Name: batch_job_execution job_exec_fk; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.batch_job_execution
    ADD CONSTRAINT job_exec_fk FOREIGN KEY (job_instance_id) REFERENCES answer42.batch_job_instance(job_instance_id);


--
-- Name: batch_job_execution_params job_exec_params_fk; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.batch_job_execution_params
    ADD CONSTRAINT job_exec_params_fk FOREIGN KEY (job_execution_id) REFERENCES answer42.batch_job_execution(job_execution_id);


--
-- Name: batch_step_execution job_exec_step_fk; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.batch_step_execution
    ADD CONSTRAINT job_exec_step_fk FOREIGN KEY (job_execution_id) REFERENCES answer42.batch_job_execution(job_execution_id);


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
-- Name: batch_step_execution_context step_exec_ctx_fk; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.batch_step_execution_context
    ADD CONSTRAINT step_exec_ctx_fk FOREIGN KEY (step_execution_id) REFERENCES answer42.batch_step_execution(step_execution_id);


--
-- Name: token_metrics token_metrics_paper_id_fkey; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.token_metrics
    ADD CONSTRAINT token_metrics_paper_id_fkey FOREIGN KEY (paper_id) REFERENCES answer42.papers(id) ON DELETE SET NULL;


--
-- Name: token_metrics token_metrics_user_id_fkey; Type: FK CONSTRAINT; Schema: answer42; Owner: postgres
--

ALTER TABLE ONLY answer42.token_metrics
    ADD CONSTRAINT token_metrics_user_id_fkey FOREIGN KEY (user_id) REFERENCES answer42.users(id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--

