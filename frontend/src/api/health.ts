import { apiFetch } from './client';
import type { HealthResponse } from './types';
import { mockHealthResponse } from './mockData';


export async function getSystemHealth(): Promise<HealthResponse> {
  try {
    return await apiFetch<HealthResponse>('/api/health');
  } catch {
    return mockHealthResponse;
  }
}
