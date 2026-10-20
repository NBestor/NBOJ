--
-- PostgreSQL database dump
--


-- Dumped from database version 16.14 (Debian 16.14-1.pgdg13+1)
-- Dumped by pg_dump version 16.14 (Debian 16.14-1.pgdg13+1)

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

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: ai_diagnosis_runs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ai_diagnosis_runs (
    official_version boolean NOT NULL,
    result_saved boolean NOT NULL,
    version_number integer NOT NULL,
    completed_at timestamp(6) without time zone,
    created_at timestamp(6) without time zone NOT NULL,
    id bigint NOT NULL,
    started_at timestamp(6) without time zone,
    submission_id bigint NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    status character varying(32) NOT NULL,
    generation_key character varying(64) NOT NULL,
    current_stage character varying(96),
    failure_reason text
);


--
-- Name: ai_diagnosis_runs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ai_diagnosis_runs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ai_diagnosis_runs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.ai_diagnosis_runs_id_seq OWNED BY public.ai_diagnosis_runs.id;


--
-- Name: ai_diagnosis_stage_runs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ai_diagnosis_stage_runs (
    attempt_count integer NOT NULL,
    completed_at timestamp(6) without time zone,
    created_at timestamp(6) without time zone NOT NULL,
    id bigint NOT NULL,
    latency_ms bigint,
    run_id bigint NOT NULL,
    started_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    status character varying(32) NOT NULL,
    stage_type character varying(64) NOT NULL,
    input_fingerprint character varying(96),
    issue_id character varying(120),
    prompt_version character varying(120),
    provider character varying(120),
    stage_key character varying(160) NOT NULL,
    model character varying(180),
    failure_reason text,
    output_json text
);


--
-- Name: ai_diagnosis_stage_runs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ai_diagnosis_stage_runs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ai_diagnosis_stage_runs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.ai_diagnosis_stage_runs_id_seq OWNED BY public.ai_diagnosis_stage_runs.id;


--
-- Name: ai_standard_improvement_points; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ai_standard_improvement_points (
    enabled boolean NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    id bigint NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    category character varying(80) NOT NULL,
    library_version character varying(80) NOT NULL,
    name character varying(120) NOT NULL,
    code character varying(160) NOT NULL,
    primary_knowledge_node_code character varying(160),
    skill_unit_code character varying(160),
    applicable_languages character varying(800),
    improvement_goal character varying(1200),
    practice_strategy character varying(1200),
    student_benefit character varying(1200),
    teacher_explanation character varying(1200),
    description character varying(1600),
    related_mistake_codes character varying(1600),
    knowledge_node_codes character varying(2400)
);


--
-- Name: ai_standard_improvement_points_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ai_standard_improvement_points_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ai_standard_improvement_points_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.ai_standard_improvement_points_id_seq OWNED BY public.ai_standard_improvement_points.id;


--
-- Name: ai_standard_library_growth_candidates; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ai_standard_library_growth_candidates (
    confidence double precision,
    occurrence_count integer,
    created_at timestamp(6) without time zone NOT NULL,
    id bigint NOT NULL,
    last_observed_at timestamp(6) without time zone,
    source_problem_id bigint,
    source_submission_id bigint,
    updated_at timestamp(6) without time zone NOT NULL,
    layer character varying(40) NOT NULL,
    status character varying(40) NOT NULL,
    evidence_status character varying(80),
    parent_knowledge_node_code character varying(120),
    suggested_code character varying(120) NOT NULL,
    suggested_name character varying(160) NOT NULL,
    suggested_path character varying(800),
    evidence_refs character varying(1200),
    similar_existing_items character varying(1200),
    change_reason character varying(1600),
    observed_submission_ids character varying(1600),
    precheck_message character varying(1600),
    teacher_note character varying(1600),
    before_snapshot oid,
    diff_summary oid,
    rollback_info oid,
    CONSTRAINT ai_standard_library_growth_candidates_layer_check CHECK (((layer)::text = ANY ((ARRAY['SKILL_UNIT'::character varying, 'MISTAKE_POINT'::character varying, 'BASIC_CAUSE'::character varying, 'IMPROVEMENT_POINT'::character varying])::text[]))),
    CONSTRAINT ai_standard_library_growth_candidates_status_check CHECK (((status)::text = ANY ((ARRAY['PROPOSED'::character varying, 'NEEDS_REVIEW'::character varying, 'BLOCKED'::character varying, 'MERGED_SIMILAR'::character varying, 'TEACHER_APPROVED'::character varying, 'REJECTED'::character varying, 'MERGED'::character varying, 'IGNORED'::character varying])::text[])))
);


--
-- Name: ai_standard_library_growth_candidates_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ai_standard_library_growth_candidates_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ai_standard_library_growth_candidates_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.ai_standard_library_growth_candidates_id_seq OWNED BY public.ai_standard_library_growth_candidates.id;


--
-- Name: ai_standard_library_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ai_standard_library_items (
    enabled boolean NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    id bigint NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    layer character varying(40) NOT NULL,
    severity character varying(40),
    category character varying(80) NOT NULL,
    library_version character varying(80) NOT NULL,
    mistake_type character varying(80),
    code character varying(100) NOT NULL,
    ability_point character varying(120),
    name character varying(120) NOT NULL,
    teaching_action character varying(120),
    primary_knowledge_node_code character varying(160),
    skill_unit_code character varying(160),
    applicable_languages character varying(800),
    hintl1 character varying(800),
    hintl2 character varying(800),
    hintl3 character varying(800),
    student_benefit character varying(800),
    when_to_use character varying(800),
    judge_signals character varying(1200),
    student_explanation character varying(1200),
    teacher_explanation character varying(1200),
    common_misconception character varying(1600),
    description character varying(1600),
    related_items character varying(1600),
    required_evidence character varying(1600),
    common_code_patterns character varying(2400),
    evidence_signals character varying(2400),
    knowledge_node_codes character varying(2400),
    prerequisite_knowledge_codes character varying(2400),
    related_knowledge_node_codes character varying(2400),
    CONSTRAINT ai_standard_library_items_layer_check CHECK (((layer)::text = ANY ((ARRAY['SKILL_UNIT'::character varying, 'MISTAKE_POINT'::character varying, 'BASIC_CAUSE'::character varying, 'IMPROVEMENT_POINT'::character varying])::text[])))
);


