package com.orchestrator.platform.karate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class KafkaHelperTest {

    @Test
    @DisplayName("Verify KafkaHelper.send and poll in mock mode (zero broker dependency)")
    public void testKafkaSendAndPollMock() {
        // Send a message in mock mode
        Map<String, Object> sendCfg = Map.of(
                "topic", "test-orders-topic",
                "key", "order-101",
                "payload", Map.of("orderId", 101, "status", "PAID", "amount", 99.50),
                "mock", true
        );
        Map<String, Object> sendResult = KafkaHelper.send(sendCfg);
        assertNotNull(sendResult);
        assertTrue((Boolean) sendResult.get("success"), "Produce must succeed in mock mode");
        assertEquals("test-orders-topic", sendResult.get("topic"));

        // Poll for message with matching key and jsonPath assertion
        Map<String, Object> pollCfg = Map.of(
                "topic", "test-orders-topic",
                "keyMatch", "order-101",
                "jsonPath", "$.status",
                "expectedValue", "PAID",
                "mock", true
        );
        Map<String, Object> pollResult = KafkaHelper.poll(pollCfg);
        assertNotNull(pollResult);
        assertTrue((Boolean) pollResult.get("matched"), "Poll must match expected JSONPath");
        assertEquals("order-101", pollResult.get("key"));
        assertNotNull(pollResult.get("matchedPayload"));
    }

    @Test
    @DisplayName("Verify KafkaHelper requires topic")
    public void testKafkaMissingTopicValidation() {
        Map<String, Object> sendResult = KafkaHelper.send(Map.of("key", "test-key"));
        assertFalse((Boolean) sendResult.get("success"));
        assertTrue(sendResult.get("errorMessage").toString().contains("topic is required"));

        Map<String, Object> pollResult = KafkaHelper.poll(Map.of());
        assertFalse((Boolean) pollResult.get("matched"));
        assertTrue(pollResult.get("errorMessage").toString().contains("topic is required"));
    }

    @Test
    @DisplayName("Verify KafkaHelper.resolveToken interpolates environment and dynamic macros")
    public void testKafkaResolveToken() {
        System.setProperty("KAFKA_TEST_BOOTSTRAP", "127.0.0.1:9092");
        try {
            String resolved = KafkaHelper.resolveToken("${$env:KAFKA_TEST_BOOTSTRAP}");
            assertEquals("127.0.0.1:9092", resolved);

            String withUuid = KafkaHelper.resolveToken("order-${$randomUuid}");
            assertTrue(withUuid.startsWith("order-") && withUuid.length() > 10);

            String withTs = KafkaHelper.resolveToken("event-${$timestamp}");
            assertTrue(withTs.startsWith("event-") && withTs.length() > 10);
        } finally {
            System.clearProperty("KAFKA_TEST_BOOTSTRAP");
        }
    }
}
