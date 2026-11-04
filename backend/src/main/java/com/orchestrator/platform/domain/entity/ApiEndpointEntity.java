package com.orchestrator.platform.domain.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "api_endpoint_catalog", uniqueConstraints = {
    @UniqueConstraint(name = "uq_endpoint_operation", columnNames = {"service_name", "endpoint_path", "http_method"})
})
public class ApiEndpointEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "service_name", nullable = false, length = 100)
    private String serviceName;

    @Column(name = "endpoint_path", nullable = false, length = 500)
    private String endpointPath;

    @Column(name = "http_method", nullable = false, length = 10)
    private String httpMethod;

    @Column(name = "operation_id", length = 200)
    private String operationId;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags", columnDefinition = "jsonb")
    private List<String> tags = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters", columnDefinition = "jsonb")
    private List<Map<String, Object>> parameters = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_body_schema", columnDefinition = "jsonb")
    private Map<String, Object> requestBodySchema = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sample_request_payload", columnDefinition = "jsonb")
    private Map<String, Object> sampleRequestPayload = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_schemas", columnDefinition = "jsonb")
    private Map<String, Object> responseSchemas = new HashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void onPrePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void onPreUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public String getEndpointPath() { return endpointPath; }
    public void setEndpointPath(String endpointPath) { this.endpointPath = endpointPath; }

    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }

    public String getOperationId() { return operationId; }
    public void setOperationId(String operationId) { this.operationId = operationId; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public List<Map<String, Object>> getParameters() { return parameters; }
    public void setParameters(List<Map<String, Object>> parameters) { this.parameters = parameters; }

    public Map<String, Object> getRequestBodySchema() { return requestBodySchema; }
    public void setRequestBodySchema(Map<String, Object> requestBodySchema) { this.requestBodySchema = requestBodySchema; }

    public Map<String, Object> getSampleRequestPayload() { return sampleRequestPayload; }
    public void setSampleRequestPayload(Map<String, Object> sampleRequestPayload) { this.sampleRequestPayload = sampleRequestPayload; }

    public Map<String, Object> getResponseSchemas() { return responseSchemas; }
    public void setResponseSchemas(Map<String, Object> responseSchemas) { this.responseSchemas = responseSchemas; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
