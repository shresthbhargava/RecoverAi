import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { MetricBlock } from '../components/dashboard/MetricBlock';
import { PipelineVisual } from '../components/dashboard/PipelineVisual';
import { PerformanceChart } from '../components/dashboard/PerformanceChart';
import { Card } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { Skeleton } from '../components/ui/Skeleton';
import { getAnalyticsSummary, getRecoveryCases } from '../api/cases';
import type { AnalyticsSummary, RecoveryCaseSummary } from '../api/types';
import { ArrowUpRight, Activity } from 'lucide-react';


export const Dashboard: React.FC = () => {
  const navigate = useNavigate();
  const [analytics, setAnalytics] = useState<AnalyticsSummary | null>(null);
  const [cases, setCases] = useState<RecoveryCaseSummary[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([getAnalyticsSummary(), getRecoveryCases()])
      .then(([analyticsRes, casesRes]) => {
        setAnalytics(analyticsRes);
        setCases(casesRes);
      })
      .finally(() => setLoading(false));
  }, []);

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'RECOVERED':
        return <Badge variant="success">RECOVERED</Badge>;
      case 'POLICY_BLOCKED':
        return <Badge variant="danger">BLOCKED</Badge>;
      case 'FAILED':
        return <Badge variant="danger">FAILED</Badge>;
      case 'AWAITING_APPROVAL':
        return <Badge variant="warning">AWAITING APPROVAL</Badge>;
      case 'EXECUTING':
      case 'IN_PROGRESS':
        return <Badge variant="info">IN PROGRESS</Badge>;
      default:
        return <Badge variant="neutral">{status}</Badge>;
    }
  };

  if (loading) {
    return (
      <div className="space-y-6">
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} height="80px" />
          ))}
        </div>
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <Skeleton height="200px" />
          <Skeleton height="200px" />
        </div>
        <Skeleton height="250px" />
      </div>
    );
  }

  const atRisk = analytics?.revenueAtRisk ?? 34825;
  const recovered = analytics?.revenueRecovered ?? 15000;
  const rate = analytics?.recoveryRate ?? 43.1;
  const activeCasesCount = analytics?.recoverableCases ?? 3;
  const cost = analytics?.recoveryCost ?? 30;
  const netRecovered = analytics?.netRecoveredRevenue ?? 14970;

  return (
    <div className="space-y-6">
      {/* Top Metrics Row */}
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
        <MetricBlock
          label="Revenue at Risk"
          value={`₹${atRisk.toLocaleString()}`}
          subtext="142 events processed"
          variant="danger"
        />
        <MetricBlock
          label="Recovered"
          value={`₹${recovered.toLocaleString()}`}
          subtext="2 cases resolved"
          variant="success"
        />
        <MetricBlock
          label="Recovery Rate"
          value={`${rate}%`}
          subtext="Merchant target >40%"
          variant="success"
        />
        <MetricBlock
          label="Active Cases"
          value={activeCasesCount.toString()}
          subtext="3 processing now"
          variant="info"
        />
        <MetricBlock
          label="Recovery Cost"
          value={`₹${cost}`}
          subtext="Avg ₹15 / attempt"
          variant="neutral"
        />
        <MetricBlock
          label="Net Recovered"
          value={`₹${netRecovered.toLocaleString()}`}
          subtext="After execution cost"
          variant="success"
        />
      </div>

      {/* Two Column Section: Pipeline Visual & Performance Chart */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-4">
        {/* LEFT: Recovery Pipeline */}
        <Card className="lg:col-span-7 flex flex-col justify-between">
          <div className="flex items-center justify-between pb-3 border-b border-[#242A32] mb-3">
            <div>
              <h2 className="text-xs font-mono font-bold uppercase tracking-wider text-[#F5F7FA]">
                Recovery Pipeline
              </h2>
              <p className="text-[11px] text-[#8B949E]">
                Automated stage progression from failure to settlement.
              </p>
            </div>
            <Activity className="w-4 h-4 text-[#3B82F6]" />
          </div>
          <PipelineVisual />
        </Card>

        {/* RIGHT: Recovery Performance Chart */}
        <Card className="lg:col-span-5 flex flex-col justify-between">
          <div className="pb-3 border-b border-[#242A32] mb-3">
            <h2 className="text-xs font-mono font-bold uppercase tracking-wider text-[#F5F7FA]">
              Recovery Performance
            </h2>
            <p className="text-[11px] text-[#8B949E]">
              Revenue at risk vs. recovered capital.
            </p>
          </div>
          <PerformanceChart />
        </Card>
      </div>

      {/* Bottom Table: Recent Recovery Cases */}
      <Card>
        <div className="flex items-center justify-between pb-3 border-b border-[#242A32] mb-3">
          <div>
            <h2 className="text-xs font-mono font-bold uppercase tracking-wider text-[#F5F7FA]">
              Recent Recovery Cases
            </h2>
            <p className="text-[11px] text-[#8B949E]">
              Click any case to inspect diagnosis, policy rules, and attempts history.
            </p>
          </div>
          <button
            onClick={() => navigate('/cases')}
            className="text-xs font-mono text-[#3B82F6] hover:underline flex items-center"
          >
            View all cases <ArrowUpRight className="w-3.5 h-3.5 ml-1" />
          </button>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left font-mono text-xs">
            <thead>
              <tr className="border-b border-[#242A32] text-[#5F6875] uppercase text-[10px]">
                <th className="py-2.5 px-3">Customer</th>
                <th className="py-2.5 px-3">Amount</th>
                <th className="py-2.5 px-3">Failure</th>
                <th className="py-2.5 px-3">Diagnosis</th>
                <th className="py-2.5 px-3">Strategy</th>
                <th className="py-2.5 px-3">Status</th>
                <th className="py-2.5 px-3">Updated</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#242A32]">
              {cases.map((c) => (
                <tr
                  key={c.id}
                  onClick={() => navigate(`/cases/${c.id}`)}
                  className="hover:bg-[#15191F] transition-colors cursor-pointer group"
                >
                  <td className="py-3 px-3 font-semibold text-[#F5F7FA] group-hover:text-white flex items-center justify-between">
                    <span>{c.customerName}</span>
                    <ArrowUpRight className="w-3 h-3 text-[#5F6875] group-hover:text-[#F5F7FA] opacity-0 group-hover:opacity-100 transition-opacity" />
                  </td>
                  <td className="py-3 px-3 text-[#F5F7FA] tabular-nums font-bold">
                    ₹{c.amountAtRisk.toLocaleString()}
                  </td>
                  <td className="py-3 px-3 text-[#8B949E]">
                    {c.diagnosis.includes('OUTAGE')
                      ? 'Temporary Failure'
                      : c.diagnosis.includes('TIMEOUT')
                      ? 'Payment Timeout'
                      : c.diagnosis.includes('FUNDS')
                      ? 'Insufficient Funds'
                      : 'Card Limit'}
                  </td>
                  <td className="py-3 px-3 text-[#8B949E]">
                    <span className="text-[#3B82F6]">
                      {((c.diagnosisConfidence || 0.9) * 100).toFixed(0)}%
                    </span>{' '}
                    confidence
                  </td>
                  <td className="py-3 px-3 text-[#F5F7FA]">{c.selectedStrategy}</td>
                  <td className="py-3 px-3">{getStatusBadge(c.status)}</td>
                  <td className="py-3 px-3 text-[#5F6875]">
                    {new Date(c.createdAt).toLocaleTimeString()}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
};
