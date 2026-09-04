import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { Skeleton } from '../components/ui/Skeleton';
import { getRecoveryCases } from '../api/cases';
import type { RecoveryCaseSummary } from '../api/types';
import { ArrowUpRight, Search, Filter } from 'lucide-react';


export const Cases: React.FC = () => {
  const navigate = useNavigate();
  const [cases, setCases] = useState<RecoveryCaseSummary[]>([]);
  const [filteredCases, setFilteredCases] = useState<RecoveryCaseSummary[]>([]);
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [searchTerm, setSearchTerm] = useState<string>('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getRecoveryCases()
      .then((res) => {
        setCases(res);
        setFilteredCases(res);
      })
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    let result = cases;
    if (statusFilter !== 'ALL') {
      result = result.filter((c) => c.status === statusFilter);
    }
    if (searchTerm) {
      const term = searchTerm.toLowerCase();
      result = result.filter(
        (c) =>
          c.customerName.toLowerCase().includes(term) ||
          c.id.toLowerCase().includes(term) ||
          c.selectedStrategy.toLowerCase().includes(term)
      );
    }
    setFilteredCases(result);
  }, [statusFilter, searchTerm, cases]);

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'RECOVERED':
        return <Badge variant="success">RECOVERED</Badge>;
      case 'POLICY_BLOCKED':
        return <Badge variant="danger">POLICY BLOCKED</Badge>;
      case 'FAILED':
        return <Badge variant="danger">FAILED</Badge>;
      case 'AWAITING_APPROVAL':
        return <Badge variant="warning">AWAITING APPROVAL</Badge>;
      case 'EXECUTING':
        return <Badge variant="info">EXECUTING</Badge>;
      default:
        return <Badge variant="neutral">{status}</Badge>;
    }
  };

  return (
    <div className="space-y-6">
      {/* Search and Filters Bar */}
      <Card className="p-3 flex flex-col sm:flex-row items-center justify-between gap-3">
        <div className="relative w-full sm:w-72">
          <Search className="w-4 h-4 text-[#5F6875] absolute left-3 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            placeholder="Search by customer or case ID..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="w-full bg-[#15191F] border border-[#242A32] text-[#F5F7FA] text-xs pl-9 pr-3 py-1.5 rounded-[2px] focus:outline-none focus:border-[#3B82F6] font-mono"
          />
        </div>

        <div className="flex items-center space-x-1.5 overflow-x-auto w-full sm:w-auto font-mono text-xs">
          <span className="text-[#5F6875] text-[10px] uppercase mr-1 flex items-center">
            <Filter className="w-3 h-3 mr-1" /> Filter:
          </span>
          {['ALL', 'RECOVERED', 'EXECUTING', 'AWAITING_APPROVAL', 'POLICY_BLOCKED', 'FAILED'].map((st) => (
            <button
              key={st}
              onClick={() => setStatusFilter(st)}
              className={`px-2.5 py-1 text-[11px] rounded-[2px] transition-colors whitespace-nowrap ${
                statusFilter === st
                  ? 'bg-[#15191F] text-white border border-[#242A32] font-semibold'
                  : 'text-[#8B949E] hover:text-[#F5F7FA]'
              }`}
            >
              {st.replace('_', ' ')}
            </button>
          ))}
        </div>
      </Card>

      {/* Cases Table */}
      <Card>
        {loading ? (
          <div className="space-y-3 p-2">
            <Skeleton height="30px" />
            <Skeleton height="30px" />
            <Skeleton height="30px" />
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left font-mono text-xs">
              <thead>
                <tr className="border-b border-[#242A32] text-[#5F6875] uppercase text-[10px]">
                  <th className="py-2.5 px-3">Case ID</th>
                  <th className="py-2.5 px-3">Customer</th>
                  <th className="py-2.5 px-3">Amount</th>
                  <th className="py-2.5 px-3">Diagnosis</th>
                  <th className="py-2.5 px-3">Strategy</th>
                  <th className="py-2.5 px-3">Attempts</th>
                  <th className="py-2.5 px-3">Status</th>
                  <th className="py-2.5 px-3">Created</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#242A32]">
                {filteredCases.length > 0 ? (
                  filteredCases.map((c) => (
                    <tr
                      key={c.id}
                      onClick={() => navigate(`/cases/${c.id}`)}
                      className="hover:bg-[#15191F] transition-colors cursor-pointer group"
                    >
                      <td className="py-3 px-3 font-semibold text-[#3B82F6] flex items-center space-x-1">
                        <span>{c.id.slice(0, 8)}...</span>
                        <ArrowUpRight className="w-3 h-3 opacity-0 group-hover:opacity-100 transition-opacity" />
                      </td>
                      <td className="py-3 px-3 text-[#F5F7FA] font-medium">{c.customerName}</td>
                      <td className="py-3 px-3 text-[#F5F7FA] tabular-nums font-bold">
                        ₹{c.amountAtRisk.toLocaleString()}
                      </td>
                      <td className="py-3 px-3 text-[#8B949E]">{c.diagnosis}</td>
                      <td className="py-3 px-3 text-[#F5F7FA]">{c.selectedStrategy}</td>
                      <td className="py-3 px-3 text-[#8B949E] tabular-nums">{c.attemptCount} / 3</td>
                      <td className="py-3 px-3">{getStatusBadge(c.status)}</td>
                      <td className="py-3 px-3 text-[#5F6875]">
                        {new Date(c.createdAt).toLocaleTimeString()}
                      </td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan={8} className="py-8 text-center text-[#5F6875]">
                      No recovery cases found matching filter criteria.
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
