-- V7__seed_reference_reusable_auth_child_subworkflow.sql
-- Reference Workflow: Reusable Auth & Session Component (Child Sub-Workflow)
-- Demonstrates a modular, standalone component designed to be executed as a nested child step inside parent workflows, providing tokens and session metadata.

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
    '10000000-0000-0000-0000-000000000006'::uuid,
    '[Reference] Reusable Auth & Session Component (Child Sub-Workflow)',
    'A modular reusable child workflow designed for composition: authenticates credentials against the auth service, initializes active session tokens and roles, and exposes runtime variables to calling parent workflows.',
    1,
    'ACTIVE',
    '{
      "nodes": [
        {
          "id": "step_child_login",
          "type": "httpRequest",
          "position": { "x": 180, "y": 80 },
          "data": {
            "name": "1. Authenticate Credentials",
            "method": "POST",
            "url": "http://localhost:8080/api/v1/mock/auth/login",
            "headers": {
              "Content-Type": "application/json"
            },
            "body": {
              "username": "qa_tester",
              "password": "Secret123!"
            },
            "expectedStatus": 200,
            "extractions": [
              { "variableName": "authToken", "source": "RESPONSE_BODY", "jsonPath": "$.token" }
            ]
          }
        },
        {
          "id": "step_child_session",
          "type": "transform",
          "position": { "x": 180, "y": 280 },
          "data": {
            "name": "2. Generate Session Context",
            "script": "function(ctx) {\n  return {\n    sessionUuid: ''sess-'' + Math.random().toString(36).substring(2, 12),\n    sessionCreated: new Date().toISOString(),\n    userRole: ''ROLE_ADMIN''\n  };\n}"
          }
        }
      ],
      "edges": [
        {
          "id": "edge_child_login_to_session",
          "source": "step_child_login",
          "target": "step_child_session",
          "animated": true
        }
      ],
      "viewport": { "x": 0, "y": 0, "zoom": 1 }
    }'::jsonb,
    '{
      "steps": [
        {
          "id": "step_child_login",
          "name": "1. Authenticate Credentials",
          "type": "HTTP_REQUEST",
          "enabled": true,
          "httpConfig": {
            "method": "POST",
            "url": "http://localhost:8080/api/v1/mock/auth/login",
            "headers": {
              "Content-Type": "application/json"
            },
            "body": {
              "username": "qa_tester",
              "password": "Secret123!"
            }
          },
          "assertions": [
            { "field": "status", "operator": "EQUALS", "expectedValue": "200" }
          ],
          "extractions": [
            { "variableName": "authToken", "source": "RESPONSE_BODY", "jsonPath": "$.token" }
          ]
        },
        {
          "id": "step_child_session",
          "name": "2. Generate Session Context",
          "type": "TRANSFORM",
          "enabled": true,
          "transformConfig": {
            "script": "function(ctx) {\n  return {\n    sessionUuid: ''sess-'' + Math.random().toString(36).substring(2, 12),\n    sessionCreated: new Date().toISOString(),\n    userRole: ''ROLE_ADMIN''\n  };\n}"
          },
          "assertions": [],
          "extractions": []
        }
      ],
      "variables": {}
    }'::jsonb,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
