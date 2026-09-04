import { apiFetch, BASE_URL } from './client';
import type { AgentDecision } from './types';
import { mockDecisions } from './mockData';


export async function getRecentDecisions(limit = 50): Promise<AgentDecision[]> {
  try {
    const data = await apiFetch<AgentDecision[]>(`/api/agent-decisions/recent?limit=${limit}`);
    return data && data.length > 0 ? data : mockDecisions;
  } catch {
    return mockDecisions;
  }
}

export function subscribeToLiveDecisions(
  onData: (data: any) => void,
  onError?: (err: Event) => void
): () => void {
  try {
    const eventSource = new EventSource(`${BASE_URL}/api/agent-decisions/stream`);

    eventSource.onmessage = (event) => {
      try {
        const parsed = JSON.parse(event.data);
        onData(parsed);
      } catch (err) {
        console.error('Failed to parse SSE payload', err);
      }
    };

    eventSource.onerror = (err) => {
      if (onError) onError(err);
      eventSource.close();
    };

    return () => {
      eventSource.close();
    };
  } catch {
    return () => {};
  }
}

