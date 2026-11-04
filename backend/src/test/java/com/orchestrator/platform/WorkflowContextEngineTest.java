package com.orchestrator.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orchestrator.platform.service.WorkflowContextEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowContextEngineTest {

    private WorkflowContextEngine contextEngine;

    @BeforeEach
    void setUp() {
        contextEngine = new WorkflowContextEngine(new ObjectMapper());
    }

    @Test
    void testResolvePlaceholders() {
        Map<String, Object> context = new HashMap<>();
        context.put("authToken", "jwt_secret_token_123");
        context.put("orderId", "ORD-99881");

        String input = "Bearer ${authToken} for order ${orderId}";
        String resolved = contextEngine.resolveString(input, context);

        assertEquals("Bearer jwt_secret_token_123 for order ORD-99881", resolved);
    }

    @Test
    void testDynamicMacros() {
        Map<String, Object> context = new HashMap<>();

        String timestampStr = contextEngine.resolveString("${$timestamp}", context);
        assertNotNull(timestampStr);
        assertTrue(Long.parseLong(timestampStr) > 0);

        String uuidStr = contextEngine.resolveString("${$randomUuid}", context);
        assertNotNull(uuidStr);
        assertEquals(36, uuidStr.length());
    }

    @Test
    void testEvaluateJsonPath() {
        Map<String, Object> user = Map.of(
                "id", "user_42",
                "profile", Map.of("email", "john@example.com", "tier", "GOLD"),
                "roles", List.of("USER", "ADMIN")
        );

        Object email = contextEngine.evaluateJsonPath(user, "$.profile.email");
        assertEquals("john@example.com", email);

        Object role = contextEngine.evaluateJsonPath(user, "$.roles[1]");
        assertEquals("ADMIN", role);
    }

    @Test
    void testResolveObjectRecursively() {
        Map<String, Object> context = Map.of(
                "baseUrl", "https://api.example.com",
                "userId", "usr-100"
        );

        Map<String, Object> request = Map.of(
                "url", "${baseUrl}/users/${userId}",
                "body", Map.of("owner", "${userId}", "active", true)
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> resolved = (Map<String, Object>) contextEngine.resolveObject(request, context);

        assertEquals("https://api.example.com/users/usr-100", resolved.get("url"));
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) resolved.get("body");
        assertEquals("usr-100", body.get("owner"));
        assertEquals(true, body.get("active"));
    }
}
