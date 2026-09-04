import React from 'react';
import { NavLink } from 'react-router-dom';
import {
  LayoutDashboard,
  FolderGit2,
  Activity,
  BrainCircuit,
  ShieldAlert,
  Webhook,
  ActivitySquare,
  Sparkles,
  ShieldCheck,
  X
} from 'lucide-react';

interface SidebarProps {
  isMobileOpen?: boolean;
  onMobileClose?: () => void;
}

export const Sidebar: React.FC<SidebarProps> = ({ isMobileOpen, onMobileClose }) => {
  const sections = [
    {
      heading: 'COMMAND CENTER',
      items: [
        { label: 'Overview', to: '/dashboard', icon: LayoutDashboard }
      ]
    },
    {
      heading: 'RECOVERY',
      items: [
        { label: 'Cases', to: '/cases', icon: FolderGit2 },
        { label: 'Live Activity', to: '/activity', icon: Activity }
      ]
    },
    {
      heading: 'INTELLIGENCE',
      items: [
        { label: 'Decisions', to: '/decisions', icon: BrainCircuit },
        { label: 'Policies', to: '/policies', icon: ShieldAlert }
      ]
    },
    {
      heading: 'SYSTEM',
      items: [
        { label: 'Webhooks', to: '/webhooks', icon: Webhook },
        { label: 'System Health', to: '/health', icon: ActivitySquare }
      ]
    }
  ];

  return (
    <>
      {/* Mobile backdrop */}
      {isMobileOpen && (
        <div
          className="fixed inset-0 z-40 bg-black/80 lg:hidden"
          onClick={onMobileClose}
        />
      )}

      <aside
        className={`fixed top-0 bottom-0 left-0 z-50 w-64 bg-[#0B0D10] border-r border-[#242A32] flex flex-col justify-between transition-transform duration-200 lg:translate-x-0 ${
          isMobileOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0'
        }`}
      >
        <div>
          {/* Header */}
          <div className="flex items-center justify-between px-5 h-14 border-b border-[#242A32]">
            <div className="flex items-center space-x-2.5">
              <div className="w-5 h-5 rounded-[2px] bg-white flex items-center justify-center font-mono font-bold text-black text-xs">
                R
              </div>
              <span className="font-semibold text-sm tracking-tight text-[#F5F7FA]">
                RecoverAI
              </span>
            </div>
            {isMobileOpen && (
              <button
                onClick={onMobileClose}
                className="lg:hidden text-[#8B949E] hover:text-white"
              >
                <X className="w-5 h-5" />
              </button>
            )}
          </div>

          {/* Navigation Links */}
          <nav className="p-3 space-y-6 overflow-y-auto max-h-[calc(100vh-180px)]">
            {sections.map((sec) => (
              <div key={sec.heading}>
                <div className="px-3 mb-2 text-[10px] font-mono font-semibold uppercase tracking-wider text-[#5F6875]">
                  {sec.heading}
                </div>
                <div className="space-y-0.5">
                  {sec.items.map((item) => {
                    const Icon = item.icon;
                    return (
                      <NavLink
                        key={item.to}
                        to={item.to}
                        onClick={onMobileClose}
                        className={({ isActive }) =>
                          `flex items-center px-3 py-1.5 text-xs font-medium rounded-[2px] transition-colors relative ${
                            isActive
                              ? 'bg-[#15191F] text-white border-l-2 border-white pl-2.5'
                              : 'text-[#8B949E] hover:text-[#F5F7FA] hover:bg-[#111419]'
                          }`
                        }
                      >
                        <Icon className="w-4 h-4 mr-2.5 flex-shrink-0 text-[#8B949E]" />
                        <span>{item.label}</span>
                      </NavLink>
                    );
                  })}
                </div>
              </div>
            ))}
          </nav>
        </div>

        {/* Bottom Section: DEMO MODE */}
        <div className="p-3 border-t border-[#242A32] bg-[#0B0D10]">
          <div className="px-3 py-1 text-[10px] font-mono font-semibold uppercase tracking-wider text-[#5F6875] mb-1">
            DEMO MODE
          </div>
          <div className="space-y-1.5 px-2 py-1 text-[11px] font-mono text-[#8B949E]">
            <div className="flex items-center justify-between">
              <span className="flex items-center text-[#8B949E]">
                <Sparkles className="w-3 h-3 mr-1.5 text-[#8B949E]" />
                Fallback AI
              </span>
              <span className="text-[10px] px-1 bg-[#15191F] border border-[#242A32] rounded text-[#F5F7FA]">
                ACTIVE
              </span>
            </div>
            <div className="flex items-center justify-between">
              <span className="flex items-center text-[#8B949E]">
                <ShieldCheck className="w-3 h-3 mr-1.5 text-[#10B981]" />
                Razorpay Test
              </span>
              <span className="text-[10px] px-1 bg-[#10B981]/10 text-[#10B981] border border-[#10B981]/30 rounded">
                READY
              </span>
            </div>
          </div>
        </div>
      </aside>
    </>
  );
};
