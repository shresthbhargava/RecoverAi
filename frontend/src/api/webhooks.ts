import { apiFetch } from './client';
import type { WebhookEvent } from './types';
import { mockWebhookEvents } from './mockData';


export async function getRecentWebhooks(limit = 50): Promise<WebhookEvent[]> {
  try {
    const data = await apiFetch<WebhookEvent[]>(`/api/webhooks/recent?limit=${limit}`);
    return data && data.length > 0 ? data : mockWebhookEvents;
  } catch {
    return mockWebhookEvents;
  }
}

export async function getWebhookStats(): Promise<Record<string, number>> {
  try {
    return await apiFetch<Record<string, number>>('/api/webhooks/stats');
  } catch {
    return {
      PROCESSED: 42,
      DUPLICATE: 8,
      INVALID_SIGNATURE: 3,
      FAILED: 1,
      IGNORED: 5,
      TOTAL: 59,
    };
  }
}
