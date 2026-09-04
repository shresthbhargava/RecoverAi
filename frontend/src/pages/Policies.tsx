import React, { useEffect, useState } from 'react';
import { Card } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { Skeleton } from '../components/ui/Skeleton';
import { Toast } from '../components/ui/Toast';
import { PolicyEditorModal } from '../components/policies/PolicyEditorModal';
import { getPolicies } from '../api/policies';
import type { PolicyRule } from '../api/types';
import { ShieldCheck, Edit3 } from 'lucide-react';


export const Policies: React.FC = () => {
  const [policies, setPolicies] = useState<PolicyRule[]>([]);
  const [selectedPolicy, setSelectedPolicy] = useState<PolicyRule | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  useEffect(() => {
    getPolicies()
      .then((res) => setPolicies(res))
      .finally(() => setLoading(false));
  }, []);

  const handleEdit = (policy: PolicyRule) => {
    setSelectedPolicy(policy);
    setModalOpen(true);
  };

  const handleSaved = (updated: PolicyRule) => {
    setPolicies((prev) => prev.map((p) => (p.id === updated.id ? updated : p)));
    setToastMessage(`Policy rule "${updated.name}" updated successfully.`);
  };

  return (
    <div className="space-y-6">
      {toastMessage && (
        <Toast
          message={toastMessage}
          onClose={() => setToastMessage(null)}
        />
      )}

      <Card className="p-4 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
        <div>
          <div className="flex items-center space-x-2">
            <ShieldCheck className="w-4 h-4 text-[#10B981]" />
            <h2 className="text-xs font-mono font-bold uppercase tracking-wider text-[#F5F7FA]">
              Deterministic Policy Engine Rules
            </h2>
          </div>
          <p className="text-xs text-[#8B949E] mt-1 font-sans">
            Every strategy proposed by RecoverAI must strictly satisfy these merchant-configured guardrails before execution.
          </p>
        </div>
        <div className="text-xs font-mono text-[#8B949E] bg-[#15191F] border border-[#242A32] px-2.5 py-1 rounded-[2px]">
          Engine Enforcing: <span className="text-[#10B981]">7 Active Rules</span>
        </div>
      </Card>

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
                  <th className="py-2.5 px-3">Policy Rule</th>
                  <th className="py-2.5 px-3">Type</th>
                  <th className="py-2.5 px-3">Enforced Limit</th>
                  <th className="py-2.5 px-3">Status</th>
                  <th className="py-2.5 px-3">Description</th>
                  <th className="py-2.5 px-3 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#242A32]">
                {policies.map((p) => (
                  <tr key={p.id} className="hover:bg-[#15191F] transition-colors group">
                    <td className="py-3 px-3 font-semibold text-[#F5F7FA]">{p.name}</td>
                    <td className="py-3 px-3 text-[#5F6875]">{p.ruleType}</td>
                    <td className="py-3 px-3 font-bold text-[#3B82F6] tabular-nums">{p.value}</td>
                    <td className="py-3 px-3">
                      {p.enabled ? (
                        <Badge variant="success">ENABLED</Badge>
                      ) : (
                        <Badge variant="neutral">DISABLED</Badge>
                      )}
                    </td>
                    <td className="py-3 px-3 text-[#8B949E] text-[11px] max-w-xs truncate">
                      {p.description}
                    </td>
                    <td className="py-3 px-3 text-right">
                      <button
                        onClick={() => handleEdit(p)}
                        className="px-2.5 py-1 bg-[#15191F] border border-[#242A32] hover:bg-[#242A32] text-xs font-mono text-[#F5F7FA] rounded-[2px] transition-colors inline-flex items-center"
                      >
                        <Edit3 className="w-3 h-3 mr-1 text-[#8B949E]" />
                        Edit
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <PolicyEditorModal
        policy={selectedPolicy}
        isOpen={modalOpen}
        onClose={() => setModalOpen(false)}
        onSaved={handleSaved}
      />
    </div>
  );
};