--
-- Name: ai_standard_library_items_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ai_standard_library_items_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ai_standard_library_items_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.ai_standard_library_items_id_seq OWNED BY public.ai_standard_library_items.id;


--
-- Name: ai_standard_library_legacy_mappings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ai_standard_library_legacy_mappings (
    confidence double precision NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    id bigint NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    legacy_layer character varying(40) NOT NULL,
    migration_status character varying(40) NOT NULL,
    target_type character varying(40) NOT NULL,
    source_version character varying(80),
    legacy_code character varying(160) NOT NULL,
    target_code character varying(160) NOT NULL,
    CONSTRAINT ai_standard_library_legacy_mappings_legacy_layer_check CHECK (((legacy_layer)::text = ANY ((ARRAY['SKILL_UNIT'::character varying, 'MISTAKE_POINT'::character varying, 'BASIC_CAUSE'::character varying, 'IMPROVEMENT_POINT'::character varying])::text[]))),
    CONSTRAINT ai_standard_library_legacy_mappings_target_type_check CHECK (((target_type)::text = ANY ((ARRAY['KNOWLEDGE_NODE'::character varying, 'SKILL_UNIT'::character varying, 'MISTAKE_POINT'::character varying, 'IMPROVEMENT_POINT'::character varying])::text[])))
);


--
-- Name: ai_standard_library_legacy_mappings_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ai_standard_library_legacy_mappings_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ai_standard_library_legacy_mappings_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.ai_standard_library_legacy_mappings_id_seq OWNED BY public.ai_standard_library_legacy_mappings.id;


--
-- Name: ai_standard_library_relations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ai_standard_library_relations (
    enabled boolean NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    id bigint NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    relation_type character varying(40) NOT NULL,
    source_type character varying(40) NOT NULL,
    target_type character varying(40) NOT NULL,
    source_code character varying(160) NOT NULL,
    target_code character varying(160) NOT NULL,
    description character varying(800),
    CONSTRAINT ai_standard_library_relations_relation_type_check CHECK (((relation_type)::text = ANY ((ARRAY['PREREQUISITE'::character varying, 'RELATED'::character varying, 'CONFUSABLE'::character varying, 'TRANSFER'::character varying, 'EXTENDS'::character varying])::text[]))),
    CONSTRAINT ai_standard_library_relations_source_type_check CHECK (((source_type)::text = ANY ((ARRAY['KNOWLEDGE_NODE'::character varying, 'SKILL_UNIT'::character varying, 'MISTAKE_POINT'::character varying, 'IMPROVEMENT_POINT'::character varying])::text[]))),
    CONSTRAINT ai_standard_library_relations_target_type_check CHECK (((target_type)::text = ANY ((ARRAY['KNOWLEDGE_NODE'::character varying, 'SKILL_UNIT'::character varying, 'MISTAKE_POINT'::character varying, 'IMPROVEMENT_POINT'::character varying])::text[])))
);


--
-- Name: ai_standard_library_relations_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ai_standard_library_relations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ai_standard_library_relations_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.ai_standard_library_relations_id_seq OWNED BY public.ai_standard_library_relations.id;


--
-- Name: ai_standard_mistake_points; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ai_standard_mistake_points (
    enabled boolean NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    id bigint NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    severity character varying(40),
    category character varying(80) NOT NULL,
    library_version character varying(80) NOT NULL,
    mistake_type character varying(80),
    name character varying(120) NOT NULL,
    code character varying(160) NOT NULL,
    primary_knowledge_node_code character varying(160) NOT NULL,
    skill_unit_code character varying(160) NOT NULL,
    applicable_languages character varying(800),
    repair_strategy character varying(1200),
    symptom character varying(1200),
    description character varying(1600),
    misconception character varying(1600),
    knowledge_node_codes character varying(2400),
    prerequisite_knowledge_codes character varying(2400)
);


--
-- Name: ai_standard_mistake_points_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ai_standard_mistake_points_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ai_standard_mistake_points_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.ai_standard_mistake_points_id_seq OWNED BY public.ai_standard_mistake_points.id;


--
-- Name: ai_standard_skill_units; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ai_standard_skill_units (
    enabled boolean NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    id bigint NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    mastery_level character varying(40),
    category character varying(80) NOT NULL,
    library_version character varying(80) NOT NULL,
    name character varying(120) NOT NULL,
    code character varying(160) NOT NULL,
    primary_knowledge_node_code character varying(160) NOT NULL,
    applicable_languages character varying(800),
    learning_goal character varying(1200),
    description character varying(1600),
    knowledge_node_codes character varying(2400),
    prerequisite_knowledge_codes character varying(2400)
);


--
-- Name: ai_standard_skill_units_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ai_standard_skill_units_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ai_standard_skill_units_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.ai_standard_skill_units_id_seq OWNED BY public.ai_standard_skill_units.id;


--
-- Name: assignment_invites; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.assignment_invites (
    enabled boolean NOT NULL,
    assignment_id bigint NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    expires_at timestamp(6) without time zone,
    id bigint NOT NULL,
    code character varying(255) NOT NULL
);


--
-- Name: assignment_invites_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.assignment_invites_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: assignment_invites_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.assignment_invites_id_seq OWNED BY public.assignment_invites.id;


--
-- Name: assignment_tasks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.assignment_tasks (
    order_index integer NOT NULL,
    required boolean NOT NULL,
    assignment_id bigint NOT NULL,
    id bigint NOT NULL,
    problem_id bigint NOT NULL
);


--
-- Name: assignment_tasks_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.assignment_tasks_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: assignment_tasks_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.assignment_tasks_id_seq OWNED BY public.assignment_tasks.id;


