package com.orchestrator.platform.karate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

/**
 * Universal Kafka Helper for Karate Scenarios and Standalone CLI executions.
 * Provides standalone message publishing (produce) and message consumption/assertion (poll)
 * with zero Spring dependencies, compatible with pure JVM, standalone JARs, and low-resource environments.
 */
public class KafkaHelper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

    // In-memory fallback message bus for local/offline testing without external Kafka brokers
    private static final Map<String, List<Map<String, Object>>> IN_MEMORY_TOPICS = new HashMap<>();

    /**
     * Publishes a message to a Kafka topic.
     *
     * @param config Map containing bootstrapServers, topic, key (optional), payload (String or Map), headers (optional)
     * @return Map with success, topic, partition, offset, timestamp, errorMessage
     */
    public static Map<String, Object> send(Map<String, Object> config) {
        String bootstrapServers = resolveToken(getString(config, "bootstrapServers", "localhost:9092"));
        String topic = resolveToken(getString(config, "topic", ""));
        String key = resolveToken(getString(config, "key", null));
        Object payloadObj = config.get("payload") != null ? config.get("payload") : config.get("value");
        long timeoutMs = getLong(config, "timeoutMs", 5000L);
        boolean mockMode = getBoolean(config, "mock", false);

        Map<String, Object> result = new LinkedHashMap<>();

        if (topic == null || topic.isBlank()) {
            result.put("success", false);
            result.put("errorMessage", "Kafka topic is required");
            return result;
        }

        String payloadStr;
        try {
            if (payloadObj instanceof String str) {
                payloadStr = resolveToken(str);
            } else if (payloadObj != null) {
                payloadStr = OBJECT_MAPPER.writeValueAsString(payloadObj);
            } else {
                payloadStr = "";
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("errorMessage", "Failed to serialize Kafka payload: " + e.getMessage());
            return result;
        }

        // Header extraction
        List<Header> recordHeaders = new ArrayList<>();
        if (config.get("headers") instanceof Map<?, ?> headerMap) {
            for (Map.Entry<?, ?> entry : headerMap.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    recordHeaders.add(new RecordHeader(
                            String.valueOf(entry.getKey()),
                            String.valueOf(entry.getValue()).getBytes(StandardCharsets.UTF_8)
                    ));
                }
            }
        }

        // If mockMode is explicitly enabled or broker connection is skipped for unit tests
        if (mockMode) {
            return recordInMemory(topic, key, payloadStr);
        }

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, (int) Math.min(timeoutMs, 5000L));
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, (int) Math.min(timeoutMs + 2000L, 8000L));

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, null, key, payloadStr, recordHeaders);
            RecordMetadata meta = producer.send(record).get();

            result.put("success", true);
            result.put("topic", meta.topic());
            result.put("partition", meta.partition());
            result.put("offset", meta.offset());
            result.put("timestamp", meta.timestamp());
            result.put("errorMessage", null);
            return result;
        } catch (Exception e) {
            // Check if user requested mock fallback on unreachable broker
            if (getBoolean(config, "fallbackToMock", false)) {
                return recordInMemory(topic, key, payloadStr);
            }
            result.put("success", false);
            result.put("errorMessage", "Kafka produce failed to " + bootstrapServers + ": " + e.getMessage());
            return result;
        }
    }

    /**
     * Polls a Kafka topic for a record matching the criteria (key match, JSONPath, or non-empty).
     *
     * @param config Map containing bootstrapServers, topic, groupId, timeoutMs, keyMatch, jsonPath, expectedValue
     * @return Map with matched, messagesScanned, matchedPayload, payload, key, headers, errorMessage
     */
    public static Map<String, Object> poll(Map<String, Object> config) {
        String bootstrapServers = resolveToken(getString(config, "bootstrapServers", "localhost:9092"));
        String topic = resolveToken(getString(config, "topic", ""));
        String groupId = resolveToken(getString(config, "groupId", "meterflow-runner-" + UUID.randomUUID().toString().substring(0, 8)));
        long timeoutMs = getLong(config, "timeoutMs", 10000L);
        String keyMatch = resolveToken(getString(config, "keyMatch", null));
        String jsonPath = getString(config, "jsonPath", null);
        String expectedValue = resolveToken(getString(config, "expectedValue", null));
        String offsetReset = getString(config, "autoOffsetReset", "earliest");
        boolean mockMode = getBoolean(config, "mock", false);

        Map<String, Object> result = new LinkedHashMap<>();

        if (topic == null || topic.isBlank()) {
            result.put("matched", false);
            result.put("messagesScanned", 0);
            result.put("errorMessage", "Kafka topic is required");
            return result;
        }

        if (mockMode) {
            return pollInMemory(topic, keyMatch, jsonPath, expectedValue);
        }

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, offsetReset);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, (int) Math.min(timeoutMs, 5000L));

        long startTime = System.currentTimeMillis();
        int scanned = 0;

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(topic));

            while ((System.currentTimeMillis() - startTime) < timeoutMs) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    scanned++;
                    boolean keyMatches = (keyMatch == null || keyMatch.isBlank()) ||
                            (record.key() != null && record.key().contains(keyMatch));

                    if (keyMatches) {
                        String payload = record.value();
                        if (jsonPath != null && !jsonPath.isBlank() && expectedValue != null) {
                            try {
                                Object extracted = JsonPath.read(payload, jsonPath);
                                if (extracted != null && String.valueOf(extracted).equals(expectedValue)) {
                                    return buildSuccessResult(scanned, payload, record.key(), record.headers());
                                }
                            } catch (Exception ignored) {}
                        } else {
                            return buildSuccessResult(scanned, payload, record.key(), record.headers());
                        }
                    }
                }
            }

            result.put("matched", false);
            result.put("messagesScanned", scanned);
            result.put("errorMessage", "Kafka assert timed out after " + timeoutMs + "ms waiting for message on topic " + topic);
            return result;
        } catch (Exception e) {
            if (getBoolean(config, "fallbackToMock", false)) {
                return pollInMemory(topic, keyMatch, jsonPath, expectedValue);
            }
            result.put("matched", false);
            result.put("messagesScanned", scanned);
            result.put("errorMessage", "Kafka consumer error on " + bootstrapServers + ": " + e.getMessage());
            return result;
        }
    }

    private static Map<String, Object> buildSuccessResult(int scanned, String payload, String key, Iterable<Header> headers) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("matched", true);
        result.put("messagesScanned", scanned);
        result.put("matchedPayload", payload);
        result.put("key", key);

        // Parse payload as JSON object if valid
        try {
            if (payload != null && (payload.trim().startsWith("{") || payload.trim().startsWith("["))) {
                result.put("payload", OBJECT_MAPPER.readValue(payload, Object.class));
            } else {
                result.put("payload", payload);
            }
        } catch (Exception ignored) {
            result.put("payload", payload);
        }

        Map<String, String> headerMap = new LinkedHashMap<>();
        if (headers != null) {
            for (Header h : headers) {
                headerMap.put(h.key(), new String(h.value(), StandardCharsets.UTF_8));
            }
        }
        result.put("headers", headerMap);
        result.put("errorMessage", null);
        return result;
    }

    private static synchronized Map<String, Object> recordInMemory(String topic, String key, String payload) {
        IN_MEMORY_TOPICS.computeIfAbsent(topic, k -> new ArrayList<>());
        Map<String, Object> msg = new HashMap<>();
        msg.put("key", key);
        msg.put("payload", payload);
        msg.put("timestamp", System.currentTimeMillis());
        IN_MEMORY_TOPICS.get(topic).add(msg);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("topic", topic);
        res.put("partition", 0);
        res.put("offset", IN_MEMORY_TOPICS.get(topic).size() - 1);
        res.put("timestamp", msg.get("timestamp"));
        res.put("mock", true);
        res.put("errorMessage", null);
        return res;
    }

    private static synchronized Map<String, Object> pollInMemory(String topic, String keyMatch, String jsonPath, String expectedValue) {
        List<Map<String, Object>> msgs = IN_MEMORY_TOPICS.getOrDefault(topic, Collections.emptyList());
        int scanned = 0;
        for (Map<String, Object> msg : msgs) {
            scanned++;
            String key = (String) msg.get("key");
            String payload = (String) msg.get("payload");

            boolean keyMatches = (keyMatch == null || keyMatch.isBlank()) || (key != null && key.contains(keyMatch));
            if (keyMatches) {
                if (jsonPath != null && !jsonPath.isBlank() && expectedValue != null) {
                    try {
                        Object extracted = JsonPath.read(payload, jsonPath);
                        if (extracted != null && String.valueOf(extracted).equals(expectedValue)) {
                            return buildSuccessResult(scanned, payload, key, null);
                        }
                    } catch (Exception ignored) {}
                } else {
                    return buildSuccessResult(scanned, payload, key, null);
                }
            }
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("matched", false);
        res.put("messagesScanned", scanned);
        res.put("errorMessage", "No matching in-memory record found on topic: " + topic);
        return res;
    }

    public static String resolveToken(String input) {
        if (input == null || !input.contains("${")) {
            return input;
        }
        Matcher matcher = TOKEN_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String expr = matcher.group(1).trim();
            String replacement = null;

            if (expr.startsWith("$env:") || expr.startsWith("env:")) {
                String envVar = expr.substring(expr.indexOf(':') + 1).trim();
                replacement = System.getenv(envVar);
                if (replacement == null) {
                    replacement = System.getProperty(envVar);
                }
            } else if (expr.startsWith("$secret:") || expr.startsWith("secret:")) {
                String secretKey = expr.substring(expr.indexOf(':') + 1).trim();
                replacement = System.getenv(secretKey);
                if (replacement == null) {
                    replacement = System.getProperty(secretKey);
                }
            } else if (expr.equals("$timestamp")) {
                replacement = String.valueOf(System.currentTimeMillis());
            } else if (expr.equals("$randomUuid")) {
                replacement = UUID.randomUUID().toString();
            }

            if (replacement != null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String getString(Map<String, Object> map, String key, String defaultValue) {
        Object val = map.get(key);
        return val != null ? String.valueOf(val).trim() : defaultValue;
    }

    private static long getLong(Map<String, Object> map, String key, long defaultValue) {
        Object val = map.get(key);
        if (val instanceof Number num) return num.longValue();
        if (val != null) {
            try { return Long.parseLong(String.valueOf(val).trim()); } catch (Exception ignored) {}
        }
        return defaultValue;
    }

    private static boolean getBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        Object val = map.get(key);
        if (val instanceof Boolean b) return b;
        if (val != null) return Boolean.parseBoolean(String.valueOf(val).trim());
        return defaultValue;
    }
}
