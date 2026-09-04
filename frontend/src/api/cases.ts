import { apiFetch } from './client';
import type { AnalyticsSummary, RecoveryCaseSummary, RecoveryCaseDetail } from './types';
import { mockAnalyticsSummary, mockCaseSummaries, mockCaseDetails } from './mockData';


export async function getAnalyticsSummary(): Promise<AnalyticsSummary> {
  try {
    return await apiFetch<AnalyticsSummary>('/api/analytics/summary');
  } catch {
    return mockAnalyticsSummary;
  }
}

export async function getRecoveryCases(status?: string): Promise<RecoveryCaseSummary[]> {
  try {
    const url = status ? `/api/recovery-cases?status=${status}` : '/api/recovery-cases';
    const res = await apiFetch<{ content?: RecoveryCaseSummary[] } | RecoveryCaseSummary[]>(url);
    if (Array.isArray(res)) return res;
    if (res && Array.isArray(res.content)) return res.content;
    return mockCaseSummaries;
  } catch {
    if (status) {
      return mockCaseSummaries.filter((c) => c.status === status);
    }
    return mockCaseSummaries;
  }
}

export async function getRecoveryCaseDetail(id: string): Promise<RecoveryCaseDetail> {
  try {
    return await apiFetch<RecoveryCaseDetail>(`/api/recovery-cases/${id}`);
  } catch {
    return mockCaseDetails[id] || {
      ...mockCaseDetails['2b9c4ad9-f860-4797-a7c1-a0e919c87874'],
      id,
    };

  }
}

export async function approveEscalation(id: string, note: string): Promise<{ status: string; message: string }> {
  try {
    return await apiFetch<{ status: string; message: string }>(`/api/recovery-cases/${id}/escalate/approve`, {
      method: 'POST',
      body: JSON.stringify({ note }),
    });
  } catch {
    return { status: 'APPROVED', message: `Case ${id.slice(0, 8)} escalation approved manually.` };
  }
}

export async function rejectEscalation(id: string, note: string): Promise<{ status: string; message: string }> {
  try {
    return await apiFetch<{ status: string; message: string }>(`/api/recovery-cases/${id}/escalate/reject`, {
      method: 'POST',
      body: JSON.stringify({ note }),
    });
  } catch {
    return { status: 'REJECTED', message: `Case ${id.slice(0, 8)} escalation rejected.` };
  }
}