--
-- Name: assignments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.assignments (
    class_group_id bigint,
    created_at timestamp(6) without time zone NOT NULL,
    ends_at timestamp(6) without time zone,
    id bigint NOT NULL,
    starts_at timestamp(6) without time zone,
    description text,
    hint_policy character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    title character varying(255) NOT NULL,
    CONSTRAINT assignments_hint_policy_check CHECK (((hint_policy)::text = ANY ((ARRAY['L1'::character varying, 'L2'::character varying, 'L3'::character varying, 'L4'::character varying])::text[]))),
    CONSTRAINT assignments_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'ACTIVE'::character varying, 'CLOSED'::character varying])::text[])))
);


--
-- Name: assignments_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.assignments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: assignments_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.assignments_id_seq OWNED BY public.assignments.id;


--
-- Name: class_groups; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.class_groups (
    created_at timestamp(6) without time zone NOT NULL,
    id bigint NOT NULL,
    grade character varying(255),
    name character varying(255) NOT NULL,
    teacher_name character varying(255)
);


--
-- Name: class_groups_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.class_groups_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: class_groups_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.class_groups_id_seq OWNED BY public.class_groups.id;


--
-- Name: class_review_feedback; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.class_review_feedback (
    assignment_id bigint NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    example_problem_id bigint,
    id bigint NOT NULL,
    action_type character varying(255) NOT NULL,
    created_by character varying(255),
    evidence_tags text,
    suggestion_key character varying(255) NOT NULL,
    target_ability character varying(255),
    teacher_note text
);


--
-- Name: class_review_feedback_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.class_review_feedback_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: class_review_feedback_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.class_review_feedback_id_seq OWNED BY public.class_review_feedback.id;


--
-- Name: coach_prompts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.coach_prompts (
    turn_index integer,
    answered_at timestamp(6) without time zone,
    assignment_id bigint,
    created_at timestamp(6) without time zone NOT NULL,
    id bigint NOT NULL,
    parent_prompt_id bigint,
    student_profile_id bigint,
    submission_id bigint NOT NULL,
    coach_feedback text,
    context_summary text,
    evidence_refs text,
    hint_policy character varying(255) NOT NULL,
    model_answer_leak_risk character varying(255),
    model_failure_reason character varying(255),
    prompt_type character varying(255) NOT NULL,
    question text NOT NULL,
    rationale text,
    student_answer text
);


--
-- Name: coach_prompts_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.coach_prompts_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: coach_prompts_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.coach_prompts_id_seq OWNED BY public.coach_prompts.id;


--
-- Name: hint_safety_checks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.hint_safety_checks (
    checked_at timestamp(6) without time zone NOT NULL,
    id bigint NOT NULL,
    submission_id bigint NOT NULL,
    blocked_reasons_json text,
    original_hint text,
    risk_level character varying(255) NOT NULL,
    safe_hint text
);


--
-- Name: hint_safety_checks_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.hint_safety_checks_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: hint_safety_checks_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.hint_safety_checks_id_seq OWNED BY public.hint_safety_checks.id;


--
-- Name: informatics_knowledge_nodes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.informatics_knowledge_nodes (
    enabled boolean NOT NULL,
    sort_order integer NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    id bigint NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    type character varying(40) NOT NULL,
    difficulty character varying(80),
    library_version character varying(80) NOT NULL,
    stage character varying(80),
    name character varying(120) NOT NULL,
    code character varying(160) NOT NULL,
    parent_code character varying(160),
    path character varying(1200) NOT NULL,
    aliases character varying(1600),
    description character varying(1600),
    learning_objectives character varying(1600),
    prerequisites character varying(1600),
    typical_problems character varying(1600),
    CONSTRAINT informatics_knowledge_nodes_type_check CHECK (((type)::text = ANY ((ARRAY['DOMAIN'::character varying, 'CHAPTER'::character varying, 'SECTION'::character varying, 'TOPIC'::character varying, 'KNOWLEDGE_POINT'::character varying])::text[])))
);


--
-- Name: informatics_knowledge_nodes_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.informatics_knowledge_nodes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: informatics_knowledge_nodes_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.informatics_knowledge_nodes_id_seq OWNED BY public.informatics_knowledge_nodes.id;


--
-- Name: problems; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.problems (
    memory_limit integer NOT NULL,
    time_limit integer NOT NULL,
    created_at timestamp(6) without time zone,
    id bigint NOT NULL,
    ai_prompt_direction text,
    algorithm_strategies text,
    boundary_types text,
    common_mistakes text,
    description text NOT NULL,
    difficulty character varying(255) NOT NULL,
    knowledge_points text,
    starter_code text,
    title character varying(255) NOT NULL,
    CONSTRAINT problems_difficulty_check CHECK (((difficulty)::text = ANY ((ARRAY['EASY'::character varying, 'MEDIUM'::character varying, 'HARD'::character varying])::text[])))
);


--
-- Name: problems_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.problems_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: problems_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.problems_id_seq OWNED BY public.problems.id;


--
-- Name: student_ai_feedback_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_ai_feedback_events (
    assignment_id bigint,
    created_at timestamp(6) without time zone NOT NULL,
    feedback_revision_id bigint,
    id bigint NOT NULL,
    problem_id bigint,
    student_profile_id bigint,
    submission_id bigint NOT NULL,
    answer_leak_risk character varying(255),
    event_type character varying(255) NOT NULL,
    failure_reason text,
    feedback_source character varying(255),
    feedback_status character varying(255)
);


--
-- Name: student_ai_feedback_events_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.student_ai_feedback_events_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: student_ai_feedback_events_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.student_ai_feedback_events_id_seq OWNED BY public.student_ai_feedback_events.id;


--
-- Name: student_ai_feedback_revisions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_ai_feedback_revisions (
    diagnosis_run_version integer,
    version_number integer NOT NULL,
    analysis_id bigint,
    diagnosis_run_id bigint,
    feedback_id bigint,
    generated_at timestamp(6) without time zone NOT NULL,
    id bigint NOT NULL,
    submission_id bigint NOT NULL,
    source character varying(32) NOT NULL,
    status character varying(32) NOT NULL,
    generation_key character varying(64) NOT NULL,
    prompt_version character varying(120),
    provider character varying(120),
    schema_version character varying(120),
    model character varying(180),
    failure_reason text,
    feedback_json text
);


