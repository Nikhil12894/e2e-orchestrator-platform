---
last_mapped_commit: 02b6b6971a69d3d7daeda45ed742be30270d99dc
last_mapped_at: 2026-11-06
---
# Codebase Structure

**Analysis Date:** 2026-11-06

## Directory Layout

```
e2e-orchestrator-platform/
├── backend/                               # Spring Boot 4.1.2 backend service (Java 25 LTS)
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/orchestrator/platform/
│   │   │   │   ├── config/                # Spring & Hikari configuration classes
│   │   │   │   ├── domain/                # Entities, enums, AST models, and DTOs
│   │   │   │   ├── repository/            # Spring Data JPA repositories
│   │   │   │   ├── service/               # Core business services & AST engine
│   │   │   │   │   └── execution/         # Dynamic Karate runner, bridge & executors
│   │   │   │   └── web/                   # REST API controllers
│   │   │   └── resources/
│   │   │       ├── db/migration/          # Flyway SQL schema and seed scripts
│   │   │       ├── karate/                # Embedded Karate DSL feature templates
│   │   │       └── application.yml        # Spring Boot application configuration
│   │   └── test/                          # JUnit 5 & Karate integration tests
│   ├── Dockerfile                         # Ultra-optimized Alpine + jlink multi-stage build
│   ├── docker-entrypoint.sh               # Container startup script
│   └── pom.xml                            # Maven project build specification
├── frontend/                              # React 18 + Vite + Tailwind frontend application
│   ├── src/
│   │   ├── api/                           # Axios REST API client & SSE consumer
│   │   ├── components/
│   │   │   ├── canvas/                    # React Flow canvas, custom nodes, removable edges
│   │   │   ├── catalog/                   # OpenAPI catalog sidebar and import modals
│   │   │   ├── console/                   # Live execution console and report viewer
│   │   │   ├── drawer/                    # Node configuration drawer and variable picker
│   │   │   └── navbar/                    # Top navigation bar, settings, history modals
│   │   ├── context/                       # React context (Theme switcher)
│   │   ├── types/                         # TypeScript interfaces and domain types
│   │   ├── utils/                         # Topological Kahn layout engine
│   │   ├── App.tsx                        # Root layout and canvas orchestrator
│   │   └── main.tsx                       # React application entry point
│   ├── Dockerfile                         # Nginx production container build
│   ├── nginx.conf                         # Reverse proxy configuration
│   ├── package.json                       # npm dependencies and scripts
│   ├── tailwind.config.js                 # Tailwind CSS theme configuration
│   ├── tsconfig.json                      # TypeScript compiler options
│   └── vite.config.ts                     # Vite build configuration and manual chunks
├── .github/workflows/                     # GitHub Actions CI/CD workflows
├── docker-compose.yml                     # Local multi-container compose configuration
├── docker-compose.hub.yml                 # Pre-built Docker Hub compose configuration
├── docker-build-push.sh                   # Multi-architecture container build script
└── README.md                              # Comprehensive architectural and usage documentation
```

## Directory Purposes

**`backend/src/main/java/com/orchestrator/platform/`:**
- Purpose: Application source code for Spring Boot test orchestration service
- Contains: Java classes, controllers, services, repositories, domain entities, AST models
- Key files: `E2EOrchestratorApplication.java`, `service/execution/DynamicKarateRunner.java`, `service/execution/DynamicKarateBridge.java`

**`backend/src/main/resources/karate/`:**
- Purpose: Reusable Karate DSL feature files executed by the dynamic runner
- Contains: `.feature` files, `karate-config.js`
- Key files: `generic-flow-executor.feature`, `execute-http.feature`, `execute-db.feature`, `execute-kafka.feature`, `execute-transform.feature`, `execute-subworkflow.feature`

**`backend/src/main/resources/db/migration/`:**
- Purpose: Flyway database migrations for PostgreSQL schema and reference test workflows
- Contains: Versioned SQL migration files (`V1__...sql` through `V9__...sql`)
- Key files: `V1__init_orchestrator_schema.sql` (schema definitions), `V2` through `V9` (sample seed workflows)

**`frontend/src/components/canvas/`:**
- Purpose: Visual drag-and-drop workflow canvas
- Contains: FlowCanvas wrapper, custom node components (`HttpRequestNode.tsx`, `DbPollNode.tsx`, `KafkaAssertNode.tsx`, `DelayNode.tsx`, `TransformNode.tsx`, `SubWorkflowNode.tsx`), custom edges
- Key files: `FlowCanvas.tsx`, `nodes/HttpRequestNode.tsx`

**`frontend/src/components/drawer/`:**
- Purpose: Configuration panel for editing step parameters and variables
- Contains: `NodeConfigDrawer.tsx`, `VariableSelectorModal.tsx`
- Key files: `NodeConfigDrawer.tsx`

