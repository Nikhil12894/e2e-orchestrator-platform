package com.orchestrator.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.PathNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class WorkflowContextEngine {

    private static final Logger log = LoggerFactory.getLogger(WorkflowContextEngine.class);
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");
    private static final Pattern RANDOM_INT_PATTERN = Pattern.compile("\\$randomInt\\((\\d+)\\s*,\\s*(\\d+)\\)");
    private static final Pattern RANDOM_STR_PATTERN = Pattern.compile("\\$randomString\\((\\d+)\\)");

    private final ObjectMapper objectMapper;
    private final Configuration jsonPathConfig;

    public WorkflowContextEngine(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.jsonPathConfig = Configuration.defaultConfiguration()
                .addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL, Option.SUPPRESS_EXCEPTIONS);
    }

    /**
     * Resolves all ${placeholder} tokens in a string using values from the context.
     */
    public String resolveString(String input, Map<String, Object> context) {
        if (input == null || !input.contains("${")) {
            return input;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String expression = matcher.group(1).trim();
            Object resolvedValue = resolveExpression(expression, context);
            String replacement = resolvedValue != null ? String.valueOf(resolvedValue) : matcher.group(0);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Resolves an expression like "step_login.token", "$randomUuid", "env.baseUrl", etc.
     */
    public Object resolveExpression(String expression, Map<String, Object> context) {
        if (expression == null || expression.trim().isEmpty()) {
            return null;
        }

        // Built-in dynamic functions
        if (expression.equalsIgnoreCase("$timestamp")) {
            return String.valueOf(System.currentTimeMillis());
        }
        if (expression.equalsIgnoreCase("$isoTimestamp")) {
            return Instant.now().toString();
        }
        if (expression.equalsIgnoreCase("$randomUuid")) {
            return UUID.randomUUID().toString();
        }
        Matcher randIntMatcher = RANDOM_INT_PATTERN.matcher(expression);
        if (randIntMatcher.matches()) {
            int min = Integer.parseInt(randIntMatcher.group(1));
            int max = Integer.parseInt(randIntMatcher.group(2));
            return new Random().nextInt((max - min) + 1) + min;
        }
        Matcher randStrMatcher = RANDOM_STR_PATTERN.matcher(expression);
        if (randStrMatcher.matches()) {
            int length = Integer.parseInt(randStrMatcher.group(1));
            return generateRandomAlphaNumeric(length);
        }

        // Check direct key in context
        if (context.containsKey(expression)) {
            return context.get(expression);
        }

        // Handle dot notation: stepId.varName or stepId.response.body.path
        String[] parts = expression.split("\\.", 2);
        String rootKey = parts[0];
        if (context.containsKey(rootKey) && parts.length > 1) {
            Object rootObj = context.get(rootKey);
            return evaluatePropertyOrJsonPath(rootObj, parts[1]);
        }

        // Check if expression is a JSONPath against the root context
        if (expression.startsWith("$")) {
            try {
                return JsonPath.using(jsonPathConfig).parse(context).read(expression);
            } catch (Exception e) {
                log.debug("JSONPath evaluation on context failed for {}: {}", expression, e.getMessage());
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private Object evaluatePropertyOrJsonPath(Object rootObj, String subPath) {
        if (rootObj == null) return null;

        // If subPath starts with $, use direct JSONPath
        if (subPath.startsWith("$")) {
            return evaluateJsonPath(rootObj, subPath);
        }

        // Handle dot navigation in nested maps
        String[] keys = subPath.split("\\.");
        Object current = rootObj;

        for (String key : keys) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(key);
            } else {
                // Try converting object to map or evaluating as JSONPath
                try {
                    String json = objectMapper.writeValueAsString(current);
                    return evaluateJsonPath(json, "$." + subPath);
                } catch (Exception e) {
                    return null;
                }
            }
            if (current == null) break;
        }
        return current;
    }

    /**
     * Evaluates a Jayway JSONPath expression against an object (String, Map, or POJO).
     */
    public Object evaluateJsonPath(Object document, String jsonPath) {
        if (document == null || jsonPath == null) return null;
        try {
            String path = jsonPath.startsWith("$") ? jsonPath : "$." + jsonPath;
            if (document instanceof String strDoc) {
                if (strDoc.trim().isEmpty()) return null;
                return JsonPath.using(jsonPathConfig).parse(strDoc).read(path);
            }
            return JsonPath.using(jsonPathConfig).parse(document).read(path);
        } catch (PathNotFoundException e) {
            log.debug("JSONPath not found: {} on document", jsonPath);
            return null;
        } catch (Exception e) {
            log.warn("Error evaluating JSONPath '{}': {}", jsonPath, e.getMessage());
            return null;
        }
    }

    /**
     * Recursively resolves placeholders in an object structure (Map, List, String, etc.).
     */
    @SuppressWarnings("unchecked")
    public Object resolveObject(Object obj, Map<String, Object> context) {
        if (obj == null) return null;

        if (obj instanceof String str) {
            // Check if string is EXACTLY "${expression}" and replace with raw resolved object type
            if (str.startsWith("${") && str.endsWith("}") && str.indexOf("${", 2) == -1) {
                String expr = str.substring(2, str.length() - 1).trim();
                Object rawResolved = resolveExpression(expr, context);
                return rawResolved != null ? rawResolved : str;
            }
            return resolveString(str, context);
        } else if (obj instanceof Map) {
            Map<String, Object> resolvedMap = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) obj).entrySet()) {
                String resolvedKey = resolveString(entry.getKey(), context);
                Object resolvedVal = resolveObject(entry.getValue(), context);
                resolvedMap.put(resolvedKey, resolvedVal);
            }
            return resolvedMap;
        } else if (obj instanceof List) {
            List<Object> resolvedList = new ArrayList<>();
            for (Object item : (List<Object>) obj) {
                resolvedList.add(resolveObject(item, context));
            }
            return resolvedList;
        }
        return obj;
    }

    private String generateRandomAlphaNumeric(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
