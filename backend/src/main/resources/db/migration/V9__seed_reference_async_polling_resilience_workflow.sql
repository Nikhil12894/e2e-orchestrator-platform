-- V9__seed_reference_async_polling_resilience_workflow.sql
-- Reference Workflow: Async Job Polling & Resilient Retry Policies
-- Demonstrates async job execution, non-blocking virtual thread delay pause, native Karate HTTP retry polling with interval backoff, and auxiliary steps with continue-on-failure and report exclusion.

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
    '10000000-0000-0000-0000-000000000008'::uuid,
    '[Reference] Async Job Polling & Resilient Retry Policies',
    'Demonstrates resilient async testing patterns: trigger long-running background tasks, pause with non-blocking delay, poll HTTP endpoints with automated retry attempts and interval backoff, and execute auxiliary cleanup steps without impacting the report scorecard.',
    1,
    'ACTIVE',
    '{
      "nodes": [
        {
          "id": "step_trigger_job",
          "type": "httpRequest",
          "position": { "x": 180, "y": 80 },
          "data": {
            "name": "1. Launch Async Background Job",
            "method": "POST",
            "url": "http://localhost:8080/api/v1/mock/orders",
            "headers": {
              "Content-Type": "application/json"
            },
            "body": {
              "jobType": "DATA_AGGREGATION",
              "priority": "HIGH"
            },
            "expectedStatus": 201,
            "extractions": [
              { "variableName": "jobId", "source": "RESPONSE_BODY", "jsonPath": "$.orderId" }
            ]
          }
        },
        {
          "id": "step_convergence_delay",
          "type": "delay",
          "position": { "x": 180, "y": 280 },
          "data": {
            "name": "2. State Convergence Delay (2s)",
            "delayMs": 2000
          }
        },
        {
          "id": "step_poll_status",
          "type": "httpRequest",
          "position": { "x": 180, "y": 480 },
          "data": {
            "name": "3. Poll Job Status with Retry Policy",
            "method": "GET",
            "url": "http://localhost:8080/api/v1/mock/orders/${jobId}",
            "retryCount": 5,
            "retryIntervalMs": 1000,
            "expectedStatus": 200,
            "assertions": [
              { "field": "status", "operator": "EQUALS", "expectedValue": "200" }
            ]
          }
        },
        {
          "id": "step_auxiliary_cleanup",
          "type": "httpRequest",
          "position": { "x": 180, "y": 680 },
          "data": {
            "name": "4. Auxiliary Cleanup (Tolerant & Excluded)",
            "method": "DELETE",
            "url": "http://localhost:8080/api/v1/mock/orders/${jobId}",
            "continueOnFailure": true,
            "excludeFromReport": true,
            "enabled": true
          }
        }
      ],
      "edges": [
        {
          "id": "edge_trigger_to_delay",
          "source": "step_trigger_job",
          "target": "step_convergence_delay",
          "animated": true
        },
        {
          "id": "edge_delay_to_poll",
          "source": "step_convergence_delay",
          "target": "step_poll_status",
          "animated": true
        },
        {
          "id": "edge_poll_to_cleanup",
          "source": "step_poll_status",
          "target": "step_auxiliary_cleanup",
          "animated": true
        }
      ],
      "viewport": { "x": 0, "y": 0, "zoom": 1 }
    }'::jsonb,
    '{
      "steps": [
        {
          "id": "step_trigger_job",
          "name": "1. Launch Async Background Job",
          "type": "HTTP_REQUEST",
          "enabled": true,
          "httpConfig": {
            "method": "POST",
            "url": "http://localhost:8080/api/v1/mock/orders",
            "headers": {
              "Content-Type": "application/json"
            },
            "body": {
              "jobType": "DATA_AGGREGATION",
              "priority": "HIGH"
            }
          },
          "assertions": [
            { "field": "status", "operator": "EQUALS", "expectedValue": "201" }
          ],
          "extractions": [
            { "variableName": "jobId", "source": "RESPONSE_BODY", "jsonPath": "$.orderId" }
          ]
        },
        {
          "id": "step_convergence_delay",
          "name": "2. State Convergence Delay (2s)",
          "type": "DELAY",
          "enabled": true,
          "delayMs": 2000,
          "assertions": [],
          "extractions": []
        },
        {
          "id": "step_poll_status",
          "name": "3. Poll Job Status with Retry Policy",
          "type": "HTTP_REQUEST",
          "enabled": true,
          "httpConfig": {
            "method": "GET",
            "url": "http://localhost:8080/api/v1/mock/orders/${jobId}",
            "retryCount": 5,
            "retryIntervalMs": 1000
          },
          "assertions": [
            { "field": "status", "operator": "EQUALS", "expectedValue": "200" }
          ],
          "extractions": []
        },
        {
          "id": "step_auxiliary_cleanup",
          "name": "4. Auxiliary Cleanup (Tolerant & Excluded)",
          "type": "HTTP_REQUEST",
          "enabled": true,
          "continueOnFailure": true,
          "excludeFromReport": true,
          "httpConfig": {
            "method": "DELETE",
            "url": "http://localhost:8080/api/v1/mock/orders/${jobId}"
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
