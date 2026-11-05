-- V2__seed_reference_rest_api_chaining_workflow.sql
-- Reference Workflow: REST API Chaining & Auth Lifecycle
-- Demonstrates authenticating against an API, capturing Bearer tokens, injecting extracted variables into downstream requests, and asserting response codes and body payloads.

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
    '10000000-0000-0000-0000-000000000001'::uuid,
    '[Reference] REST API Chaining & Auth Lifecycle',
    'Demonstrates multi-step REST API orchestration: user authentication, Bearer token & User ID extraction, dynamic order submission using extracted variables, and final status verification.',
    1,
    'ACTIVE',
    '{
      "nodes": [
        {
          "id": "step_auth",
          "type": "httpRequest",
          "position": { "x": 180, "y": 80 },
          "data": {
            "name": "1. User Authentication",
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
            "assertions": [
              { "field": "status", "operator": "EQUALS", "expectedValue": "200" },
              { "field": "body.authenticated", "operator": "EQUALS", "expectedValue": "true" }
            ],
            "extractions": [
              { "variableName": "authToken", "source": "RESPONSE_BODY", "jsonPath": "$.token" },
              { "variableName": "userId", "source": "RESPONSE_BODY", "jsonPath": "$.userId" }
            ]
          }
        },
        {
          "id": "step_create_order",
          "type": "httpRequest",
          "position": { "x": 180, "y": 280 },
          "data": {
            "name": "2. Submit Order",
            "method": "POST",
            "url": "http://localhost:8080/api/v1/mock/orders",
            "headers": {
              "Authorization": "Bearer ${authToken}",
              "Content-Type": "application/json"
            },
            "body": {
              "userId": "${userId}",
              "productId": "PROD-1001",
              "quantity": 2,
              "totalPrice": 149.99
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
          "id": "step_get_order",
          "type": "httpRequest",
          "position": { "x": 180, "y": 480 },
          "data": {
            "name": "3. Query Order Details",
            "method": "GET",
            "url": "http://localhost:8080/api/v1/mock/orders/${orderId}",
            "headers": {
              "Authorization": "Bearer ${authToken}"
            },
            "expectedStatus": 200,
            "assertions": [
              { "field": "status", "operator": "EQUALS", "expectedValue": "200" },
              { "field": "body.userId", "operator": "EQUALS", "expectedValue": "${userId}" }
            ]
          }
        }
      ],
      "edges": [
        {
          "id": "edge_auth_to_order",
          "source": "step_auth",
          "target": "step_create_order",
          "animated": true
        },
        {
          "id": "edge_order_to_get",
          "source": "step_create_order",
          "target": "step_get_order",
          "animated": true
        }
      ],
      "viewport": { "x": 0, "y": 0, "zoom": 1 }
    }'::jsonb,
    '{
      "steps": [
        {
          "id": "step_auth",
          "name": "1. User Authentication",
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
            { "field": "status", "operator": "EQUALS", "expectedValue": "200" },
            { "field": "body.authenticated", "operator": "EQUALS", "expectedValue": "true" }
          ],
          "extractions": [
            { "variableName": "authToken", "source": "RESPONSE_BODY", "jsonPath": "$.token" },
            { "variableName": "userId", "source": "RESPONSE_BODY", "jsonPath": "$.userId" }
          ]
        },
        {
          "id": "step_create_order",
          "name": "2. Submit Order",
          "type": "HTTP_REQUEST",
          "enabled": true,
          "httpConfig": {
            "method": "POST",
            "url": "http://localhost:8080/api/v1/mock/orders",
            "headers": {
              "Authorization": "Bearer ${authToken}",
              "Content-Type": "application/json"
            },
            "body": {
              "userId": "${userId}",
              "productId": "PROD-1001",
              "quantity": 2,
              "totalPrice": 149.99
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
          "id": "step_get_order",
          "name": "3. Query Order Details",
          "type": "HTTP_REQUEST",
          "enabled": true,
          "httpConfig": {
            "method": "GET",
            "url": "http://localhost:8080/api/v1/mock/orders/${orderId}",
            "headers": {
              "Authorization": "Bearer ${authToken}"
            }
          },
          "assertions": [
            { "field": "status", "operator": "EQUALS", "expectedValue": "200" },
            { "field": "body.userId", "operator": "EQUALS", "expectedValue": "${userId}" }
          ],
          "extractions": []
        }
      ],
      "variables": {}
    }'::jsonb,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
