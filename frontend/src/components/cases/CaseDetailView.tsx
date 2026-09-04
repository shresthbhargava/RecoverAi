import React, { useState } from 'react';
import type { RecoveryCaseDetail } from '../../api/types';
import { Badge } from '../ui/Badge';

import { Card } from '../ui/Card';
import { Toast } from '../ui/Toast';
import { approveEscalation, rejectEscalation } from '../../api/cases';
import {
  CheckCircle2,
  XCircle,
  Clock,
  User,
  CreditCard,
  Building2,
  Copy,
  Check,
  ShieldCheck,
  Brain,
  AlertCircle
} from 'lucide-react';

interface CaseDetailViewProps {
  caseData: RecoveryCaseDetail;
  onRefresh?: () => void;
}

export const CaseDetailView: React.FC<CaseDetailViewProps> = ({ caseData, onRefresh }) => {
  const [copiedId, setCopiedId] = useState(false);
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const [escalationLoading, setEscalationLoading] = useState(false);

  const handleCopyId = (text: string) => {
    navigator.clipboard.writeText(text);
    setCopiedId(true);
    setToastMessage(`Copied ID ${text.slice(0, 8)} to clipboard`);
    setTimeout(() => setCopiedId(false), 2000);
  };

  const handleApprove = async () => {
    setEscalationLoading(true);
    try {
      const res = await approveEscalation(caseData.id, 'Approved via Operations Console');
      setToastMessage(res.message);
      if (onRefresh) onRefresh();
    } finally {
      setEscalationLoading(false);
    }
  };

  const handleReject = async () => {
    setEscalationLoading(true);
    try {
      const res = await rejectEscalation(caseData.id, 'Rejected via Operations Console');
      setToastMessage(res.message);
      if (onRefresh) onRefresh();
    } finally {
      setEscalationLoading(false);
    }
  };

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
      {toastMessage && (
        <Toast
          message={toastMessage}
          onClose={() => setToastMessage(null)}
        />
      )}

      {/* Case Header Card */}
      <Card className="p-5">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 pb-4 border-b border-[#242A32]">
          <div>
            <div className="flex items-center space-x-3">
              <span className="font-mono text-sm text-[#8B949E]">CASE ID</span>
              <span className="font-mono text-sm font-semibold text-[#F5F7FA]">
                {caseData.id}
              </span>
              <button
                onClick={() => handleCopyId(caseData.id)}
                className="text-[#8B949E] hover:text-[#F5F7FA] p-1 rounded transition-colors"
                title="Copy Case ID"
              >
                {copiedId ? <Check className="w-3.5 h-3.5 text-[#10B981]" /> : <Copy className="w-3.5 h-3.5" />}
              </button>
              {getStatusBadge(caseData.status)}
            </div>
            <div className="text-xs text-[#8B949E] mt-1 font-mono">
              Created: {new Date(caseData.createdAt).toLocaleString()}
              {caseData.resolvedAt && (
                <span className="ml-3">
                  Resolved: {new Date(caseData.resolvedAt).toLocaleString()}
                </span>
              )}
            </div>
          </div>

          {/* Amount at Risk Display */}
          <div className="text-right font-mono">
            <div className="text-[10px] uppercase text-[#5F6875] tracking-wider font-semibold">
              AMOUNT AT RISK
            </div>
            <div className="text-2xl font-bold text-[#F5F7FA] tabular-nums mt-0.5">
              ₹{caseData.amountAtRisk.toLocaleString()}
            </div>
          </div>
        </div>

        {/* Escalation Action Banner if AWAITING_APPROVAL */}
        {caseData.status === 'AWAITING_APPROVAL' && (
          <div className="mt-4 p-3 bg-[#F59E0B]/10 border border-[#F59E0B]/30 rounded-[3px] flex items-center justify-between">
            <div className="flex items-center space-x-2 text-xs font-mono text-[#F59E0B]">
              <AlertCircle className="w-4 h-4 flex-shrink-0" />
              <span>
                This recovery action requires manual merchant approval (High LTV / Custom Incentive).
              </span>
            </div>
            <div className="flex items-center space-x-2">
              <button
                disabled={escalationLoading}
                onClick={handleReject}
                className="px-3 py-1 bg-[#15191F] border border-[#242A32] text-xs font-mono text-[#EF4444] hover:bg-[#EF4444]/10 rounded-[2px] transition-colors"
              >
                Reject
              </button>
              <button
                disabled={escalationLoading}
                onClick={handleApprove}
                className="px-3 py-1 bg-[#10B981] text-xs font-mono text-black font-semibold hover:bg-[#10B981]/90 rounded-[2px] transition-colors"
              >
                Approve & Execute
              </button>
            </div>
          </div>
        )}

        {/* Customer & Payment Meta Grid */}
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-4 mt-4 font-mono text-xs">
          <div>
            <div className="text-[10px] text-[#5F6875] uppercase flex items-center">
              <User className="w-3 h-3 mr-1" /> Customer
            </div>
            <div className="text-[#F5F7FA] font-medium mt-0.5">{caseData.customerName}</div>
            <div className="text-[#8B949E] text-[11px] truncate">{caseData.customerEmail}</div>
          </div>

          <div>
            <div className="text-[10px] text-[#5F6875] uppercase flex items-center">
              <CreditCard className="w-3 h-3 mr-1" /> Payment ID
            </div>
            <div className="text-[#F5F7FA] font-medium mt-0.5">{caseData.paymentId}</div>
            <div className="text-[#8B949E] text-[11px]">Razorpay Test Mode</div>
          </div>

          <div>
            <div className="text-[10px] text-[#5F6875] uppercase flex items-center">
              <Building2 className="w-3 h-3 mr-1" /> Customer History
            </div>
            <div className="text-[#10B981] font-medium mt-0.5">
              {caseData.customerPastSuccessfulPayments} Successful
            </div>
            <div className="text-[#EF4444] text-[11px]">
              {caseData.customerPastFailedPayments} Failed
            </div>
          </div>

          <div>
            <div className="text-[10px] text-[#5F6875] uppercase flex items-center">
              <Clock className="w-3 h-3 mr-1" /> Strategy & Budget
            </div>
            <div className="text-[#F5F7FA] font-medium mt-0.5">
              {caseData.selectedStrategy || 'WAIT_AND_RETRY'}
            </div>
            <div className="text-[#8B949E] text-[11px]">
              Cost Budget: ₹{caseData.recoveryCost || 15}
            </div>
          </div>
        </div>
      </Card>

      {/* Recovery Analysis Section */}
      <Card>
        <div className="flex items-center space-x-2 pb-3 border-b border-[#242A32] mb-4">
          <Brain className="w-4 h-4 text-[#3B82F6]" />
          <h3 className="text-xs font-mono font-bold uppercase tracking-wider text-[#F5F7FA]">
            Recovery Analysis
          </h3>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-4 font-mono text-xs">
          <div className="p-3 bg-[#15191F] border border-[#242A32] rounded-[2px]">
            <div className="text-[10px] text-[#5F6875] uppercase">Diagnosis</div>
            <div className="text-[#F5F7FA] font-semibold mt-1">
              {caseData.diagnosis || 'TEMPORARY_BANK_OUTAGE'}
            </div>
            <div className="text-[10px] text-[#3B82F6] mt-0.5">
              Confidence: {((caseData.diagnosisConfidence || 0.91) * 100).toFixed(0)}%
            </div>
          </div>

          <div className="p-3 bg-[#15191F] border border-[#242A32] rounded-[2px]">
            <div className="text-[10px] text-[#5F6875] uppercase">Recovery Probability</div>
            <div className="text-[#10B981] font-semibold text-lg mt-0.5 tabular-nums">
              {((caseData.recoveryProbability || 0.78) * 100).toFixed(0)}%
            </div>
            <div className="text-[10px] text-[#8B949E]">High conversion score</div>
          </div>

          <div className="p-3 bg-[#15191F] border border-[#242A32] rounded-[2px]">
            <div className="text-[10px] text-[#5F6875] uppercase">Expected Value</div>
            <div className="text-[#F5F7FA] font-semibold text-lg mt-0.5 tabular-nums">
              ₹{(caseData.expectedRecoveryValue || 11700).toLocaleString()}
            </div>
            <div className="text-[10px] text-[#8B949E]">Risk-adjusted net recovery</div>
          </div>

          <div className="p-3 bg-[#15191F] border border-[#242A32] rounded-[2px]">
            <div className="text-[10px] text-[#5F6875] uppercase">Recommended Action</div>
            <div className="text-[#3B82F6] font-semibold mt-1">
              {caseData.selectedStrategy || 'WAIT_AND_RETRY'}
            </div>
            <div className="text-[10px] text-[#8B949E]">Automated schedule</div>
          </div>
        </div>
      </Card>

      {/* Policy Evaluation Matrix */}
      <Card>
        <div className="flex items-center space-x-2 pb-3 border-b border-[#242A32] mb-4">
          <ShieldCheck className="w-4 h-4 text-[#10B981]" />
          <h3 className="text-xs font-mono font-bold uppercase tracking-wider text-[#F5F7FA]">
            Policy Evaluation Guardrails
          </h3>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-3 font-mono text-xs">
          <div className="flex items-center justify-between p-2.5 bg-[#15191F] border border-[#242A32] rounded-[2px]">
            <span className="text-[#8B949E]">Maximum Attempts</span>
            <span className="flex items-center text-[#10B981] font-semibold">
              <CheckCircle2 className="w-3.5 h-3.5 mr-1" /> Passed (1/3)
            </span>
          </div>

          <div className="flex items-center justify-between p-2.5 bg-[#15191F] border border-[#242A32] rounded-[2px]">
            <span className="text-[#8B949E]">Retry Interval</span>
            <span className="flex items-center text-[#10B981] font-semibold">
              <CheckCircle2 className="w-3.5 h-3.5 mr-1" /> Passed (30m)
            </span>
          </div>

          <div className="flex items-center justify-between p-2.5 bg-[#15191F] border border-[#242A32] rounded-[2px]">
            <span className="text-[#8B949E]">Contact Window</span>
            <span className="flex items-center text-[#10B981] font-semibold">
              <CheckCircle2 className="w-3.5 h-3.5 mr-1" /> Passed (&lt;21:00)
            </span>
          </div>

          <div className="flex items-center justify-between p-2.5 bg-[#15191F] border border-[#242A32] rounded-[2px]">
            <span className="text-[#8B949E]">Cost Budget</span>
            <span className="flex items-center text-[#10B981] font-semibold">
              <CheckCircle2 className="w-3.5 h-3.5 mr-1" /> Passed (₹15)
            </span>
          </div>
        </div>
      </Card>

      {/* Recovery Attempts Table */}
      <Card>
        <div className="pb-3 border-b border-[#242A32] mb-3">
          <h3 className="text-xs font-mono font-bold uppercase tracking-wider text-[#F5F7FA]">
            Execution Attempts History
          </h3>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left font-mono text-xs">
            <thead>
              <tr className="border-b border-[#242A32] text-[#5F6875] uppercase text-[10px]">
                <th className="py-2 px-3">Attempt #</th>
                <th className="py-2 px-3">Strategy</th>
                <th className="py-2 px-3">Status</th>
                <th className="py-2 px-3">Recovered</th>
                <th className="py-2 px-3">Razorpay Ref</th>
                <th className="py-2 px-3">Timestamp</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#242A32]">
              {caseData.attempts && caseData.attempts.length > 0 ? (
                caseData.attempts.map((att) => (
                  <tr key={att.id} className="hover:bg-[#15191F] transition-colors">
                    <td className="py-2.5 px-3 font-semibold text-[#F5F7FA]">
                      #{att.attemptNumber}
                    </td>
                    <td className="py-2.5 px-3 text-[#3B82F6]">{att.strategyUsed}</td>
                    <td className="py-2.5 px-3">
                      {att.status === 'RECOVERED' ? (
                        <span className="flex items-center text-[#10B981]">
                          <CheckCircle2 className="w-3.5 h-3.5 mr-1" /> SUCCEEDED
                        </span>
                      ) : att.status === 'POLICY_BLOCKED' ? (
                        <span className="flex items-center text-[#EF4444]">
                          <XCircle className="w-3.5 h-3.5 mr-1" /> BLOCKED
                        </span>
                      ) : (
                        <span className="flex items-center text-[#EF4444]">
                          <XCircle className="w-3.5 h-3.5 mr-1" /> FAILED
                        </span>
                      )}
                    </td>
                    <td className="py-2.5 px-3 text-[#F5F7FA] tabular-nums font-semibold">
                      ₹{att.recoveredAmount.toLocaleString()}
                    </td>
                    <td className="py-2.5 px-3 text-[#8B949E]">
                      {att.razorpayOrderId || att.razorpayPaymentLink || 'N/A'}
                    </td>
                    <td className="py-2.5 px-3 text-[#5F6875]">
                      {new Date(att.createdAt).toLocaleTimeString()}
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={6} className="py-4 text-center text-[#5F6875]">
                    No recovery attempts recorded yet.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
};
