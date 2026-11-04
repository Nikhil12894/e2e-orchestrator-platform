package com.orchestrator.platform.domain.model.ast;

import java.io.Serializable;

public class KafkaAssertStepConfig implements Serializable {
    private String bootstrapServers = "localhost:9092";
    private String topic;
    private String groupId;
    private Long timeoutMs = 10000L;
    private String keyMatch;
    private String jsonPath;
    private String expectedValue;

    public KafkaAssertStepConfig() {}

    public String getBootstrapServers() { return bootstrapServers; }
    public void setBootstrapServers(String bootstrapServers) { this.bootstrapServers = bootstrapServers; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public Long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(Long timeoutMs) { this.timeoutMs = timeoutMs; }

    public String getKeyMatch() { return keyMatch; }
    public void setKeyMatch(String keyMatch) { this.keyMatch = keyMatch; }

    public String getJsonPath() { return jsonPath; }
    public void setJsonPath(String jsonPath) { this.jsonPath = jsonPath; }

    public String getExpectedValue() { return expectedValue; }
    public void setExpectedValue(String expectedValue) { this.expectedValue = expectedValue; }
}
