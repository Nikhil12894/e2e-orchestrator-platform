-- V1__init_orchestrator_schema.sql
-- Production-Ready Schema for E2E Test Orchestration Platform

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. API Endpoint Catalog: Stores parsed OpenAPI operations & generated sample payloads
CREATE TABLE IF NOT EXISTS api_endpoint_catalog (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_name VARCHAR(100) NOT NULL,
    endpoint_path VARCHAR(500) NOT NULL,
    http_method VARCHAR(10) NOT NULL,
    operation_id VARCHAR(200),
    summary TEXT,
    description TEXT,
    tags JSONB NOT NULL DEFAULT '[]'::jsonb,
    parameters JSONB NOT NULL DEFAULT '[]'::jsonb,
    request_body_schema JSONB NOT NULL DEFAULT '{}'::jsonb,
    sample_request_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    response_schemas JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_endpoint_operation UNIQUE (service_name, endpoint_path, http_method)
);

CREATE INDEX IF NOT EXISTS idx_catalog_service ON api_endpoint_catalog(service_name);
CREATE INDEX IF NOT EXISTS idx_catalog_path ON api_endpoint_catalog(endpoint_path);
CREATE INDEX IF NOT EXISTS idx_catalog_method ON api_endpoint_catalog(http_method);
CREATE INDEX IF NOT EXISTS idx_catalog_tags ON api_endpoint_catalog USING GIN (tags);

-- 2. Test Workflows: Stores visual workflow AST and UI canvas state
CREATE TABLE IF NOT EXISTS test_workflows (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    version INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    canvas_state JSONB NOT NULL DEFAULT '{"nodes":[],"edges":[],"viewport":{"x":0,"y":0,"zoom":1}}'::jsonb,
    workflow_ast JSONB NOT NULL DEFAULT '{"steps":[],"variables":{}}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_workflow_name ON test_workflows(name);
CREATE INDEX IF NOT EXISTS idx_workflow_status ON test_workflows(status);

-- 3. Workflow Executions: Tracks runs, statuses, reports and step evaluation states
CREATE TABLE IF NOT EXISTS workflow_executions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id UUID REFERENCES test_workflows(id) ON DELETE SET NULL,
    workflow_name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    trigger_type VARCHAR(50) NOT NULL DEFAULT 'MANUAL',
    start_time TIMESTAMP WITH TIME ZONE,
    end_time TIMESTAMP WITH TIME ZONE,
    duration_ms BIGINT,
    context_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    step_results JSONB NOT NULL DEFAULT '[]'::jsonb,
    report_path VARCHAR(1000),
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_execution_workflow_id ON workflow_executions(workflow_id);
CREATE INDEX IF NOT EXISTS idx_execution_status ON workflow_executions(status);
CREATE INDEX IF NOT EXISTS idx_execution_created_at ON workflow_executions(created_at DESC);
