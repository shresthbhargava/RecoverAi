import React, { useState } from 'react';
import { ShieldAlert, AlertTriangle, ChevronDown, ChevronUp, Lock } from 'lucide-react';
import { Badge } from '../ui/Badge';

interface PolicyBlockedCardProps {
  timestamp?: string;
  proposedAction?: string;
  reason?: string;
  attempts?: string;
  caseId?: string;
}

export const PolicyBlockedCard: React.FC<PolicyBlockedCardProps> = ({
  timestamp = '10:45:00',
  proposedAction = 'SEND_PAYMENT_LINK',
  reason = 'Maximum recovery attempts exceeded',
  attempts = '3 / 3',
  caseId = '9a12c4bf-7019-482f-b12a-8819204910ef',
}) => {
  const [expanded, setExpanded] = useState(true);

  return (
    <div className="bg-[#111419] border border-[#EF4444]/40 rounded-[4px] p-4 text-[#F5F7FA] relative overflow-hidden my-3 shadow-lg">
      <div className="flex items-start justify-between">
        <div className="flex items-center space-x-2.5">
          <div className="w-8 h-8 rounded-[2px] bg-[#EF4444]/15 border border-[#EF4444]/40 flex items-center justify-center text-[#EF4444]">
            <ShieldAlert className="w-4 h-4" />
          </div>
          <div>
            <div className="flex items-center space-x-2">
              <span className="font-mono text-xs text-[#EF4444] font-bold tracking-wider uppercase">
                POLICY BLOCKED
              </span>
              <Badge variant="danger" size="sm">
                GUARDRAIL ENFORCED
              </Badge>
            </div>
            <div className="text-xs font-mono text-[#8B949E] mt-0.5">
              Target Strategy: <span className="text-[#F5F7FA] font-semibold">{proposedAction}</span>
            </div>
          </div>
        </div>

        <div className="flex items-center space-x-3 text-xs font-mono">
          <span className="text-[#5F6875]">{timestamp}</span>
          <button
            onClick={() => setExpanded(!expanded)}
            className="text-[#8B949E] hover:text-[#F5F7FA] p-1 rounded hover:bg-[#15191F] transition-colors"
          >
            {expanded ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
          </button>
        </div>
      </div>

      {expanded && (
        <div className="mt-4 pt-3 border-t border-[#242A32] space-y-3 font-mono text-xs">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3 bg-[#15191F] p-3 rounded-[2px] border border-[#242A32]">
            <div>
              <div className="text-[10px] text-[#5F6875] uppercase tracking-wider">Policy Violation Reason</div>
              <div className="text-[#F5F7FA] font-medium mt-0.5 flex items-center">
                <AlertTriangle className="w-3.5 h-3.5 text-[#F59E0B] mr-1.5 flex-shrink-0" />
                {reason}
              </div>
            </div>

            <div>
              <div className="text-[10px] text-[#5F6875] uppercase tracking-wider">Attempt Counter</div>
              <div className="text-[#EF4444] font-semibold mt-0.5 tabular-nums">
                Current attempts: {attempts}
              </div>
            </div>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 pt-1">
            <div className="p-2 bg-[#0B0D10] border border-[#242A32] rounded-[2px]">
              <div className="text-[10px] text-[#5F6875] uppercase">Proposed Action</div>
              <div className="text-[#F5F7FA] font-mono mt-0.5 truncate">{proposedAction}</div>
            </div>

            <div className="p-2 bg-[#0B0D10] border border-[#242A32] rounded-[2px]">
              <div className="text-[10px] text-[#5F6875] uppercase">AI Decision</div>
              <div className="text-[#EF4444] font-mono font-bold mt-0.5">BLOCKED</div>
            </div>

            <div className="p-2 bg-[#0B0D10] border border-[#242A32] rounded-[2px]">
              <div className="text-[10px] text-[#5F6875] uppercase">Razorpay API</div>
              <div className="text-[#8B949E] font-mono font-bold mt-0.5 flex items-center text-[11px]">
                <Lock className="w-3 h-3 mr-1 text-[#EF4444]" />
                NOT CALLED
              </div>
            </div>

            <div className="p-2 bg-[#0B0D10] border border-[#242A32] rounded-[2px]">
              <div className="text-[10px] text-[#5F6875] uppercase">Case ID</div>
              <div className="text-[#8B949E] font-mono mt-0.5 truncate">{caseId.slice(0, 8)}...</div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
