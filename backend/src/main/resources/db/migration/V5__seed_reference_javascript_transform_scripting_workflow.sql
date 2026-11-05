-- V5__seed_reference_javascript_transform_scripting_workflow.sql
-- Reference Workflow: JavaScript Data Reshaping & Dynamic Scripting
-- Demonstrates in-memory JavaScript execution via function(ctx) to generate synthetic nonces, ISO timestamps, masked tokens, and feed reshaped payloads downstream.

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
    '10000000-0000-0000-0000-000000000004'::uuid,
    '[Reference] JavaScript Data Reshaping & Dynamic Scripting',
    'Demonstrates custom JavaScript transformation steps: capture raw upstream API data, execute in-memory function(ctx) to compute timestamps, dynamic nonces, and masked fields, and forward reshaped data to downstream APIs.',
    1,
    'ACTIVE',
    '{
      "nodes": [
        {
          "id": "step_fetch_source",
          "type": "httpRequest",
          "position": { "x": 180, "y": 80 },
          "data": {
            "name": "1. Fetch Source Order Data",
            "method": "GET",
            "url": "http://localhost:8080/api/v1/mock/orders",
            "expectedStatus": 200,
            "extractions": [
              { "variableName": "rawOrderId", "source": "RESPONSE_BODY", "jsonPath": "$.orderId", "defaultValue": "ORD-SAMPLE-99" }
            ]
          }
        },
        {
          "id": "step_transform_payload",
          "type": "transform",
          "position": { "x": 180, "y": 280 },
          "data": {
            "name": "2. JavaScript Reshape & Nonce Generator",
            "script": "function(ctx) {\n  var raw = ctx.rawOrderId || ''ORD-DEFAULT'';\n  return {\n    isoTimestamp: new Date().toISOString(),\n    requestNonce: Math.random().toString(36).substring(2, 10),\n    maskedOrderId: raw.substring(0, 4) + ''****''\n  };\n}"
          }
        },
        {
          "id": "step_dispatch_reshaped",
          "type": "httpRequest",
          "position": { "x": 180, "y": 480 },
          "data": {
            "name": "3. Dispatch Reshaped Payload",
            "method": "POST",
            "url": "http://localhost:8080/api/v1/mock/orders",
            "headers": {
              "Content-Type": "application/json",
              "X-Request-Nonce": "${requestNonce}",
              "X-Timestamp": "${isoTimestamp}"
            },
            "body": {
              "orderRef": "${maskedOrderId}",
              "isProcessed": true
            },
            "expectedStatus": 201,
            "assertions": [
              { "field": "status", "operator": "EQUALS", "expectedValue": "201" }
            ]
          }
        }
      ],
      "edges": [
        {
          "id": "edge_fetch_to_transform",
          "source": "step_fetch_source",
          "target": "step_transform_payload",
          "animated": true
        },
        {
          "id": "edge_transform_to_dispatch",
          "source": "step_transform_payload",
          "target": "step_dispatch_reshaped",
          "animated": true
        }
      ],
      "viewport": { "x": 0, "y": 0, "zoom": 1 }
    }'::jsonb,
    '{
      "steps": [
        {
          "id": "step_fetch_source",
          "name": "1. Fetch Source Order Data",
          "type": "HTTP_REQUEST",
          "enabled": true,
          "httpConfig": {
            "method": "GET",
            "url": "http://localhost:8080/api/v1/mock/orders"
          },
          "assertions": [
            { "field": "status", "operator": "EQUALS", "expectedValue": "200" }
          ],
          "extractions": [
            { "variableName": "rawOrderId", "source": "RESPONSE_BODY", "jsonPath": "$.orderId", "defaultValue": "ORD-SAMPLE-99" }
          ]
        },
        {
          "id": "step_transform_payload",
          "name": "2. JavaScript Reshape & Nonce Generator",
          "type": "TRANSFORM",
          "enabled": true,
          "transformConfig": {
            "script": "function(ctx) {\n  var raw = ctx.rawOrderId || ''ORD-DEFAULT'';\n  return {\n    isoTimestamp: new Date().toISOString(),\n    requestNonce: Math.random().toString(36).substring(2, 10),\n    maskedOrderId: raw.substring(0, 4) + ''****''\n  };\n}"
          },
          "assertions": [],
          "extractions": []
        },
        {
          "id": "step_dispatch_reshaped",
          "name": "3. Dispatch Reshaped Payload",
          "type": "HTTP_REQUEST",
          "enabled": true,
          "httpConfig": {
            "method": "POST",
            "url": "http://localhost:8080/api/v1/mock/orders",
            "headers": {
              "Content-Type": "application/json",
              "X-Request-Nonce": "${requestNonce}",
              "X-Timestamp": "${isoTimestamp}"
            },
            "body": {
              "orderRef": "${maskedOrderId}",
              "isProcessed": true
            }
          },
          "assertions": [
            { "field": "status", "operator": "EQUALS", "expectedValue": "201" }
          ],
          "extractions": []
        }
      ],
      "variables": {}
    }'::jsonb,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
