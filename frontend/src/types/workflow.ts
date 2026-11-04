export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';

export type StepType = 'HTTP_REQUEST' | 'DB_POLL' | 'KAFKA_ASSERT' | 'DELAY' | 'CONDITION';

export type ExecutionStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';

export interface ApiEndpoint {
  id: string;
  serviceName: string;
  endpointPath: string;
  httpMethod: HttpMethod;
  operationId?: string;
  summary?: string;
  description?: string;
  tags: string[];
  parameters: Array<{
    name: string;
    in: string;
    required: boolean;
    description?: string;
    type?: string;
    example?: any;
  }>;
  requestBodySchema: Record<string, any>;
  sampleRequestPayload: Record<string, any>;
  responseSchemas: Record<string, any>;
  updatedAt: string;
}

export interface StepAssertion {
  field: string;
  operator: 'EQUALS' | 'NOT_EQUALS' | 'CONTAINS' | 'GREATER_THAN' | 'LESS_THAN' | 'MATCHES_REGEX' | 'IS_NOT_NULL';
  expectedValue: any;
}

export interface VariableExtraction {
  variableName: string;
  source: 'RESPONSE_BODY' | 'RESPONSE_HEADER' | 'RESPONSE_STATUS' | 'DB_ROW_COLUMN';
  jsonPath: string;
  defaultValue?: string;
}

export interface HttpRequestStepConfig {
  url: string;
  method: HttpMethod;
  headers?: Record<string, string>;
  params?: Record<string, string>;
  body?: any;
  expectedStatus?: number;
  timeoutMs?: number;
}

export interface DbPollStepConfig {
  jdbcUrl: string;
  username?: string;
  password?: string;
  driverClassName?: string;
  query: string;
  pollIntervalMs?: number;
  timeoutMs?: number;
  condition?: string;
  expectedRowCount?: number;
}

export interface KafkaAssertStepConfig {
  bootstrapServers: string;
  topic: string;
  groupId?: string;
  timeoutMs?: number;
  keyMatch?: string;
  jsonPath?: string;
  expectedValue?: string;
}

export interface WorkflowStep {
  id: string;
  name: string;
  type: StepType;
  enabled: boolean;
  httpConfig?: HttpRequestStepConfig;
  dbPollConfig?: DbPollStepConfig;
  kafkaConfig?: KafkaAssertStepConfig;
  delayMs?: number;
  assertions?: StepAssertion[];
  extractions?: VariableExtraction[];
  continueOnFailure?: boolean;
  excludeFromReport?: boolean;
}

export interface WorkflowAst {
  steps: WorkflowStep[];
  variables?: Record<string, any>;
  defaultHeaders?: Record<string, string>;
  dynamicHeaders?: Record<string, string>;
  envProperties?: Record<string, Record<string, any>>;
  secrets?: Record<string, string>;
  timeoutMs?: number;
}

export interface CanvasPosition {
  x: number;
  y: number;
}

export interface CanvasNodeData {
  [key: string]: any;
  name: string;
  method?: HttpMethod;
  url?: string;
  headers?: Record<string, string>;
  params?: Record<string, string>;
  body?: any;
  expectedStatus?: number;
  jdbcUrl?: string;
  username?: string;
  password?: string;
  driverClassName?: string;
  query?: string;
  condition?: string;
  bootstrapServers?: string;
  topic?: string;
  jsonPath?: string;
  expectedValue?: string;
  delayMs?: number;
  assertions?: StepAssertion[];
  extractions?: VariableExtraction[];
  continueOnFailure?: boolean;
  excludeFromReport?: boolean;
  status?: ExecutionStatus;
  durationMs?: number;
}

export interface CanvasNode {
  id: string;
  type: string;
  position: CanvasPosition;
  data: CanvasNodeData;
  selected?: boolean;
}

export interface CanvasEdge {
  id: string;
  source: string;
  target: string;
  sourceHandle?: string;
  targetHandle?: string;
  label?: string;
  animated?: boolean;
}

export interface CanvasState {
  nodes: CanvasNode[];
  edges: CanvasEdge[];
  viewport: { x: number; y: number; zoom: number };
  variables?: Record<string, any>;
  defaultHeaders?: Record<string, string>;
  dynamicHeaders?: Record<string, string>;
  envProperties?: Record<string, Record<string, any>>;
  secrets?: Record<string, string>;
}

export interface TestWorkflow {
  id: string;
  name: string;
  description?: string;
  version: number;
  status: string;
  canvasState: CanvasState;
  workflowAst: WorkflowAst;
  createdAt: string;
  updatedAt: string;
}

export interface ExecutionStepResult {
  stepId: string;
  stepName: string;
  stepType: StepType;
  status: ExecutionStatus;
  startTime?: string;
  endTime?: string;
  durationMs?: number;
  requestPayload?: any;
  responsePayload?: any;
  httpStatus?: number;
  assertionsPassed: boolean;
  assertionDetails: string[];
  extractedVariables: Record<string, any>;
  errorMessage?: string;
  excludeFromReport?: boolean;
}

export interface WorkflowExecution {
  id: string;
  workflowId: string;
  workflowName: string;
  status: ExecutionStatus;
  triggerType: string;
  startTime?: string;
  endTime?: string;
  durationMs?: number;
  contextSnapshot: Record<string, any>;
  stepResults: ExecutionStepResult[];
  reportPath?: string;
  errorMessage?: string;
  createdAt: string;
}

export interface ExecutionEventDto {
  executionId: string;
  workflowId?: string;
  eventType: string;
  stepId?: string;
  stepName?: string;
  status: ExecutionStatus;
  message: string;
  timestamp: string;
  details?: any;
}
