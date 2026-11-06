---
last_mapped_commit: 02b6b6971a69d3d7daeda45ed742be30270d99dc
last_mapped_at: 2026-11-06
---
# Coding Conventions

**Analysis Date:** 2026-11-06

## Naming Patterns

**Files:**
- Java source files use PascalCase matching public class/interface name: `DynamicKarateRunner.java`, `WorkflowExecutionController.java` (`backend/src/main/java`)
- React component files use PascalCase with `.tsx`: `FlowCanvas.tsx`, `NodeConfigDrawer.tsx` (`frontend/src/components`)
- TypeScript utilities and API modules use camelCase: `client.ts`, `layoutEngine.ts` (`frontend/src/utils`)
- Database migrations use Flyway versioned prefix `V{N}__{description}.sql`: `V1__init_orchestrator_schema.sql` (`backend/src/main/resources/db/migration`)
- Karate feature files use kebab-case: `generic-flow-executor.feature`, `execute-http.feature` (`backend/src/main/resources/karate`)

**Functions & Methods:**
- Java methods use camelCase starting with action verbs: `executeWorkflow()`, `triggerExecution()`, `resolveReportDir()`, `sanitizeContextSnapshot()`
- TypeScript functions and React hooks use camelCase: `calculateTopologicalLayout()`, `subscribeToExecutionEvents()`, `useCallback()`
- React functional components use PascalCase: `HttpRequestNode`, `ReportViewerModal`

**Variables:**
- Local variables and parameters use camelCase: `executionId`, `reportDir`, `activeContexts`, `keepLatest`
- Constant fields in Java use SCREAMING_SNAKE_CASE: `DEFAULT_PORTS`, `MAX_ATTEMPTS`
- Java Enums use SCREAMING_SNAKE_CASE: `ExecutionStatus.RUNNING`, `StepType.HTTP_REQUEST` (`backend/src/main/java/com/orchestrator/platform/domain/enums`)

**Types & Interfaces:**
- Java Classes, Interfaces, Enums, and Records use PascalCase: `WorkflowExecutionEntity`, `StepAssertion`, `HttpRequestStepConfig`
- TypeScript interfaces and types use PascalCase: `TestWorkflow`, `WorkflowStep`, `ApiEndpoint`, `ExecutionEventDto` (`frontend/src/types/workflow.ts`)

## Code Style

**Formatting:**
- Java: Standard 4-space indentation; brackets on same line (K&R style); parameters annotated with `@JsonProperty` or Jakarta validation
- TypeScript / React: 2-space indentation; ES module imports; Tailwind CSS utility classes organized logically (layout, sizing, typography, colors, dark mode variants)

**Linting:**
- Backend: Maven Compiler Plugin with `-parameters` flag and strict Java 25 compiler checking
- Frontend: TypeScript compiler (`tsc --noEmit` in `npm run build`) with strict type checking enabled in `frontend/tsconfig.json` (`strict: true`, `noUnusedLocals: true`, `noUnusedParameters: true`)

## Import Organization

**Order (Java):**
1. Standard Java libraries (`java.util.*`, `java.nio.*`, `java.time.*`)
2. Third-party frameworks (`io.karatelabs.*`, `com.fasterxml.jackson.*`, `org.slf4j.*`)
3. Spring Framework (`org.springframework.stereotype.*`, `org.springframework.web.bind.annotation.*`)
4. Internal domain, service, and repository packages (`com.orchestrator.platform.*`)

**Order (TypeScript):**
1. React core and hooks (`react`, `react-dom`)
2. Third-party UI and state libraries (`@xyflow/react`, `lucide-react`, `axios`, `@stomp/stompjs`)
3. Internal application types and contexts (`../types/workflow`, `../context/ThemeContext`)
4. Internal components and API clients (`../api/client`, `./nodes/HttpRequestNode`)

**Path Aliases:**
- Standard relative imports (`./`, `../`) used across TypeScript files (`frontend/src/App.tsx:7`)

## Error Handling

**Patterns:**
- Backend Controller Layer: Returns standard Spring `ResponseEntity` with descriptive HTTP status codes (`HttpStatus.NOT_FOUND`, `HttpStatus.ACCEPTED`, `HttpStatus.CREATED`)
- Backend Execution Engine: Encapsulates exceptions within `WorkflowExecutionEntity.errorMessage` and marks execution status as `ExecutionStatus.FAILED` without crashing virtual threads (`DynamicKarateRunner.java:188`)
- Cancellation Checking: Cooperative cancellation via `DynamicKarateBridge.isExecutionCancelled(executionId)` checked before delay pauses and step executions
- Frontend API: Uses Axios with try/catch blocks; error callbacks for SSE streams (`eventSource.onerror`) to clean up listeners and notify users

## Logging

**Framework:**
- Backend: SLF4J with Logback (`LoggerFactory.getLogger(Class.class)`)
- Frontend: Browser console (`console.error`, `console.warn`) for unexpected parsing or network errors

**Patterns:**
- Logging levels: INFO for execution lifecycle events (`Starting Dynamic Karate Execution [id={}, workflow={}]`), DEBUG for step inputs/outputs, ERROR for trapped exceptions
- Secret Redaction: All logs emitted through `ExecutionLogBroadcaster` pass through secret sanitization (`redactSecrets(...)`) replacing token values with `[REDACTED]`

## Comments

**When to Comment:**
- Explaining non-obvious architecture decisions (e.g., in-memory bridge synchronization, virtual thread isolation, fallback paths for Docker directory permissions)
- Swagger / OpenAPI documentation annotations on all REST controller endpoints (`@Operation(summary = ..., description = ...)`)
- Section markers in complex components (e.g., `// 1. Environment Properties`, `// 2. Global Workflow Variables`)

**JSDoc/TSDoc:**
- Used on utility algorithms such as `layoutEngine.ts` to document inputs, outputs, and Kahn's algorithm complexity

## Function Design

**Size:**
- Targeted single-responsibility methods in backend services (typically 15-40 lines)
- Large configuration drawer (`NodeConfigDrawer.tsx`) contains distinct sub-render functions for each step type

**Parameters:**
- Complex step configurations use dedicated typed DTOs (`HttpRequestStepConfig`, `DbPollStepConfig`, `KafkaAssertStepConfig`) rather than loose parameter lists

**Return Values:**
- Java services return `Optional<T>` for queries that may not find an entity (`getWorkflowById(UUID id)`)
- API endpoints return strongly typed `ResponseEntity<T>`

## Module Design

**Exports:**
- Named exports preferred in TypeScript utility and API modules: `export const workflowApi = ...` (`frontend/src/api/client.ts`)
- Default exports used for primary React components: `export default FlowCanvas` (`frontend/src/components/canvas/FlowCanvas.tsx`)

**Barrel Files:**
- Centralized types defined in `frontend/src/types/workflow.ts` and re-exported across components without multiple scattered type files

---

*Convention analysis: 2026-11-06*
