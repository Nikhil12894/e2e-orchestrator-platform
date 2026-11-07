package com.orchestrator.platform.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/mock")
@Tag(name = "Target Mock APIs", description = "Target microservice mock endpoints for testing workflows out of the box")
public class MockTargetApiController {

    private final Map<String, Map<String, Object>> mockOrders = new ConcurrentHashMap<>();

    @PostMapping(value = "/auth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Operation(summary = "OAuth2 / Form URL-Encoded token login", description = "Authenticates using application/x-www-form-urlencoded parameters and returns access token")
    public ResponseEntity<Map<String, Object>> formLogin(@RequestParam(required = false) Map<String, String> formParams) {
        String username = formParams != null && formParams.containsKey("username") ?
                formParams.get("username") : "guest";
        String grantType = formParams != null ? formParams.getOrDefault("grant_type", "password") : "password";

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("access_token", "mock-token-" + UUID.randomUUID().toString());
        resp.put("token_type", "Bearer");
        resp.put("expires_in", 3600);
        resp.put("username", username);
        resp.put("grant_type", grantType);
        resp.put("received_fields", formParams != null ? formParams : Collections.emptyMap());
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/auth/login")
    @Operation(summary = "Authenticate user", description = "Authenticates user and returns JWT access token")
    public ResponseEntity<Map<String, Object>> login(@RequestBody(required = false) Map<String, Object> credentials) {
        String username = credentials != null && credentials.containsKey("username") ?
                String.valueOf(credentials.get("username")) : "guest";

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("token", "mock-jwt-" + UUID.randomUUID().toString());
        resp.put("userId", "usr-" + Math.abs(username.hashCode()));
        resp.put("username", username);
        resp.put("role", "ADMIN");
        resp.put("expiresIn", 3600);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/orders")
    @Operation(summary = "Create order", description = "Submits a new order and returns generated orderId")
    public ResponseEntity<Map<String, Object>> createOrder(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) Map<String, Object> payload) {

        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("orderId", orderId);
        order.put("status", "CONFIRMED");
        order.put("userId", payload != null ? payload.get("userId") : "usr-default");
        order.put("productId", payload != null ? payload.get("productId") : "PROD-001");
        order.put("quantity", payload != null ? payload.get("quantity") : 1);
        order.put("totalPrice", payload != null ? payload.get("totalPrice") : 99.99);
        order.put("createdAt", Instant.now().toString());

        mockOrders.put(orderId, order);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    @GetMapping("/orders/{orderId}")
    @Operation(summary = "Get order details", description = "Retrieves order status and line items by order ID")
    public ResponseEntity<Map<String, Object>> getOrder(
            @PathVariable("orderId") String orderId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        Map<String, Object> order = mockOrders.get(orderId);
        if (order == null) {
            order = new LinkedHashMap<>();
            order.put("orderId", orderId);
            order.put("status", "CONFIRMED");
            order.put("totalPrice", 199.98);
            order.put("updatedAt", Instant.now().toString());
        }
        return ResponseEntity.ok(order);
    }

    @PostMapping("/payments/charge")
    @Operation(summary = "Process Payment", description = "Charges customer card or account")
    public ResponseEntity<Map<String, Object>> chargePayment(@RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("transactionId", "TXN-" + UUID.randomUUID().toString().substring(0, 8));
        response.put("status", "SUCCESS");
        response.put("authorized", true);
        response.put("processedAt", Instant.now().toString());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/inventory/{sku}")
    @Operation(summary = "Check Inventory Stock", description = "Returns available stock for SKU")
    public ResponseEntity<Map<String, Object>> checkStock(@PathVariable("sku") String sku) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("sku", sku);
        resp.put("stockQuantity", 50);
        resp.put("warehouse", "US-EAST-1");
        resp.put("inStock", true);
        return ResponseEntity.ok(resp);
    }
}
