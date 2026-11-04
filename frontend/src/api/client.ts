import axios from 'axios';
import { ApiEndpoint, TestWorkflow, WorkflowExecution, ExecutionEventDto } from '../types/workflow';

const api = axios.create({
  baseURL: '/api/v1',
  headers: {
    'Content-Type': 'application/json',
  },
});

export const catalogApi = {
  getEndpoints: async (serviceName?: string, search?: string): Promise<ApiEndpoint[]> => {
    const params = new URLSearchParams();
    if (serviceName) params.append('serviceName', serviceName);
    if (search) params.append('search', search);
    const resp = await api.get<ApiEndpoint[]>('/catalog/endpoints', { params });
    return resp.data;
  },

  getServices: async (): Promise<string[]> => {
    const resp = await api.get<string[]>('/catalog/services');
    return resp.data;
  },

  ingestSpec: async (serviceName: string, specContent?: string, specUrl?: string): Promise<ApiEndpoint[]> => {
    const resp = await api.post<ApiEndpoint[]>('/catalog/ingest', {
      serviceName,
      specContent,
      specUrl,
    });
    return resp.data;
  },

  seedSampleServices: async (): Promise<{ message: string; count: number }> => {
    const resp = await api.post<{ message: string; count: number }>('/catalog/seed-samples');
    return resp.data;
  },

  deleteEndpoint: async (id: string): Promise<void> => {
    await api.delete(`/catalog/endpoints/${id}`);
  },
};

export const workflowApi = {
  getAllWorkflows: async (): Promise<TestWorkflow[]> => {
    const resp = await api.get<TestWorkflow[]>('/workflows');
    return resp.data;
  },

  getWorkflowById: async (id: string): Promise<TestWorkflow> => {
    const resp = await api.get<TestWorkflow>(`/workflows/${id}`);
    return resp.data;
  },

  createWorkflow: async (workflow: Partial<TestWorkflow>): Promise<TestWorkflow> => {
    const resp = await api.post<TestWorkflow>('/workflows', workflow);
    return resp.data;
  },

  updateWorkflow: async (id: string, workflow: Partial<TestWorkflow>): Promise<TestWorkflow> => {
    const resp = await api.put<TestWorkflow>(`/workflows/${id}`, workflow);
    return resp.data;
  },

  deleteWorkflow: async (id: string): Promise<void> => {
    await api.delete(`/workflows/${id}`);
  },
};

export const executionApi = {
  triggerExecution: async (
    workflowId: string,
    initialVariables: Record<string, any> = {},
    environment = 'default'
  ): Promise<WorkflowExecution> => {
    const resp = await api.post<WorkflowExecution>(`/workflows/${workflowId}/execute`, {
      triggerType: 'MANUAL',
      initialVariables,
      environment,
    });
    return resp.data;
  },

  getExecutions: async (workflowId?: string): Promise<WorkflowExecution[]> => {
    const params = workflowId ? { workflowId } : {};
    const resp = await api.get<WorkflowExecution[]>('/executions', { params });
    return resp.data;
  },

  getExecutionById: async (id: string): Promise<WorkflowExecution> => {
    const resp = await api.get<WorkflowExecution>(`/executions/${id}`);
    return resp.data;
  },

  subscribeToExecutionEvents: (
    executionId: string,
    onEvent: (event: ExecutionEventDto) => void,
    onError?: (err: any) => void
  ): (() => void) => {
    const eventSource = new EventSource(`/api/v1/executions/${executionId}/stream`);

    eventSource.addEventListener('execution-event', (e) => {
      try {
        const parsed = JSON.parse(e.data) as ExecutionEventDto;
        onEvent(parsed);
      } catch (err) {
        console.error('Failed to parse SSE event data', err);
      }
    });

    eventSource.onerror = (err) => {
      if (onError) onError(err);
      eventSource.close();
    };

    return () => {
      eventSource.close();
    };
  },
};