--
-- Name: student_ai_feedback_revisions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.student_ai_feedback_revisions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: student_ai_feedback_revisions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.student_ai_feedback_revisions_id_seq OWNED BY public.student_ai_feedback_revisions.id;


--
-- Name: student_ai_feedbacks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_ai_feedbacks (
    generated_at timestamp(6) without time zone,
    id bigint NOT NULL,
    latest_revision_id bigint,
    submission_id bigint NOT NULL,
    generation_key character varying(64),
    failure_reason text,
    feedback_json text,
    source character varying(255) NOT NULL,
    status character varying(255) NOT NULL
);


--
-- Name: student_ai_feedbacks_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.student_ai_feedbacks_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: student_ai_feedbacks_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.student_ai_feedbacks_id_seq OWNED BY public.student_ai_feedbacks.id;


--
-- Name: student_profiles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_profiles (
    class_group_id bigint,
    created_at timestamp(6) without time zone NOT NULL,
    id bigint NOT NULL,
    last_seen_at timestamp(6) without time zone NOT NULL,
    display_name character varying(255) NOT NULL,
    identity_key character varying(255) NOT NULL,
    note character varying(255),
    student_no character varying(255)
);


--
-- Name: student_profiles_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.student_profiles_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: student_profiles_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.student_profiles_id_seq OWNED BY public.student_profiles.id;


--
-- Name: student_recommendation_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_recommendation_events (
    assignment_id bigint,
    created_at timestamp(6) without time zone NOT NULL,
    followup_submission_id bigint,
    id bigint NOT NULL,
    problem_id bigint,
    student_profile_id bigint NOT NULL,
    event_type character varying(255) NOT NULL,
    expected_completion_signal text,
    fallback_action text,
    focus_ability character varying(255),
    focus_tags text,
    followup_fine_grained_tag character varying(255),
    followup_issue_tag character varying(255),
    followup_verdict character varying(255),
    learning_hypothesis text,
    recommendation_token character varying(255) NOT NULL,
    risk_level character varying(255),
    strategy character varying(255),
    type character varying(255) NOT NULL
);


--
-- Name: student_recommendation_events_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.student_recommendation_events_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: student_recommendation_events_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.student_recommendation_events_id_seq OWNED BY public.student_recommendation_events.id;


--
-- Name: submission_analyses; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.submission_analyses (
    generated_at timestamp(6) without time zone NOT NULL,
    id bigint NOT NULL,
    submission_id bigint NOT NULL,
    analysis_source character varying(255) NOT NULL,
    evidence_json text,
    headline character varying(255) NOT NULL,
    report_json text,
    report_markdown text NOT NULL,
    scenario character varying(255) NOT NULL,
    summary text NOT NULL
);


--
-- Name: submission_analyses_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.submission_analyses_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: submission_analyses_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.submission_analyses_id_seq OWNED BY public.submission_analyses.id;


--
-- Name: submission_case_results; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.submission_case_results (
    execution_time double precision,
    is_hidden boolean NOT NULL,
    memory_used integer,
    passed boolean NOT NULL,
    test_case_number integer NOT NULL,
    id bigint NOT NULL,
    submission_id bigint NOT NULL,
    actual_output text,
    expected_output text,
    input_snapshot text
);


--
-- Name: submission_case_results_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.submission_case_results_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: submission_case_results_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.submission_case_results_id_seq OWNED BY public.submission_case_results.id;


--
-- Name: submission_diagnosis_facts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.submission_diagnosis_facts (
    confidence double precision,
    primary_issue boolean NOT NULL,
    analysis_id bigint NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    id bigint NOT NULL,
    submission_id bigint NOT NULL,
    point_key_version character varying(24),
    display_category character varying(32),
    fact_type character varying(32) NOT NULL,
    knowledge_path_status character varying(32) NOT NULL,
    library_fit character varying(32),
    point_key_source character varying(32),
    projection_status character varying(32) NOT NULL,
    issue_id character varying(120),
    improvement_point_id character varying(160),
    mistake_point_id character varying(160),
    skill_unit_id character varying(160),
    fact_key character varying(180) NOT NULL,
    normalized_point_key character varying(220),
    title character varying(500),
    evidence_refs_json text,
    knowledge_path_json text
);


--
-- Name: submission_diagnosis_facts_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.submission_diagnosis_facts_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: submission_diagnosis_facts_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.submission_diagnosis_facts_id_seq OWNED BY public.submission_diagnosis_facts.id;


--
-- Name: submission_evidence_backfill_batches; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.submission_evidence_backfill_batches (
    dry_run boolean NOT NULL,
    completed_at timestamp(6) without time zone,
    cursor_end bigint,
    cursor_start bigint,
    failed_count bigint NOT NULL,
    id bigint NOT NULL,
    processed_count bigint NOT NULL,
    skipped_count bigint NOT NULL,
    started_at timestamp(6) without time zone NOT NULL,
    success_count bigint NOT NULL,
    batch_key character varying(64) NOT NULL,
    error_json text
);


--
-- Name: submission_evidence_backfill_batches_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.submission_evidence_backfill_batches_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: submission_evidence_backfill_batches_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.submission_evidence_backfill_batches_id_seq OWNED BY public.submission_evidence_backfill_batches.id;


