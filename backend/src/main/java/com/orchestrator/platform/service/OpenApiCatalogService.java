package com.orchestrator.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orchestrator.platform.domain.entity.ApiEndpointEntity;
import com.orchestrator.platform.domain.model.dto.IngestSpecRequest;
import com.orchestrator.platform.repository.ApiEndpointRepository;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class OpenApiCatalogService {

    private static final Logger log = LoggerFactory.getLogger(OpenApiCatalogService.class);

    private final ApiEndpointRepository endpointRepository;
    private final ObjectMapper objectMapper;

    public OpenApiCatalogService(ApiEndpointRepository endpointRepository, ObjectMapper objectMapper) {
        this.endpointRepository = endpointRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<ApiEndpointEntity> ingestSpec(IngestSpecRequest request) {
        log.info("Ingesting OpenAPI spec for service: {}", request.getServiceName());
        OpenAPI openAPI = parseSpec(request);
        if (openAPI == null || openAPI.getPaths() == null) {
            throw new IllegalArgumentException("Failed to parse OpenAPI spec: Invalid or empty specification");
        }

        List<ApiEndpointEntity> savedEndpoints = new ArrayList<>();
        Map<String, Schema> componentsSchemas = openAPI.getComponents() != null ?
                openAPI.getComponents().getSchemas() : Collections.emptyMap();

        for (Map.Entry<String, PathItem> pathEntry : openAPI.getPaths().entrySet()) {
            String path = pathEntry.getKey();
            PathItem pathItem = pathEntry.getValue();

            Map<PathItem.HttpMethod, Operation> operations = pathItem.readOperationsMap();
            for (Map.Entry<PathItem.HttpMethod, Operation> opEntry : operations.entrySet()) {
                String httpMethod = opEntry.getKey().name();
                Operation operation = opEntry.getValue();

                ApiEndpointEntity endpoint = endpointRepository
                        .findByServiceNameAndEndpointPathAndHttpMethod(request.getServiceName(), path, httpMethod)
                        .orElse(new ApiEndpointEntity());

                endpoint.setServiceName(request.getServiceName());
                endpoint.setEndpointPath(path);
                endpoint.setHttpMethod(httpMethod);
                endpoint.setOperationId(operation.getOperationId() != null ?
                        operation.getOperationId() : (httpMethod.toLowerCase() + "_" + path.replaceAll("[^a-zA-Z0-9]", "_")));
                endpoint.setSummary(operation.getSummary() != null ? operation.getSummary() : operation.getDescription());
                endpoint.setDescription(operation.getDescription());

                // Tags
                endpoint.setTags(operation.getTags() != null ? new ArrayList<>(operation.getTags()) : new ArrayList<>());

                // Parameters
                List<Map<String, Object>> params = extractParameters(operation.getParameters());
                endpoint.setParameters(params);

                // Request body & sample payload
                Map<String, Object> reqBodySchema = new HashMap<>();
                Map<String, Object> samplePayload = new HashMap<>();
                if (operation.getRequestBody() != null) {
                    extractRequestBody(operation.getRequestBody(), componentsSchemas, reqBodySchema, samplePayload);
                }
                endpoint.setRequestBodySchema(reqBodySchema);
                endpoint.setSampleRequestPayload(samplePayload);

                // Response schemas
                Map<String, Object> respSchemas = extractResponses(operation.getResponses());
                endpoint.setResponseSchemas(respSchemas);

                endpoint.setUpdatedAt(Instant.now());
                savedEndpoints.add(endpointRepository.save(endpoint));
            }
        }

        log.info("Successfully ingested {} endpoints for service: {}", savedEndpoints.size(), request.getServiceName());
        return savedEndpoints;
    }

    private OpenAPI parseSpec(IngestSpecRequest request) {
        OpenAPIParser parser = new OpenAPIParser();
        if (request.getSpecContent() != null && !request.getSpecContent().trim().isEmpty()) {
            SwaggerParseResult result = parser.readContents(request.getSpecContent(), null, null);
            if (result.getOpenAPI() != null) {
                return result.getOpenAPI();
            }
            if (result.getMessages() != null && !result.getMessages().isEmpty()) {
                log.warn("Spec parse warnings: {}", result.getMessages());
            }
        }
        if (request.getSpecUrl() != null && !request.getSpecUrl().trim().isEmpty()) {
            SwaggerParseResult result = parser.readLocation(request.getSpecUrl(), null, null);
            if (result.getOpenAPI() != null) {
                return result.getOpenAPI();
            }
        }
        throw new IllegalArgumentException("Either valid specContent or specUrl must be provided");
    }

    private List<Map<String, Object>> extractParameters(List<Parameter> parameters) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (parameters == null) return result;

        for (Parameter p : parameters) {
            Map<String, Object> paramMap = new HashMap<>();
            paramMap.put("name", p.getName());
            paramMap.put("in", p.getIn());
            paramMap.put("required", p.getRequired() != null && p.getRequired());
            paramMap.put("description", p.getDescription());
            if (p.getSchema() != null) {
                paramMap.put("type", p.getSchema().getType());
                paramMap.put("example", p.getSchema().getExample());
            }
            result.add(paramMap);
        }
        return result;
    }

    private void extractRequestBody(RequestBody requestBody, Map<String, Schema> componentsSchemas,
                                    Map<String, Object> reqBodySchema, Map<String, Object> samplePayload) {
        if (requestBody.getContent() == null) return;
        MediaType jsonMedia = requestBody.getContent().get("application/json");
        if (jsonMedia == null && !requestBody.getContent().isEmpty()) {
            jsonMedia = requestBody.getContent().values().iterator().next();
        }
        if (jsonMedia == null || jsonMedia.getSchema() == null) return;

        Schema<?> schema = jsonMedia.getSchema();
        if (schema.get$ref() != null && componentsSchemas != null) {
            String refKey = schema.get$ref().replace("#/components/schemas/", "");
            Schema<?> resolved = componentsSchemas.get(refKey);
            if (resolved != null) {
                schema = resolved;
            }
        }

        reqBodySchema.put("type", schema.getType());
        reqBodySchema.put("description", schema.getDescription());

        Object generatedSample = generateSampleFromSchema(schema, componentsSchemas, 0);
        if (generatedSample instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) generatedSample;
            samplePayload.putAll(map);
        } else if (generatedSample != null) {
            samplePayload.put("data", generatedSample);
        }
    }

    @SuppressWarnings("rawtypes")
    private Object generateSampleFromSchema(Schema schema, Map<String, Schema> componentsSchemas, int depth) {
        if (schema == null || depth > 5) return null;

        if (schema.get$ref() != null && componentsSchemas != null) {
            String refKey = schema.get$ref().replace("#/components/schemas/", "");
            Schema resolved = componentsSchemas.get(refKey);
            if (resolved != null) {
                return generateSampleFromSchema(resolved, componentsSchemas, depth + 1);
            }
        }

        if (schema.getExample() != null) {
            return schema.getExample();
        }

        String type = schema.getType();
        if ("string".equalsIgnoreCase(type)) {
            String format = schema.getFormat();
            if ("date-time".equalsIgnoreCase(format)) return Instant.now().toString();
            if ("date".equalsIgnoreCase(format)) return "2026-01-01";
            if ("email".equalsIgnoreCase(format)) return "user@example.com";
            if ("uuid".equalsIgnoreCase(format)) return UUID.randomUUID().toString();
            return "sample_value";
        } else if ("integer".equalsIgnoreCase(type) || "int32".equalsIgnoreCase(type) || "int64".equalsIgnoreCase(type)) {
            return 100;
        } else if ("number".equalsIgnoreCase(type) || "float".equalsIgnoreCase(type) || "double".equalsIgnoreCase(type)) {
            return 99.99;
        } else if ("boolean".equalsIgnoreCase(type)) {
            return true;
        } else if ("array".equalsIgnoreCase(type)) {
            List<Object> list = new ArrayList<>();
            if (schema instanceof ArraySchema arraySchema && arraySchema.getItems() != null) {
                Object itemSample = generateSampleFromSchema(arraySchema.getItems(), componentsSchemas, depth + 1);
                if (itemSample != null) list.add(itemSample);
            }
            return list;
        } else if ("object".equalsIgnoreCase(type) || schema.getProperties() != null) {
            Map<String, Object> obj = new LinkedHashMap<>();
            Map<String, Schema> properties = schema.getProperties();
            if (properties != null) {
                for (Map.Entry<String, Schema> propEntry : properties.entrySet()) {
                    String propName = propEntry.getKey();
                    Schema propSchema = propEntry.getValue();
                    if (propSchema.getExample() != null) {
                        obj.put(propName, propSchema.getExample());
                    } else if (propName.toLowerCase().contains("email")) {
                        obj.put(propName, "customer@example.com");
                    } else if (propName.toLowerCase().contains("id")) {
                        obj.put(propName, UUID.randomUUID().toString().substring(0, 8));
                    } else if (propName.toLowerCase().contains("status")) {
                        obj.put(propName, "PENDING");
                    } else if (propName.toLowerCase().contains("name")) {
                        obj.put(propName, "Test " + propName);
                    } else {
                        obj.put(propName, generateSampleFromSchema(propSchema, componentsSchemas, depth + 1));
                    }
                }
            }
            return obj;
        }

        return "sample";
    }

    private Map<String, Object> extractResponses(Map<String, ApiResponse> responses) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (responses == null) return result;

        for (Map.Entry<String, ApiResponse> entry : responses.entrySet()) {
            Map<String, Object> respMap = new HashMap<>();
            respMap.put("description", entry.getValue().getDescription());
            result.put(entry.getKey(), respMap);
        }
        return result;
    }

    public List<ApiEndpointEntity> getEndpoints(String serviceName, String search) {
        Specification<ApiEndpointEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (serviceName != null && !serviceName.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("serviceName"), serviceName.trim()));
            }
            if (search != null && !search.trim().isEmpty()) {
                String pattern = "%" + search.toLowerCase().trim() + "%";
                Predicate pathMatch = cb.like(cb.lower(root.get("endpointPath")), pattern);
                Predicate summaryMatch = cb.like(cb.lower(root.get("summary")), pattern);
                Predicate opMatch = cb.like(cb.lower(root.get("operationId")), pattern);
                predicates.add(cb.or(pathMatch, summaryMatch, opMatch));
            }
            query.orderBy(cb.asc(root.get("serviceName")), cb.asc(root.get("endpointPath")), cb.asc(root.get("httpMethod")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return endpointRepository.findAll(spec);
    }

    public List<String> getServices() {
        return endpointRepository.findDistinctServiceNames();
    }

    public void deleteEndpoint(UUID id) {
        endpointRepository.deleteById(id);
    }
}
