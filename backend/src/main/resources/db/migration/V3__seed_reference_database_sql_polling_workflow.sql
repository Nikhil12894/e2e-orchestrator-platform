-- V3__seed_reference_database_sql_polling_workflow.sql
-- Reference Workflow: Database SQL Polling & Row Extraction
-- Demonstrates asynchronous relational database state verification, polling with max retry attempts and timeouts, multi-column row extraction, and downstream API verification.

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
    '10000000-0000-0000-0000-000000000002'::uuid,
    '[Reference] Database SQL Polling & Row Extraction',
    'Demonstrates SQL database polling with retry attempts, dynamic row status condition checks, case-insensitive column extractions into runtime variables, and cross-protocol HTTP verification.',
    1,
    'ACTIVE',
    '{
      "nodes": [
        {
          "id": "step_db_poll",
          "type": "dbPoll",
          "position": { "x": 180, "y": 80 },
          "data": {
            "name": "1. Poll Database for Confirmed Order",
            "jdbcUrl": "jdbc:h2:mem:demo_orders;DB_CLOSE_DELAY=-1",
            "username": "sa",
            "password": "",
            "driverClassName": "org.h2.Driver",
            "query": "SELECT 101 AS order_id, ''CONFIRMED'' AS order_status, ''CARRIER_FEDEX'' AS carrier, 89.50 AS total_amount",
            "condition": "row.order_status == ''CONFIRMED''",
            "timeoutMs": 15000,
            "pollIntervalMs": 1000,
            "maxAttempts": 5,
            "extractions": [
              { "variableName": "extractedOrderId", "source": "DB_ROW_COLUMN", "jsonPath": "order_id" },
              { "variableName": "extractedStatus", "source": "DB_ROW_COLUMN", "jsonPath": "order_status" },
              { "variableName": "carrierCode", "source": "DB_ROW_COLUMN", "jsonPath": "carrier" }
            ]
          }
        },
        {
          "id": "step_verify_order",
          "type": "httpRequest",
          "position": { "x": 180, "y": 300 },
          "data": {
            "name": "2. Verify Order API with DB State",
            "method": "GET",
            "url": "http://localhost:8080/api/v1/mock/orders/${extractedOrderId}",
            "headers": {
              "X-Carrier-Code": "${carrierCode}",
              "X-Expected-Status": "${extractedStatus}"
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
          "id": "edge_db_to_verify",
          "source": "step_db_poll",
          "target": "step_verify_order",
          "animated": true
        }
      ],
      "viewport": { "x": 0, "y": 0, "zoom": 1 }
    }'::jsonb,
    '{
      "steps": [
        {
          "id": "step_db_poll",
          "name": "1. Poll Database for Confirmed Order",
          "type": "DB_POLL",
          "enabled": true,
          "dbPollConfig": {
            "jdbcUrl": "jdbc:h2:mem:demo_orders;DB_CLOSE_DELAY=-1",
            "username": "sa",
            "password": "",
            "driverClassName": "org.h2.Driver",
            "query": "SELECT 101 AS order_id, ''CONFIRMED'' AS order_status, ''CARRIER_FEDEX'' AS carrier, 89.50 AS total_amount",
            "condition": "row.order_status == ''CONFIRMED''",
            "timeoutMs": 15000,
            "pollIntervalMs": 1000,
            "maxAttempts": 5
          },
          "extractions": [
            { "variableName": "extractedOrderId", "source": "DB_ROW_COLUMN", "jsonPath": "order_id" },
            { "variableName": "extractedStatus", "source": "DB_ROW_COLUMN", "jsonPath": "order_status" },
            { "variableName": "carrierCode", "source": "DB_ROW_COLUMN", "jsonPath": "carrier" }
          ]
        },
        {
          "id": "step_verify_order",
          "name": "2. Verify Order API with DB State",
          "type": "HTTP_REQUEST",
          "enabled": true,
          "httpConfig": {
            "method": "GET",
            "url": "http://localhost:8080/api/v1/mock/orders/${extractedOrderId}",
            "headers": {
              "X-Carrier-Code": "${carrierCode}",
              "X-Expected-Status": "${extractedStatus}"
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