--
-- Name: submission_issue_transitions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.submission_issue_transitions (
    effective_attempt boolean NOT NULL,
    affected_problem_count bigint NOT NULL,
    assignment_id bigint,
    consecutive_effective_count bigint NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    current_fact_id bigint,
    current_submission_id bigint NOT NULL,
    effective_occurrence_count bigint NOT NULL,
    first_seen_submission_id bigint,
    id bigint NOT NULL,
    last_seen_submission_id bigint,
    previous_fact_id bigint,
    previous_submission_id bigint,
    problem_id bigint NOT NULL,
    raw_occurrence_count bigint NOT NULL,
    student_profile_id bigint NOT NULL,
    projection_version character varying(24) NOT NULL,
    display_category character varying(32),
    fact_type character varying(32),
    point_key_source character varying(32),
    transition_type character varying(32) NOT NULL,
    personal_label character varying(48),
    source_fingerprint character varying(80),
    normalized_point_key character varying(220) NOT NULL,
    transition_key character varying(320) NOT NULL,
    title character varying(500),
    evidence_submission_ids_json text
);


--
-- Name: submission_issue_transitions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.submission_issue_transitions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: submission_issue_transitions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.submission_issue_transitions_id_seq OWNED BY public.submission_issue_transitions.id;


--
-- Name: submissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.submissions (
    execution_time double precision,
    language_id integer NOT NULL,
    memory_used integer,
    assignment_id bigint,
    id bigint NOT NULL,
    problem_id bigint NOT NULL,
    student_profile_id bigint,
    submitted_at timestamp(6) without time zone,
    compile_output text,
    error_message text,
    language_name character varying(255),
    output text,
    source_code text NOT NULL,
    verdict character varying(255),
    CONSTRAINT submissions_verdict_check CHECK (((verdict)::text = ANY ((ARRAY['PENDING'::character varying, 'ACCEPTED'::character varying, 'WRONG_ANSWER'::character varying, 'TIME_LIMIT_EXCEEDED'::character varying, 'MEMORY_LIMIT_EXCEEDED'::character varying, 'RUNTIME_ERROR'::character varying, 'COMPILATION_ERROR'::character varying, 'INTERNAL_ERROR'::character varying])::text[])))
);


--
-- Name: submissions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.submissions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: submissions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.submissions_id_seq OWNED BY public.submissions.id;


--
-- Name: teacher_diagnosis_corrections; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.teacher_diagnosis_corrections (
    eval_candidate boolean NOT NULL,
    assignment_id bigint NOT NULL,
    corrected_at timestamp(6) without time zone NOT NULL,
    id bigint NOT NULL,
    student_profile_id bigint,
    submission_id bigint NOT NULL,
    correction_type character varying(40),
    target_issue_id character varying(80),
    target_evidence_ref character varying(240),
    corrected_knowledge_path character varying(800),
    corrected_by character varying(255),
    corrected_fine_grained_tag character varying(255),
    corrected_issue_tag character varying(255) NOT NULL,
    original_fine_grained_tag character varying(255),
    original_issue_tag character varying(255),
    teacher_note text
);


--
-- Name: teacher_diagnosis_corrections_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.teacher_diagnosis_corrections_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: teacher_diagnosis_corrections_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.teacher_diagnosis_corrections_id_seq OWNED BY public.teacher_diagnosis_corrections.id;


--
-- Name: test_cases; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.test_cases (
    is_hidden boolean,
    order_index integer,
    id bigint NOT NULL,
    problem_id bigint NOT NULL,
    expected_output text NOT NULL,
    input text NOT NULL
);


--
-- Name: test_cases_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.test_cases_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: test_cases_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.test_cases_id_seq OWNED BY public.test_cases.id;


--
-- Name: ai_diagnosis_runs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_diagnosis_runs ALTER COLUMN id SET DEFAULT nextval('public.ai_diagnosis_runs_id_seq'::regclass);


--
-- Name: ai_diagnosis_stage_runs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_diagnosis_stage_runs ALTER COLUMN id SET DEFAULT nextval('public.ai_diagnosis_stage_runs_id_seq'::regclass);


--
-- Name: ai_standard_improvement_points id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_standard_improvement_points ALTER COLUMN id SET DEFAULT nextval('public.ai_standard_improvement_points_id_seq'::regclass);


--
-- Name: ai_standard_library_growth_candidates id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_standard_library_growth_candidates ALTER COLUMN id SET DEFAULT nextval('public.ai_standard_library_growth_candidates_id_seq'::regclass);


--
-- Name: ai_standard_library_items id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_standard_library_items ALTER COLUMN id SET DEFAULT nextval('public.ai_standard_library_items_id_seq'::regclass);


--
-- Name: ai_standard_library_legacy_mappings id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_standard_library_legacy_mappings ALTER COLUMN id SET DEFAULT nextval('public.ai_standard_library_legacy_mappings_id_seq'::regclass);


--
-- Name: ai_standard_library_relations id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_standard_library_relations ALTER COLUMN id SET DEFAULT nextval('public.ai_standard_library_relations_id_seq'::regclass);


--
-- Name: ai_standard_mistake_points id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_standard_mistake_points ALTER COLUMN id SET DEFAULT nextval('public.ai_standard_mistake_points_id_seq'::regclass);


--
-- Name: ai_standard_skill_units id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_standard_skill_units ALTER COLUMN id SET DEFAULT nextval('public.ai_standard_skill_units_id_seq'::regclass);


--
-- Name: assignment_invites id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assignment_invites ALTER COLUMN id SET DEFAULT nextval('public.assignment_invites_id_seq'::regclass);


--
-- Name: assignment_tasks id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assignment_tasks ALTER COLUMN id SET DEFAULT nextval('public.assignment_tasks_id_seq'::regclass);


--
-- Name: assignments id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assignments ALTER COLUMN id SET DEFAULT nextval('public.assignments_id_seq'::regclass);


--
-- Name: class_groups id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.class_groups ALTER COLUMN id SET DEFAULT nextval('public.class_groups_id_seq'::regclass);


--
-- Name: class_review_feedback id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.class_review_feedback ALTER COLUMN id SET DEFAULT nextval('public.class_review_feedback_id_seq'::regclass);


--
-- Name: coach_prompts id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.coach_prompts ALTER COLUMN id SET DEFAULT nextval('public.coach_prompts_id_seq'::regclass);


--
-- Name: hint_safety_checks id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hint_safety_checks ALTER COLUMN id SET DEFAULT nextval('public.hint_safety_checks_id_seq'::regclass);


