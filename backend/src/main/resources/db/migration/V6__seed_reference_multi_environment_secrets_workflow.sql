-- V6__seed_reference_multi_environment_secrets_workflow.sql
-- Reference Workflow: Multi-Environment Configuration & Secret Masking
-- Demonstrates multi-environment portability across default, staging, and production with dynamic variable scoping (${$env:VAR}), secrets (${$secret:KEY}), and automated log redaction ([REDACTED]).

INSERT INTO test_workflows (
    id,
    name,
    description,
    version,
    status,
    canvas_state,
    workflow_ast,
    created_at,
    updated_at
) VALUES (
    '10000000-0000-0000-0000-000000000005'::uuid,
    '[Reference] Multi-Environment Configuration & Secret Masking',
    'Demonstrates environment-scoped dynamic properties and secrets: configure distinct values across default, staging, and production environments with zero workflow modification, dynamic ${$env:VAR} and ${$secret:KEY} resolution, and automated log masking.',
    1,
    'ACTIVE',
    '{
      "nodes": [
        {
          "id": "step_env_http",
          "type": "httpRequest",
          "position": { "x": 180, "y": 80 },
          "data": {
            "name": "1. Scoped Environment HTTP Request",
            "method": "GET",
            "url": "${$env:BASE_URL}/api/v1/mock/orders",
            "headers": {
              "Authorization": "Bearer ${$secret:API_KEY}",
              "X-Deployment-Region": "${$env:REGION}"
            },
            "expectedStatus": 200,
            "assertions": [
              { "field": "status", "operator": "EQUALS", "expectedValue": "200" }
            ]
          }
        },
        {
          "id": "step_env_db",
          "type": "dbPoll",
          "position": { "x": 180, "y": 300 },
          "data": {
            "name": "2. Scoped Database Health Verification",
            "jdbcUrl": "jdbc:h2:mem:env_test;DB_CLOSE_DELAY=-1",
            "username": "sa",
            "password": "${$secret:DB_PASS}",
            "driverClassName": "org.h2.Driver",
            "query": "SELECT 1 AS health_check, ''ACTIVE'' AS status",
            "condition": "row.health_check == 1",
            "timeoutMs": 5000,
            "pollIntervalMs": 500,
            "maxAttempts": 2,
            "extractions": [
              { "variableName": "dbStatus", "source": "DB_ROW_COLUMN", "jsonPath": "status" }
            ]
          }
        }
      ],
      "edges": [
        {
          "id": "edge_env_http_to_db",
          "source": "step_env_http",
          "target": "step_env_db",
          "animated": true
        }
      ],
      "envProperties": {
        "default": { "BASE_URL": "http://localhost:8080", "DB_HOST": "localhost", "REGION": "local-dev" },
        "staging": { "BASE_URL": "http://localhost:8080", "DB_HOST": "stg-db.internal", "REGION": "us-east-1" },
        "production": { "BASE_URL": "http://localhost:8080", "DB_HOST": "prod-db.internal", "REGION": "us-west-2" }
      },
      "envSecrets": {
        "default": { "API_KEY": "dev-token-abc123xyz", "DB_PASS": "dev-pass-456" },
        "staging": { "API_KEY": "stg-vault-key-789", "DB_PASS": "stg-super-secret" },
        "production": { "API_KEY": "prod-hsm-key-9999", "DB_PASS": "prod-enterprise-vault" }
      },
      "viewport": { "x": 0, "y": 0, "zoom": 1 }
    }'::jsonb,
    '{
      "steps": [
        {
          "id": "step_env_http",
          "name": "1. Scoped Environment HTTP Request",
          "type": "HTTP_REQUEST",
          "enabled": true,
          "httpConfig": {
            "method": "GET",
            "url": "${$env:BASE_URL}/api/v1/mock/orders",
            "headers": {
              "Authorization": "Bearer ${$secret:API_KEY}",
              "X-Deployment-Region": "${$env:REGION}"
            }
          },
          "assertions": [
            { "field": "status", "operator": "EQUALS", "expectedValue": "200" }
          ],
          "extractions": []
        },
        {
          "id": "step_env_db",
          "name": "2. Scoped Database Health Verification",
          "type": "DB_POLL",
          "enabled": true,
          "dbPollConfig": {
            "jdbcUrl": "jdbc:h2:mem:env_test;DB_CLOSE_DELAY=-1",
            "username": "sa",
            "password": "${$secret:DB_PASS}",
            "driverClassName": "org.h2.Driver",
            "query": "SELECT 1 AS health_check, ''ACTIVE'' AS status",
            "condition": "row.health_check == 1",
            "timeoutMs": 5000,
            "pollIntervalMs": 500,
            "maxAttempts": 2
          },
          "assertions": [],
          "extractions": [
            { "variableName": "dbStatus", "source": "DB_ROW_COLUMN", "jsonPath": "status" }
          ]
        }
      ],
      "envProperties": {
        "default": { "BASE_URL": "http://localhost:8080", "DB_HOST": "localhost", "REGION": "local-dev" },
        "staging": { "BASE_URL": "http://localhost:8080", "DB_HOST": "stg-db.internal", "REGION": "us-east-1" },
        "production": { "BASE_URL": "http://localhost:8080", "DB_HOST": "prod-db.internal", "REGION": "us-west-2" }
      },
      "envSecrets": {
        "default": { "API_KEY": "dev-token-abc123xyz", "DB_PASS": "dev-pass-456" },
        "staging": { "API_KEY": "stg-vault-key-789", "DB_PASS": "stg-super-secret" },
        "production": { "API_KEY": "prod-hsm-key-9999", "DB_PASS": "prod-enterprise-vault" }
      },
      "variables": {}
    }'::jsonb,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
