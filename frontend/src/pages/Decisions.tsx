import React, { useEffect, useState } from 'react';
import { Card } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { Skeleton } from '../components/ui/Skeleton';
import { getRecentDecisions } from '../api/decisions';
import type { AgentDecision } from '../api/types';
import { Brain, Filter, ShieldAlert } from 'lucide-react';


export const Decisions: React.FC = () => {
  const [decisions, setDecisions] = useState<AgentDecision[]>([]);
  const [filter, setFilter] = useState<string>('ALL');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getRecentDecisions()
      .then((res) => setDecisions(res))
      .finally(() => setLoading(false));
  }, []);

  const getDecisionBadge = (decision: string) => {
    switch (decision) {
      case 'EXECUTED':
      case 'APPROVED':
        return <Badge variant="success">EXECUTED</Badge>;
      case 'BLOCKED':
        return <Badge variant="danger">BLOCKED</Badge>;
      case 'NO_ACTION':
        return <Badge variant="neutral">NO ACTION</Badge>;
      case 'APPROVAL_REQUIRED':
        return <Badge variant="warning">APPROVAL REQUIRED</Badge>;
      default:
        return <Badge variant="neutral">{decision}</Badge>;
    }
  };

  const filtered = decisions.filter((d) => {
    if (filter === 'ALL') return true;
    if (filter === 'ALLOWED') return d.decision === 'EXECUTED' || d.decision === 'APPROVED';
    if (filter === 'BLOCKED') return d.decision === 'BLOCKED';
    if (filter === 'APPROVAL_REQUIRED') return d.decision === 'APPROVAL_REQUIRED';
    if (filter === 'NO_ACTION') return d.decision === 'NO_ACTION';
    return true;
  });

  return (
    <div className="space-y-6">
      {/* Header Info */}
      <Card className="p-4 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
        <div>
          <div className="flex items-center space-x-2">
            <Brain className="w-4 h-4 text-[#3B82F6]" />
            <h2 className="text-xs font-mono font-bold uppercase tracking-wider text-[#F5F7FA]">
              Strategy & Guardrail Decision Logs
            </h2>
          </div>
          <p className="text-xs text-[#8B949E] mt-1 font-sans">
            Complete audit trail showing that RecoverAI filters un-economic or forbidden actions instead of blindly retrying every failure.
          </p>
        </div>

        {/* Filter Tabs */}
        <div className="flex items-center space-x-1 font-mono text-xs overflow-x-auto w-full sm:w-auto">
          <span className="text-[#5F6875] text-[10px] uppercase mr-1 flex items-center">
            <Filter className="w-3 h-3 mr-1" /> View:
          </span>
          {['ALL', 'ALLOWED', 'BLOCKED', 'APPROVAL_REQUIRED', 'NO_ACTION'].map((tab) => (
            <button
              key={tab}
              onClick={() => setFilter(tab)}
              className={`px-2.5 py-1 text-[11px] rounded-[2px] transition-colors whitespace-nowrap ${
                filter === tab
                  ? 'bg-[#15191F] text-white border border-[#242A32] font-semibold'
                  : 'text-[#8B949E] hover:text-[#F5F7FA]'
              }`}
            >
              {tab.replace('_', ' ')}
            </button>
          ))}
        </div>
      </Card>

      {/* Decisions Audit Table */}
      <Card>
        {loading ? (
          <div className="space-y-3 p-2">
            <Skeleton height="35px" />
            <Skeleton height="35px" />
            <Skeleton height="35px" />
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left font-mono text-xs">
              <thead>
                <tr className="border-b border-[#242A32] text-[#5F6875] uppercase text-[10px]">
                  <th className="py-2.5 px-3">Time</th>
                  <th className="py-2.5 px-3">Agent Type</th>
                  <th className="py-2.5 px-3">Input Summary</th>
                  <th className="py-2.5 px-3">Confidence</th>
                  <th className="py-2.5 px-3">Policy / Guardrail Reason</th>
                  <th className="py-2.5 px-3">Final Decision</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#242A32]">
                {filtered.length > 0 ? (
                  filtered.map((d) => (
                    <tr key={d.id} className="hover:bg-[#15191F] transition-colors">
                      <td className="py-3 px-3 text-[#5F6875] whitespace-nowrap">
                        {new Date(d.createdAt).toLocaleTimeString()}
                      </td>
                      <td className="py-3 px-3 text-[#3B82F6] font-semibold">{d.agentType}</td>
                      <td className="py-3 px-3 text-[#F5F7FA] max-w-xs truncate font-medium">
                        {d.inputSummary}
                      </td>
                      <td className="py-3 px-3 text-[#8B949E] tabular-nums">
                        {((d.confidence || 0.9) * 100).toFixed(0)}%
                      </td>
                      <td className="py-3 px-3 text-[#8B949E] text-[11px] max-w-xs">
                        {d.reasoning && d.reasoning[0] ? (
                          <span className="flex items-center text-[#8B949E]">
                            {d.decision === 'BLOCKED' && (
                              <ShieldAlert className="w-3 h-3 text-[#EF4444] mr-1 flex-shrink-0" />
                            )}
                            {d.reasoning[0]}
                          </span>
                        ) : (
                          'Passed all checks'
                        )}
                      </td>
                      <td className="py-3 px-3">{getDecisionBadge(d.decision)}</td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan={6} className="py-8 text-center text-[#5F6875]">
                      No agent decisions found for the selected filter.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  );
};