--
-- Name: informatics_knowledge_nodes id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.informatics_knowledge_nodes ALTER COLUMN id SET DEFAULT nextval('public.informatics_knowledge_nodes_id_seq'::regclass);


--
-- Name: problems id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.problems ALTER COLUMN id SET DEFAULT nextval('public.problems_id_seq'::regclass);


--
-- Name: student_ai_feedback_events id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_ai_feedback_events ALTER COLUMN id SET DEFAULT nextval('public.student_ai_feedback_events_id_seq'::regclass);


--
-- Name: student_ai_feedback_revisions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_ai_feedback_revisions ALTER COLUMN id SET DEFAULT nextval('public.student_ai_feedback_revisions_id_seq'::regclass);


--
-- Name: student_ai_feedbacks id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_ai_feedbacks ALTER COLUMN id SET DEFAULT nextval('public.student_ai_feedbacks_id_seq'::regclass);


--
-- Name: student_profiles id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_profiles ALTER COLUMN id SET DEFAULT nextval('public.student_profiles_id_seq'::regclass);


--
-- Name: student_recommendation_events id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_recommendation_events ALTER COLUMN id SET DEFAULT nextval('public.student_recommendation_events_id_seq'::regclass);


--
-- Name: submission_analyses id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.submission_analyses ALTER COLUMN id SET DEFAULT nextval('public.submission_analyses_id_seq'::regclass);


--
-- Name: submission_case_results id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.submission_case_results ALTER COLUMN id SET DEFAULT nextval('public.submission_case_results_id_seq'::regclass);


--
-- Name: submission_diagnosis_facts id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.submission_diagnosis_facts ALTER COLUMN id SET DEFAULT nextval('public.submission_diagnosis_facts_id_seq'::regclass);


--
-- Name: submission_evidence_backfill_batches id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.submission_evidence_backfill_batches ALTER COLUMN id SET DEFAULT nextval('public.submission_evidence_backfill_batches_id_seq'::regclass);


--
-- Name: submission_issue_transitions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.submission_issue_transitions ALTER COLUMN id SET DEFAULT nextval('public.submission_issue_transitions_id_seq'::regclass);


--
-- Name: submissions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.submissions ALTER COLUMN id SET DEFAULT nextval('public.submissions_id_seq'::regclass);


--
-- Name: teacher_diagnosis_corrections id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teacher_diagnosis_corrections ALTER COLUMN id SET DEFAULT nextval('public.teacher_diagnosis_corrections_id_seq'::regclass);


--
-- Name: test_cases id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.test_cases ALTER COLUMN id SET DEFAULT nextval('public.test_cases_id_seq'::regclass);


--
-- Name: ai_diagnosis_runs ai_diagnosis_runs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_diagnosis_runs
    ADD CONSTRAINT ai_diagnosis_runs_pkey PRIMARY KEY (id);


--
-- Name: ai_diagnosis_stage_runs ai_diagnosis_stage_runs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_diagnosis_stage_runs
    ADD CONSTRAINT ai_diagnosis_stage_runs_pkey PRIMARY KEY (id);


--
-- Name: ai_standard_improvement_points ai_standard_improvement_points_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_standard_improvement_points
    ADD CONSTRAINT ai_standard_improvement_points_pkey PRIMARY KEY (id);


--
-- Name: ai_standard_library_growth_candidates ai_standard_library_growth_candidates_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_standard_library_growth_candidates
    ADD CONSTRAINT ai_standard_library_growth_candidates_pkey PRIMARY KEY (id);


--
-- Name: ai_standard_library_items ai_standard_library_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_standard_library_items
    ADD CONSTRAINT ai_standard_library_items_pkey PRIMARY KEY (id);


--
-- Name: ai_standard_library_legacy_mappings ai_standard_library_legacy_mappings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_standard_library_legacy_mappings
    ADD CONSTRAINT ai_standard_library_legacy_mappings_pkey PRIMARY KEY (id);


--
-- Name: ai_standard_library_relations ai_standard_library_relations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_standard_library_relations
    ADD CONSTRAINT ai_standard_library_relations_pkey PRIMARY KEY (id);


--
-- Name: ai_standard_mistake_points ai_standard_mistake_points_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_standard_mistake_points
    ADD CONSTRAINT ai_standard_mistake_points_pkey PRIMARY KEY (id);


--
-- Name: ai_standard_skill_units ai_standard_skill_units_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_standard_skill_units
    ADD CONSTRAINT ai_standard_skill_units_pkey PRIMARY KEY (id);


--
-- Name: assignment_invites assignment_invites_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assignment_invites
    ADD CONSTRAINT assignment_invites_code_key UNIQUE (code);


--
-- Name: assignment_invites assignment_invites_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assignment_invites
    ADD CONSTRAINT assignment_invites_pkey PRIMARY KEY (id);


--
-- Name: assignment_tasks assignment_tasks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assignment_tasks
    ADD CONSTRAINT assignment_tasks_pkey PRIMARY KEY (id);


--
-- Name: assignments assignments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assignments
    ADD CONSTRAINT assignments_pkey PRIMARY KEY (id);


--
-- Name: class_groups class_groups_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.class_groups
    ADD CONSTRAINT class_groups_pkey PRIMARY KEY (id);


--
-- Name: class_review_feedback class_review_feedback_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.class_review_feedback
    ADD CONSTRAINT class_review_feedback_pkey PRIMARY KEY (id);


--
-- Name: coach_prompts coach_prompts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.coach_prompts
    ADD CONSTRAINT coach_prompts_pkey PRIMARY KEY (id);


--
-- Name: hint_safety_checks hint_safety_checks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hint_safety_checks
    ADD CONSTRAINT hint_safety_checks_pkey PRIMARY KEY (id);


--
-- Name: informatics_knowledge_nodes informatics_knowledge_nodes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.informatics_knowledge_nodes
    ADD CONSTRAINT informatics_knowledge_nodes_pkey PRIMARY KEY (id);


