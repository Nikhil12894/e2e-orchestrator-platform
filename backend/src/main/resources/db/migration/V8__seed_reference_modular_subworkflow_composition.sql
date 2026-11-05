-- V8__seed_reference_modular_subworkflow_composition.sql
-- Reference Workflow: Master Orchestrator (Sub-Workflow Composition)
-- Demonstrates calling another configured workflow as a modular sub-step, inheriting parent runtime variables, environment properties, and flow headers, and extracting child outputs for downstream steps.

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
    '10000000-0000-0000-0000-000000000007'::uuid,
    '[Reference] Master Orchestrator (Sub-Workflow Composition)',
    'Demonstrates hierarchical workflow composition: executes the reusable Auth sub-workflow as a modular child step, inherits environment properties and secrets with log masking, captures extracted tokens, and uses them in downstream business transactions.',
    1,
    'ACTIVE',
    '{
      "nodes": [
        {
          "id": "step_call_auth_subflow",
          "type": "subWorkflow",
          "position": { "x": 180, "y": 80 },
          "data": {
            "name": "1. Execute Reusable Auth Sub-Workflow",
            "workflowId": "10000000-0000-0000-0000-000000000006",
            "workflowName": "[Reference] Reusable Auth & Session Component (Child Sub-Workflow)",
            "inheritParentContext": true,
            "inheritEnvAndSecrets": true,
            "inheritFlowHeaders": true,
            "inputVariables": {
              "clientApp": "CustomerPortal",
              "traceOrigin": "MasterOrchestrator"
            },
            "extractions": [
              { "variableName": "extractedToken", "source": "SUB_WORKFLOW_VARIABLE", "jsonPath": "authToken" },
              { "variableName": "extractedSession", "source": "SUB_WORKFLOW_VARIABLE", "jsonPath": "sessionUuid" }
            ]
          }
        },
        {
          "id": "step_execute_business_tx",
          "type": "httpRequest",
          "position": { "x": 180, "y": 300 },
          "data": {
            "name": "2. Submit Order using Sub-Workflow Token",
            "method": "POST",
            "url": "http://localhost:8080/api/v1/mock/orders",
            "headers": {
              "Authorization": "Bearer ${extractedToken}",
              "X-Session-ID": "${extractedSession}",
              "Content-Type": "application/json"
            },
            "body": {
              "productId": "PROD-COMPOSE-9",
              "quantity": 1,
              "totalPrice": 79.99
            },
            "expectedStatus": 201,
            "assertions": [
              { "field": "status", "operator": "EQUALS", "expectedValue": "201" }
            ],
            "extractions": [
              { "variableName": "orderId", "source": "RESPONSE_BODY", "jsonPath": "$.orderId" }
            ]
          }
        },
        {
          "id": "step_verify_completion",
          "type": "httpRequest",
          "position": { "x": 180, "y": 500 },
          "data": {
            "name": "3. Verify Order with Sub-Workflow Context",
            "method": "GET",
            "url": "http://localhost:8080/api/v1/mock/orders/${orderId}",
            "headers": {
              "Authorization": "Bearer ${extractedToken}",
              "X-Session-ID": "${extractedSession}"
            },
            "expectedStatus": 200,
            "assertions": [
              { "field": "status", "operator": "EQUALS", "expectedValue": "200" }
            ]
          }
        }
      ],
      "edges": [
        {
          "id": "edge_subflow_to_order",
          "source": "step_call_auth_subflow",
          "target": "step_execute_business_tx",
          "animated": true
        },
        {
          "id": "edge_order_to_verify",
          "source": "step_execute_business_tx",
          "target": "step_verify_completion",
          "animated": true
        }
      ],
      "viewport": { "x": 0, "y": 0, "zoom": 1 }
    }'::jsonb,
    '{
      "steps": [
        {
          "id": "step_call_auth_subflow",
          "name": "1. Execute Reusable Auth Sub-Workflow",
          "type": "SUB_WORKFLOW",
          "enabled": true,
          "subWorkflowConfig": {
            "workflowId": "10000000-0000-0000-0000-000000000006",
            "workflowName": "[Reference] Reusable Auth & Session Component (Child Sub-Workflow)",
            "inheritParentContext": true,
            "inheritEnvAndSecrets": true,
            "inheritFlowHeaders": true,
            "inputVariables": {
              "clientApp": "CustomerPortal",
              "traceOrigin": "MasterOrchestrator"
            }
          },
          "assertions": [],
          "extractions": [
            { "variableName": "extractedToken", "source": "SUB_WORKFLOW_VARIABLE", "jsonPath": "authToken" },
            { "variableName": "extractedSession", "source": "SUB_WORKFLOW_VARIABLE", "jsonPath": "sessionUuid" }
          ]
        },
        {
          "id": "step_execute_business_tx",
          "name": "2. Submit Order using Sub-Workflow Token",
          "type": "HTTP_REQUEST",
          "enabled": true,
          "httpConfig": {
            "method": "POST",
            "url": "http://localhost:8080/api/v1/mock/orders",
            "headers": {
              "Authorization": "Bearer ${extractedToken}",
              "X-Session-ID": "${extractedSession}",
              "Content-Type": "application/json"
            },
            "body": {
              "productId": "PROD-COMPOSE-9",
              "quantity": 1,
              "totalPrice": 79.99
            }
          },
          "assertions": [
            { "field": "status", "operator": "EQUALS", "expectedValue": "201" }
          ],
          "extractions": [
            { "variableName": "orderId", "source": "RESPONSE_BODY", "jsonPath": "$.orderId" }
          ]
        },
        {
          "id": "step_verify_completion",
          "name": "3. Verify Order with Sub-Workflow Context",
          "type": "HTTP_REQUEST",
          "enabled": true,
          "httpConfig": {
            "method": "GET",
            "url": "http://localhost:8080/api/v1/mock/orders/${orderId}",
            "headers": {
              "Authorization": "Bearer ${extractedToken}",
              "X-Session-ID": "${extractedSession}"
            }
          },
          "assertions": [
            { "field": "status", "operator": "EQUALS", "expectedValue": "200" }
          ],
          "extractions": []
        }
      ],
      "variables": {}
    }'::jsonb,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
