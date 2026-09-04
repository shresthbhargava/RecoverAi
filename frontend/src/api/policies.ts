import { apiFetch } from './client';
import type { PolicyRule } from './types';
import { mockPolicyRules } from './mockData';


export async function getPolicies(): Promise<PolicyRule[]> {
  try {
    const data = await apiFetch<PolicyRule[]>('/api/policies');
    return data && data.length > 0 ? data : mockPolicyRules;
  } catch {
    return mockPolicyRules;
  }
}

export async function updatePolicyRule(
  id: string,
  value: string,
  enabled: boolean
): Promise<PolicyRule> {
  try {
    return await apiFetch<PolicyRule>(`/api/policies/${id}`, {
      method: 'PUT',
      body: JSON.stringify({ value, enabled }),
    });
  } catch {
    // Fallback in-memory update for mock state
    const existing = mockPolicyRules.find((p) => p.id === id);
    if (existing) {
      existing.value = value;
      existing.enabled = enabled;
      return { ...existing };
    }
    throw new Error('Policy rule not found');
  }
}
