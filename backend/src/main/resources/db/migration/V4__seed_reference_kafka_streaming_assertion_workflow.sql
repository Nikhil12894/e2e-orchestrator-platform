-- V4__seed_reference_kafka_streaming_assertion_workflow.sql
-- Reference Workflow: Event-Driven Kafka Streaming Message Assertion
-- Demonstrates triggering an event via REST API, awaiting and asserting message payload arrival on an Apache Kafka topic, and verifying downstream event consumption.

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
    '10000000-0000-0000-0000-000000000003'::uuid,
    '[Reference] Event-Driven Kafka Streaming Assertion',
    'Demonstrates event-driven testing architecture: publish an asynchronous event via HTTP, consume and assert JSON message delivery on an Apache Kafka topic with timeout limits, and verify consumer processing.',
    1,
    'ACTIVE',
    '{
      "nodes": [
        {
          "id": "step_trigger_event",
          "type": "httpRequest",
          "position": { "x": 180, "y": 80 },
          "data": {
            "name": "1. Trigger Async Order Event",
            "method": "POST",
            "url": "http://localhost:8080/api/v1/mock/orders",
            "headers": {
              "Content-Type": "application/json"
            },
            "body": {
              "userId": "USR-KAFKA-1",
              "productId": "PROD-STREAM-44",
              "quantity": 1,
              "totalPrice": 499.00
            },
            "expectedStatus": 201,
            "extractions": [
              { "variableName": "orderId", "source": "RESPONSE_BODY", "jsonPath": "$.orderId" }
            ]
          }
        },
        {
          "id": "step_kafka_assert",
          "type": "kafkaAssert",
          "position": { "x": 180, "y": 280 },
          "data": {
            "name": "2. Assert Kafka Topic Message",
            "bootstrapServers": "localhost:9092",
            "topic": "order-events",
            "jsonPath": "$.orderId",
            "expectedValue": "${orderId}",
            "timeoutMs": 10000
          }
        },
        {
          "id": "step_verify_consumer",
          "type": "httpRequest",
          "position": { "x": 180, "y": 480 },
          "data": {
            "name": "3. Verify Final Consumer State",
            "method": "GET",
            "url": "http://localhost:8080/api/v1/mock/orders/${orderId}",
            "expectedStatus": 200,
            "assertions": [
              { "field": "status", "operator": "EQUALS", "expectedValue": "200" }
            ]
          }
        }
      ],
      "edges": [
        {
          "id": "edge_trigger_to_kafka",
          "source": "step_trigger_event",
          "target": "step_kafka_assert",
          "animated": true
        },
        {
          "id": "edge_kafka_to_verify",
          "source": "step_kafka_assert",
          "target": "step_verify_consumer",
          "animated": true
        }
      ],
      "viewport": { "x": 0, "y": 0, "zoom": 1 }
    }'::jsonb,
    '{
      "steps": [
        {
          "id": "step_trigger_event",
          "name": "1. Trigger Async Order Event",
          "type": "HTTP_REQUEST",
          "enabled": true,
          "httpConfig": {
            "method": "POST",
            "url": "http://localhost:8080/api/v1/mock/orders",
            "headers": {
              "Content-Type": "application/json"
            },
            "body": {
              "userId": "USR-KAFKA-1",
              "productId": "PROD-STREAM-44",
              "quantity": 1,
              "totalPrice": 499.00
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
          "id": "step_kafka_assert",
          "name": "2. Assert Kafka Topic Message",
          "type": "KAFKA_ASSERT",
          "enabled": true,
          "kafkaConfig": {
            "bootstrapServers": "localhost:9092",
            "topic": "order-events",
            "jsonPath": "$.orderId",
            "expectedValue": "${orderId}",
            "timeoutMs": 10000
          },
          "assertions": [],
          "extractions": []
        },
        {
          "id": "step_verify_consumer",
          "name": "3. Verify Final Consumer State",
          "type": "HTTP_REQUEST",
          "enabled": true,
          "httpConfig": {
            "method": "GET",
            "url": "http://localhost:8080/api/v1/mock/orders/${orderId}"
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
