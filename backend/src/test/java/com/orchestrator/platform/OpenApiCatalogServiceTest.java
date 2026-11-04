package com.orchestrator.platform;

import com.orchestrator.platform.domain.entity.ApiEndpointEntity;
import com.orchestrator.platform.domain.model.dto.IngestSpecRequest;
import com.orchestrator.platform.service.OpenApiCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class OpenApiCatalogServiceTest {

    @Autowired
    private OpenApiCatalogService catalogService;

    @Test
    void testIngestAndDeleteMicroserviceAndClearCatalog() {
        String testSpec = """
openapi: 3.0.3
info:
  title: Test Inventory Service
  version: 1.0.0
paths:
  /api/v1/inventory/items:
    get:
      summary: List Items
      operationId: getInventoryItems
      responses:
        '200':
          description: OK
  /api/v1/inventory/items/{id}:
    get:
      summary: Get Item
      operationId: getInventoryItemById
      responses:
        '200':
          description: OK
""";

        // 1. Ingest spec for InventoryService
        IngestSpecRequest request = new IngestSpecRequest("InventoryService", null, testSpec);
        List<ApiEndpointEntity> ingested = catalogService.ingestSpec(request);
        assertEquals(2, ingested.size());

        List<ApiEndpointEntity> endpoints = catalogService.getEndpoints("InventoryService", null);
        assertEquals(2, endpoints.size());

        // 2. Delete microservice by name
        int deletedCount = catalogService.deleteService("InventoryService");
        assertEquals(2, deletedCount);

        List<ApiEndpointEntity> afterDelete = catalogService.getEndpoints("InventoryService", null);
        assertTrue(afterDelete.isEmpty(), "Endpoints for InventoryService should be deleted");

        // 3. Ingest again and test clearCatalog
        catalogService.ingestSpec(request);
        assertFalse(catalogService.getEndpoints("InventoryService", null).isEmpty());

        catalogService.clearCatalog();
        assertTrue(catalogService.getEndpoints(null, null).isEmpty(), "Catalog should be empty after clear");
    }
}
