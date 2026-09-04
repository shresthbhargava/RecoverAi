import React, { useState, useEffect } from 'react';
import { Modal } from '../ui/Modal';
import type { PolicyRule } from '../../api/types';
import { updatePolicyRule } from '../../api/policies';


interface PolicyEditorModalProps {
  policy: PolicyRule | null;
  isOpen: boolean;
  onClose: () => void;
  onSaved: (updated: PolicyRule) => void;
}

export const PolicyEditorModal: React.FC<PolicyEditorModalProps> = ({
  policy,
  isOpen,
  onClose,
  onSaved,
}) => {
  const [value, setValue] = useState('');
  const [enabled, setEnabled] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (policy) {
      setValue(policy.value);
      setEnabled(policy.enabled);
    }
  }, [policy]);

  if (!policy) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      const updated = await updatePolicyRule(policy.id, value, enabled);
      onSaved(updated);
      onClose();
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={`Configure ${policy.name}`}
      subtitle={policy.description}
    >
      <form onSubmit={handleSubmit} className="space-y-4 font-mono">
        <div>
          <label className="block text-[10px] text-[#5F6875] uppercase mb-1">
            Rule Name & Type
          </label>
          <div className="text-xs text-[#F5F7FA] font-bold p-2 bg-[#15191F] border border-[#242A32] rounded-[2px] flex items-center justify-between">
            <span>{policy.name}</span>
            <span className="text-[10px] text-[#8B949E] px-1 bg-[#242A32] rounded">
              {policy.ruleType}
            </span>
          </div>
        </div>

        <div>
          <label className="block text-[10px] text-[#5F6875] uppercase mb-1">
            Policy Value Limit
          </label>
          <input
            type="text"
            value={value}
            onChange={(e) => setValue(e.target.value)}
            className="w-full bg-[#15191F] border border-[#242A32] text-[#F5F7FA] text-xs px-3 py-2 rounded-[2px] focus:outline-none focus:border-[#3B82F6]"
            required
          />
        </div>

        <div className="flex items-center justify-between p-2.5 bg-[#15191F] border border-[#242A32] rounded-[2px]">
          <div>
            <div className="text-xs font-semibold text-[#F5F7FA]">Enforce Policy Rule</div>
            <div className="text-[10px] text-[#8B949E]">
              {enabled ? 'Rule active in engine' : 'Rule bypassed'}
            </div>
          </div>
          <button
            type="button"
            onClick={() => setEnabled(!enabled)}
            className={`w-10 h-5 flex items-center rounded-full p-0.5 transition-colors ${
              enabled ? 'bg-[#10B981]' : 'bg-[#242A32]'
            }`}
          >
            <div
              className={`w-4 h-4 rounded-full bg-white transform transition-transform ${
                enabled ? 'translate-x-5' : 'translate-x-0'
              }`}
            />
          </button>
        </div>

        <div className="flex items-center justify-end space-x-2 pt-3 border-t border-[#242A32]">
          <button
            type="button"
            onClick={onClose}
            className="px-3 py-1.5 bg-[#15191F] border border-[#242A32] text-xs text-[#8B949E] hover:text-[#F5F7FA] rounded-[2px] transition-colors"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={saving}
            className="px-4 py-1.5 bg-white text-black font-semibold text-xs rounded-[2px] hover:bg-[#F5F7FA] transition-colors"
          >
            {saving ? 'Saving...' : 'Save Policy'}
          </button>
        </div>
      </form>
    </Modal>
  );
};
