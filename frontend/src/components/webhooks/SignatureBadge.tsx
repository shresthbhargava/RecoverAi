import React from 'react';
import { CheckCircle2, XCircle } from 'lucide-react';

interface SignatureBadgeProps {
  isValid: boolean;
}

export const SignatureBadge: React.FC<SignatureBadgeProps> = ({ isValid }) => {
  if (isValid) {
    return (
      <span className="inline-flex items-center px-2 py-0.5 text-[11px] font-mono font-bold rounded-[2px] bg-[#10B981]/15 text-[#10B981] border border-[#10B981]/30">
        <CheckCircle2 className="w-3 h-3 mr-1" />
        ✓ VALID
      </span>
    );
  }

  return (
    <span className="inline-flex items-center px-2 py-0.5 text-[11px] font-mono font-bold rounded-[2px] bg-[#EF4444]/15 text-[#EF4444] border border-[#EF4444]/30">
      <XCircle className="w-3 h-3 mr-1" />
      ✕ INVALID
    </span>
  );
};
