import React from 'react';
import {
  AlertTriangle,
  Search,
  Stethoscope,
  Brain,
  ShieldCheck,
  Zap,
  CheckCircle2
} from 'lucide-react';

interface Stage {
  key: string;
  label: string;
  count: string;
  status: 'active' | 'success' | 'warning' | 'blocked';
  icon: React.ComponentType<{ className?: string }>;
}

export const PipelineVisual: React.FC = () => {
  const stages: Stage[] = [
    { key: 'fail', label: 'FAILED PAYMENT', count: '142', status: 'warning', icon: AlertTriangle },
    { key: 'detect', label: 'DETECT', count: '142', status: 'active', icon: Search },
    { key: 'diag', label: 'DIAGNOSE', count: '142', status: 'active', icon: Stethoscope },
    { key: 'decide', label: 'DECIDE', count: '137', status: 'active', icon: Brain },
    { key: 'policy', label: 'POLICY CHECK', count: '137', status: 'active', icon: ShieldCheck },
    { key: 'exec', label: 'EXECUTE', count: '89', status: 'active', icon: Zap },
    { key: 'recover', label: 'RECOVERED', count: '61', status: 'success', icon: CheckCircle2 },
  ];

  return (
    <div className="w-full overflow-x-auto py-2">
      <div className="flex items-center min-w-[700px] justify-between">
        {stages.map((stage, idx) => {
          const Icon = stage.icon;
          const isLast = idx === stages.length - 1;

          const colorClasses = {
            active: 'border-[#3B82F6] bg-[#3B82F6]/10 text-[#3B82F6]',
            success: 'border-[#10B981] bg-[#10B981]/10 text-[#10B981]',
            warning: 'border-[#F59E0B] bg-[#F59E0B]/10 text-[#F59E0B]',
            blocked: 'border-[#EF4444] bg-[#EF4444]/10 text-[#EF4444]',
          }[stage.status];

          return (
            <React.Fragment key={stage.key}>
              <div className="flex flex-col items-center group relative cursor-pointer">
                <div
                  className={`w-9 h-9 rounded-[4px] border flex items-center justify-center transition-all ${colorClasses}`}
                >
                  <Icon className="w-4 h-4" />
                </div>
                <div className="text-[10px] font-mono font-semibold uppercase tracking-wider text-[#F5F7FA] mt-2 text-center whitespace-nowrap">
                  {stage.label}
                </div>
                <div className="text-[11px] font-mono text-[#8B949E] mt-0.5">
                  {stage.count} events
                </div>
              </div>

              {!isLast && (
                <div className="flex-1 px-2 flex items-center justify-center">
                  <div className="h-[1px] w-full bg-[#242A32] relative">
                    <div className="absolute right-0 top-1/2 -translate-y-1/2 w-1 h-1 rounded-full bg-[#3B4452]" />
                  </div>
                </div>
              )}
            </React.Fragment>
          );
        })}
      </div>
    </div>
  );
};
