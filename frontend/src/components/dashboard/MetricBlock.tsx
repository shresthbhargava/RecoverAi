import React from 'react';
import { Card } from '../ui/Card';

interface MetricBlockProps {
  label: string;
  value: string | number;
  subtext?: string;
  variant?: 'neutral' | 'success' | 'danger' | 'warning' | 'info';
}

export const MetricBlock: React.FC<MetricBlockProps> = ({
  label,
  value,
  subtext,
  variant = 'neutral',
}) => {
  const valueColor = {
    neutral: 'text-[#F5F7FA]',
    success: 'text-[#10B981]',
    danger: 'text-[#EF4444]',
    warning: 'text-[#F59E0B]',
    info: 'text-[#3B82F6]',
  }[variant];

  return (
    <Card className="py-3 px-3.5 flex flex-col justify-between">
      <div className="text-[10px] font-mono font-semibold uppercase tracking-wider text-[#5F6875]">
        {label}
      </div>
      <div className={`text-xl font-bold font-mono tabular-nums tracking-tight mt-1 ${valueColor}`}>
        {value}
      </div>
      {subtext && (
        <div className="text-[11px] font-mono text-[#8B949E] mt-0.5">
          {subtext}
        </div>
      )}
    </Card>
  );
};