--
-- Name: problems problems_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.problems
    ADD CONSTRAINT problems_pkey PRIMARY KEY (id);


--
-- Name: student_ai_feedback_events student_ai_feedback_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_ai_feedback_events
    ADD CONSTRAINT student_ai_feedback_events_pkey PRIMARY KEY (id);


--
-- Name: student_ai_feedback_revisions student_ai_feedback_revisions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_ai_feedback_revisions
    ADD CONSTRAINT student_ai_feedback_revisions_pkey PRIMARY KEY (id);


--
-- Name: student_ai_feedbacks student_ai_feedbacks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_ai_feedbacks
    ADD CONSTRAINT student_ai_feedbacks_pkey PRIMARY KEY (id);


--
-- Name: student_ai_feedbacks student_ai_feedbacks_submission_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_ai_feedbacks
    ADD CONSTRAINT student_ai_feedbacks_submission_id_key UNIQUE (submission_id);


--
-- Name: student_profiles student_profiles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_profiles
    ADD CONSTRAINT student_profiles_pkey PRIMARY KEY (id);


--
-- Name: student_recommendation_events student_recommendation_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_recommendation_events
    ADD CONSTRAINT student_recommendation_events_pkey PRIMARY KEY (id);


--
-- Name: submission_analyses submission_analyses_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.submission_analyses
    ADD CONSTRAINT submission_analyses_pkey PRIMARY KEY (id);


--
-- Name: submission_analyses submission_analyses_submission_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.submission_analyses
    ADD CONSTRAINT submission_analyses_submission_id_key UNIQUE (submission_id);


--
-- Name: submission_case_results submission_case_results_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.submission_case_results
    ADD CONSTRAINT submission_case_results_pkey PRIMARY KEY (id);


--
-- Name: submission_diagnosis_facts submission_diagnosis_facts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.submission_diagnosis_facts
    ADD CONSTRAINT submission_diagnosis_facts_pkey PRIMARY KEY (id);


--
-- Name: submission_evidence_backfill_batches submission_evidence_backfill_batches_batch_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.submission_evidence_backfill_batches
    ADD CONSTRAINT submission_evidence_backfill_batches_batch_key_key UNIQUE (batch_key);


--
-- Name: submission_evidence_backfill_batches submission_evidence_backfill_batches_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.submission_evidence_backfill_batches
    ADD CONSTRAINT submission_evidence_backfill_batches_pkey PRIMARY KEY (id);


--
-- Name: submission_issue_transitions submission_issue_transitions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.submission_issue_transitions
    ADD CONSTRAINT submission_issue_transitions_pkey PRIMARY KEY (id);


--
-- Name: submissions submissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.submissions
    ADD CONSTRAINT submissions_pkey PRIMARY KEY (id);


--
-- Name: teacher_diagnosis_corrections teacher_diagnosis_corrections_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teacher_diagnosis_corrections
    ADD CONSTRAINT teacher_diagnosis_corrections_pkey PRIMARY KEY (id);


--
-- Name: test_cases test_cases_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.test_cases
    ADD CONSTRAINT test_cases_pkey PRIMARY KEY (id);


--
-- Name: ai_diagnosis_runs uk_ai_diagnosis_run_generation; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_diagnosis_runs
    ADD CONSTRAINT uk_ai_diagnosis_run_generation UNIQUE (generation_key);


--
-- Name: ai_diagnosis_runs uk_ai_diagnosis_run_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_diagnosis_runs
    ADD CONSTRAINT uk_ai_diagnosis_run_version UNIQUE (submission_id, version_number);


--
-- Name: ai_diagnosis_stage_runs uk_ai_diagnosis_stage_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_diagnosis_stage_runs
    ADD CONSTRAINT uk_ai_diagnosis_stage_key UNIQUE (run_id, stage_key);


--
-- Name: ai_standard_improvement_points uk_ai_standard_improvement_point_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_standard_improvement_points
    ADD CONSTRAINT uk_ai_standard_improvement_point_code UNIQUE (code);


--
-- Name: ai_standard_library_items uk_ai_standard_library_layer_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_standard_library_items
    ADD CONSTRAINT uk_ai_standard_library_layer_code UNIQUE (layer, code);


--
-- Name: ai_standard_library_legacy_mappings uk_ai_standard_library_legacy_mapping; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_standard_library_legacy_mappings
    ADD CONSTRAINT uk_ai_standard_library_legacy_mapping UNIQUE (legacy_layer, legacy_code);


--
-- Name: ai_standard_library_relations uk_ai_standard_library_relation; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_standard_library_relations
    ADD CONSTRAINT uk_ai_standard_library_relation UNIQUE (source_type, source_code, relation_type, target_type, target_code);


--
-- Name: ai_standard_mistake_points uk_ai_standard_mistake_point_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_standard_mistake_points
    ADD CONSTRAINT uk_ai_standard_mistake_point_code UNIQUE (code);


--
-- Name: ai_standard_skill_units uk_ai_standard_skill_unit_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_standard_skill_units
    ADD CONSTRAINT uk_ai_standard_skill_unit_code UNIQUE (code);


--
-- Name: student_ai_feedback_revisions uk_feedback_revision_generation; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_ai_feedback_revisions
    ADD CONSTRAINT uk_feedback_revision_generation UNIQUE (submission_id, generation_key);


--
-- Name: informatics_knowledge_nodes uk_informatics_knowledge_node_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.informatics_knowledge_nodes
    ADD CONSTRAINT uk_informatics_knowledge_node_code UNIQUE (code);


--
-- Name: submission_diagnosis_facts uk_submission_diagnosis_fact_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.submission_diagnosis_facts
    ADD CONSTRAINT uk_submission_diagnosis_fact_key UNIQUE (fact_key);


--
-- Name: submission_issue_transitions uk_submission_issue_transition_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.submission_issue_transitions
    ADD CONSTRAINT uk_submission_issue_transition_key UNIQUE (transition_key);