**`frontend/src/components/console/`:**
- Purpose: Real-time execution monitoring, live logs, and Karate HTML report display
- Contains: Console panel, iframe report viewer modal
- Key files: `LiveExecutionConsole.tsx`, `ReportViewerModal.tsx`

## Key File Locations

**Entry Points:**
- `backend/src/main/java/com/orchestrator/platform/E2EOrchestratorApplication.java`: Spring Boot backend entry point
- `frontend/src/main.tsx`: React application DOM mount entry point
- `backend/src/main/resources/karate/generic-flow-executor.feature`: Karate DSL execution entry scenario

**Configuration:**
- `backend/src/main/resources/application.yml`: Backend database, server, retention, and actuator configuration
- `backend/src/main/resources/application-test.yml`: In-memory test environment configuration
- `frontend/vite.config.ts`: Frontend proxy, port, and bundle chunking configuration
- `docker-compose.yml`: Multi-service container orchestration

**Core Logic:**
- `backend/src/main/java/com/orchestrator/platform/service/execution/DynamicKarateRunner.java`: Dynamic test suite generation and execution management
- `backend/src/main/java/com/orchestrator/platform/service/execution/DynamicKarateBridge.java`: In-memory Java-to-Karate interop bridge
- `backend/src/main/java/com/orchestrator/platform/service/WorkflowContextEngine.java`: Variable extraction, macro evaluation, and JSONPath processing
- `frontend/src/utils/layoutEngine.ts`: Topological Kahn auto-alignment algorithm

**Testing:**
- `backend/src/test/java/com/orchestrator/platform/DynamicKarateRunnerTest.java`: End-to-end Karate workflow execution tests
- `backend/src/test/java/com/orchestrator/platform/WorkflowFeaturesAndPortabilityTest.java`: Portability, cancellation, muting, and feature export tests
- `backend/src/test/java/com/orchestrator/platform/DbExecutorAndReportExclusionTest.java`: Multi-database query polling tests

## Naming Conventions

**Files:**
- Java Classes: UpperCamelCase / PascalCase (`DynamicKarateRunner.java`, `WorkflowExecutionEntity.java`)
- React Components: UpperCamelCase / PascalCase (`FlowCanvas.tsx`, `NodeConfigDrawer.tsx`)
- TypeScript Utilities / Clients: camelCase (`client.ts`, `layoutEngine.ts`)
- Database Migrations: Flyway pattern `V{Number}__{description}.sql` (`V1__init_orchestrator_schema.sql`)
- Karate Features: kebab-case with `.feature` extension (`execute-http.feature`, `generic-flow-executor.feature`)

**Directories:**
- Java Packages: lowercase single words or dot-separated (`config`, `domain/model/ast`, `service/execution`)
- Frontend Folders: lowercase single words (`api`, `canvas`, `drawer`, `console`, `navbar`)

## Where to Add New Code

**New Step Type (e.g., GraphQL or gRPC step):**
- Domain Enum: Add enum value to `backend/src/main/java/com/orchestrator/platform/domain/enums/StepType.java`
- Backend AST Model: Create config DTO in `backend/src/main/java/com/orchestrator/platform/domain/model/ast/` and bind in `WorkflowStep.java`
- Karate Execution: Add `execute-newstep.feature` in `backend/src/main/resources/karate/` and hook into `DynamicKarateBridge.java`
- Frontend Types: Update `frontend/src/types/workflow.ts`
- Frontend Node Component: Add custom node in `frontend/src/components/canvas/nodes/` and register in `FlowCanvas.tsx`
- Frontend Drawer: Add configuration panel section in `frontend/src/components/drawer/NodeConfigDrawer.tsx`

**New REST API Endpoint:**
- Backend Controller: Add endpoint mapping in appropriate controller in `backend/src/main/java/com/orchestrator/platform/web/`
- Frontend Client: Add API method to `frontend/src/api/client.ts`

**New Test Suite:**
- Backend: Add `@SpringBootTest` test class in `backend/src/test/java/com/orchestrator/platform/`

## Special Directories

**`backend/target/`:**
- Purpose: Maven build output, compiled classes, and local Karate HTML test reports (`target/karate-reports/`)
- Generated: Yes
- Committed: No (`.gitignore`)

**`frontend/dist/`:**
- Purpose: Production Vite bundle output (HTML, JS, CSS assets)
- Generated: Yes
- Committed: No (`.gitignore`)

**`.planning/`:**
- Purpose: GSD workflow planning artifacts, codebase maps, and requirements
- Generated: Yes (by GSD commands)
- Committed: Yes

---

*Structure analysis: 2026-11-06*
