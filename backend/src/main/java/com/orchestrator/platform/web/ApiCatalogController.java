package com.orchestrator.platform.web;

import com.orchestrator.platform.domain.entity.ApiEndpointEntity;
import com.orchestrator.platform.domain.model.dto.IngestSpecRequest;
import com.orchestrator.platform.service.OpenApiCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/catalog")
@Tag(name = "API Endpoint Catalog", description = "Endpoints for OpenAPI spec ingestion, catalog management, and auto-generated payloads")
public class ApiCatalogController {

    private final OpenApiCatalogService catalogService;

    public ApiCatalogController(OpenApiCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @PostMapping("/ingest")
    @Operation(summary = "Ingest OpenAPI Specification", description = "Parses OpenAPI/Swagger 3.x spec from URL or raw YAML/JSON content")
    public ResponseEntity<List<ApiEndpointEntity>> ingestSpec(@RequestBody IngestSpecRequest request) {
        List<ApiEndpointEntity> endpoints = catalogService.ingestSpec(request);
        return ResponseEntity.ok(endpoints);
    }

    @PostMapping("/seed-samples")
    @Operation(summary = "Seed Sample Microservice APIs", description = "Loads demo microservices (AuthService, OrderService, PaymentService)")
    public ResponseEntity<Map<String, Object>> seedSampleServices() {
        String sampleSpec = """
openapi: 3.0.3
info:
  title: Demo Microservices
  version: 1.0.0
paths:
  /api/v1/mock/auth/login:
    post:
      summary: User Login
      operationId: loginUser
      tags:
        - AuthService
      requestBody:
        content:
          application/json:
            schema:
              type: object
              properties:
                username:
                  type: string
                  example: qa_tester
                password:
                  type: string
                  example: Secret123!
      responses:
        '200':
          description: Successful login
  /api/v1/mock/orders:
    post:
      summary: Create New Order
      operationId: createOrder
      tags:
        - OrderService
      requestBody:
        content:
          application/json:
            schema:
              type: object
              properties:
                userId:
                  type: string
                  example: usr-8844
                productId:
                  type: string
                  example: PROD-9988
                quantity:
                  type: integer
                  example: 2
                totalPrice:
                  type: number
                  example: 199.98
      responses:
        '201':
          description: Order created
  /api/v1/mock/orders/{orderId}:
    get:
      summary: Retrieve Order Status
      operationId: getOrderById
      tags:
        - OrderService
      parameters:
        - name: orderId
          in: path
          required: true
          schema:
            type: string
            example: ORD-99881
      responses:
        '200':
          description: Order details
  /api/v1/mock/payments/charge:
    post:
      summary: Charge Payment
      operationId: chargePayment
      tags:
        - PaymentService
      requestBody:
        content:
          application/json:
            schema:
              type: object
              properties:
                orderId:
                  type: string
                  example: ORD-99881
                amount:
                  type: number
                  example: 199.98
                paymentMethod:
                  type: string
                  example: CREDIT_CARD
      responses:
        '200':
          description: Payment successful
  /api/v1/mock/inventory/{sku}:
    get:
      summary: Check Inventory Stock
      operationId: checkStock
      tags:
        - InventoryService
      parameters:
        - name: sku
          in: path
          required: true
          schema:
            type: string
            example: PROD-9988
      responses:
        '200':
          description: Stock information
""";

        IngestSpecRequest req = new IngestSpecRequest("EcommercePlatform", null, sampleSpec);
        List<ApiEndpointEntity> endpoints = catalogService.ingestSpec(req);
        return ResponseEntity.ok(Map.of(
                "message", "Successfully seeded " + endpoints.size() + " sample endpoints",
                "count", endpoints.size()
        ));
    }

    @GetMapping("/endpoints")
    @Operation(summary = "Search Catalog Endpoints", description = "Query endpoints with optional microservice filter and text search")
    public ResponseEntity<List<ApiEndpointEntity>> getEndpoints(
            @RequestParam(value = "serviceName", required = false) String serviceName,
            @RequestParam(value = "search", required = false) String search) {
        return ResponseEntity.ok(catalogService.getEndpoints(serviceName, search));
    }

    @GetMapping("/services")
    @Operation(summary = "List Distinct Microservices", description = "Returns all unique microservice names present in catalog")
    public ResponseEntity<List<String>> getServices() {
        return ResponseEntity.ok(catalogService.getServices());
    }

    @DeleteMapping("/endpoints/{id}")
    @Operation(summary = "Delete Endpoint", description = "Removes an endpoint from catalog")
    public ResponseEntity<Void> deleteEndpoint(@PathVariable("id") UUID id) {
        catalogService.deleteEndpoint(id);
        return ResponseEntity.noContent().build();
    }
}