--
-- Name: idx_ai_diagnosis_run_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ai_diagnosis_run_status ON public.ai_diagnosis_runs USING btree (status, updated_at);


--
-- Name: idx_ai_diagnosis_run_submission; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ai_diagnosis_run_submission ON public.ai_diagnosis_runs USING btree (submission_id, version_number);


--
-- Name: idx_ai_diagnosis_stage_run; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ai_diagnosis_stage_run ON public.ai_diagnosis_stage_runs USING btree (run_id, status);


--
-- Name: idx_ai_diagnosis_stage_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ai_diagnosis_stage_type ON public.ai_diagnosis_stage_runs USING btree (stage_type, status);


--
-- Name: idx_assignment_invites_assignment; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_assignment_invites_assignment ON public.assignment_invites USING btree (assignment_id);


--
-- Name: idx_assignment_tasks_assignment; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_assignment_tasks_assignment ON public.assignment_tasks USING btree (assignment_id);


--
-- Name: idx_assignment_tasks_problem; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_assignment_tasks_problem ON public.assignment_tasks USING btree (problem_id);


--
-- Name: idx_case_results_submission; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_case_results_submission ON public.submission_case_results USING btree (submission_id);


--
-- Name: idx_case_results_submission_case; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_case_results_submission_case ON public.submission_case_results USING btree (submission_id, test_case_number);


--
-- Name: idx_class_review_feedback_assignment; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_class_review_feedback_assignment ON public.class_review_feedback USING btree (assignment_id, created_at);


--
-- Name: idx_class_review_feedback_key; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_class_review_feedback_key ON public.class_review_feedback USING btree (assignment_id, suggestion_key);


--
-- Name: idx_coach_prompts_assignment_student; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_coach_prompts_assignment_student ON public.coach_prompts USING btree (assignment_id, student_profile_id, created_at);


--
-- Name: idx_coach_prompts_submission; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_coach_prompts_submission ON public.coach_prompts USING btree (submission_id, created_at);


--
-- Name: idx_diagnosis_fact_analysis; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_diagnosis_fact_analysis ON public.submission_diagnosis_facts USING btree (analysis_id);


--
-- Name: idx_diagnosis_fact_mistake; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_diagnosis_fact_mistake ON public.submission_diagnosis_facts USING btree (mistake_point_id);


--
-- Name: idx_diagnosis_fact_normalized_point; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_diagnosis_fact_normalized_point ON public.submission_diagnosis_facts USING btree (normalized_point_key, fact_type);


--
-- Name: idx_diagnosis_fact_path; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_diagnosis_fact_path ON public.submission_diagnosis_facts USING btree (knowledge_path_status, fact_type);


--
-- Name: idx_diagnosis_fact_skill; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_diagnosis_fact_skill ON public.submission_diagnosis_facts USING btree (skill_unit_id);


--
-- Name: idx_diagnosis_fact_submission; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_diagnosis_fact_submission ON public.submission_diagnosis_facts USING btree (submission_id);


--
-- Name: idx_evidence_backfill_started; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_evidence_backfill_started ON public.submission_evidence_backfill_batches USING btree (started_at);


--
-- Name: idx_feedback_revision_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_feedback_revision_status ON public.student_ai_feedback_revisions USING btree (status, generated_at);


--
-- Name: idx_feedback_revision_submission; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_feedback_revision_submission ON public.student_ai_feedback_revisions USING btree (submission_id, version_number);


--
-- Name: idx_issue_transition_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_issue_transition_scope ON public.submission_issue_transitions USING btree (student_profile_id, assignment_id, problem_id, current_submission_id);


--
-- Name: idx_issue_transition_student_point; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_issue_transition_student_point ON public.submission_issue_transitions USING btree (student_profile_id, normalized_point_key);


--
-- Name: idx_issue_transition_submission; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_issue_transition_submission ON public.submission_issue_transitions USING btree (current_submission_id);


--
-- Name: idx_reco_events_assignment; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reco_events_assignment ON public.student_recommendation_events USING btree (assignment_id, created_at);


--
-- Name: idx_reco_events_student; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reco_events_student ON public.student_recommendation_events USING btree (student_profile_id, created_at);


--
-- Name: idx_reco_events_submission; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reco_events_submission ON public.student_recommendation_events USING btree (followup_submission_id);


--
-- Name: idx_reco_events_token; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reco_events_token ON public.student_recommendation_events USING btree (recommendation_token);


--
-- Name: idx_student_ai_feedback_event_assignment; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_student_ai_feedback_event_assignment ON public.student_ai_feedback_events USING btree (assignment_id, created_at);


--
-- Name: idx_student_ai_feedback_event_student_problem; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_student_ai_feedback_event_student_problem ON public.student_ai_feedback_events USING btree (student_profile_id, problem_id, created_at);


--
-- Name: idx_student_ai_feedback_event_submission; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_student_ai_feedback_event_submission ON public.student_ai_feedback_events USING btree (submission_id, event_type, created_at);


--
-- Name: idx_student_profiles_class; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_student_profiles_class ON public.student_profiles USING btree (class_group_id);


--
-- Name: idx_student_profiles_identity; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_student_profiles_identity ON public.student_profiles USING btree (identity_key);


--
-- Name: idx_submissions_assignment_student_problem; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_submissions_assignment_student_problem ON public.submissions USING btree (assignment_id, student_profile_id, problem_id);


--
-- Name: idx_submissions_assignment_student_submitted_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_submissions_assignment_student_submitted_at ON public.submissions USING btree (assignment_id, student_profile_id, submitted_at);


--
-- Name: idx_submissions_problem_submitted_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_submissions_problem_submitted_at ON public.submissions USING btree (problem_id, submitted_at);


--
-- Name: idx_teacher_corrections_assignment; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_teacher_corrections_assignment ON public.teacher_diagnosis_corrections USING btree (assignment_id, corrected_at);


--
-- Name: idx_teacher_corrections_submission; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_teacher_corrections_submission ON public.teacher_diagnosis_corrections USING btree (submission_id, corrected_at);


--
-- PostgreSQL database dump complete
--
