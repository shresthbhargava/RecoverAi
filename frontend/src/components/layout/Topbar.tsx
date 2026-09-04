import React, { useEffect, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { Menu, Activity, Shield } from 'lucide-react';
import { getSystemHealth } from '../../api/health';

interface TopbarProps {
  onMenuClick?: () => void;
}

const pageTitles: Record<string, { title: string; subtitle: string }> = {
  '/dashboard': {
    title: 'Command Center',
    subtitle: 'Monitor payment recovery in real time.',
  },
  '/cases': {
    title: 'Recovery Cases',
    subtitle: 'Manage and investigate merchant payment recovery cases.',
  },
  '/activity': {
    title: 'Live Recovery Activity',
    subtitle: 'Watch RecoverAI move from detection to recovery.',
  },
  '/decisions': {
    title: 'Recovery Decisions',
    subtitle: 'Audit every strategy decision and economic guardrail evaluation.',
  },
  '/policies': {
    title: 'Recovery Policies',
    subtitle: 'Guardrails that every recovery action must satisfy.',
  },
  '/webhooks': {
    title: 'Webhook Events',
    subtitle: 'Razorpay webhook signature verification & idempotency audit log.',
  },
  '/health': {
    title: 'System Health',
    subtitle: 'Service connectivity, Grok fallback status & environment metrics.',
  },
};

export const Topbar: React.FC<TopbarProps> = ({ onMenuClick }) => {
  const location = useLocation();
  const [healthStatus, setHealthStatus] = useState<'UP' | 'DEGRADED'>('UP');

  useEffect(() => {
    getSystemHealth()
      .then((res) => {
        setHealthStatus(res.mode === 'FULL' ? 'UP' : 'DEGRADED');
      })
      .catch(() => setHealthStatus('DEGRADED'));
  }, []);

  // Handle case detail title matching (/cases/:id)
  let currentMeta = pageTitles[location.pathname];
  if (!currentMeta && location.pathname.startsWith('/cases/')) {
    currentMeta = {
      title: 'Recovery Case Detail',
      subtitle: 'Deep-dive investigation and policy execution history.',
    };
  }

  const title = currentMeta?.title || 'Command Center';
  const subtitle = currentMeta?.subtitle || 'Monitor payment recovery in real time.';

  return (
    <header className="sticky top-0 z-30 flex items-center justify-between h-14 px-4 lg:px-6 bg-[#0B0D10]/95 backdrop-blur-sm border-b border-[#242A32]">
      <div className="flex items-center space-x-3">
        <button
          onClick={onMenuClick}
          className="lg:hidden text-[#8B949E] hover:text-[#F5F7FA] p-1.5 rounded hover:bg-[#15191F]"
          aria-label="Open menu"
        >
          <Menu className="w-5 h-5" />
        </button>
        <div>
          <h1 className="text-sm font-semibold text-[#F5F7FA] tracking-tight font-sans">
            {title}
          </h1>
          <p className="text-[11px] text-[#8B949E] font-normal hidden sm:block">
            {subtitle}
          </p>
        </div>
      </div>

      {/* Right Side Status Indicators */}
      <div className="flex items-center space-x-2 sm:space-x-3 text-xs font-mono">
        <div className="flex items-center px-2 py-1 bg-[#111419] border border-[#242A32] rounded-[2px] text-[#8B949E]">
          <span
            className={`w-1.5 h-1.5 rounded-full mr-1.5 ${
              healthStatus === 'UP' ? 'bg-[#10B981]' : 'bg-[#F59E0B]'
            }`}
          />
          <span className="hidden sm:inline">Backend Status</span>
          <span className="ml-1 text-[#F5F7FA]">
            ● {healthStatus === 'UP' ? 'UP' : 'DEGRADED'}
          </span>
        </div>

        <div className="hidden md:flex items-center px-2 py-1 bg-[#111419] border border-[#242A32] rounded-[2px] text-[#8B949E]">
          <Shield className="w-3 h-3 mr-1.5 text-[#10B981]" />
          <span>Razorpay Test Mode</span>
        </div>

        <div className="flex items-center px-2 py-1 bg-[#15191F] border border-[#242A32] rounded-[2px] text-[#F5F7FA]">
          <Activity className="w-3 h-3 mr-1.5 text-[#3B82F6]" />
          <span>Demo Mode</span>
        </div>
      </div>
    </header>
  );
};
