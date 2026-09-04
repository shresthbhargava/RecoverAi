import React from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import type { LiveActivityEvent } from '../../api/types';
import { PolicyBlockedCard } from './PolicyBlockedCard';

import {
  AlertTriangle,
  Stethoscope,
  Brain,
  ShieldCheck,
  Zap,
  Webhook,
  CheckCircle2
} from 'lucide-react';
import { Badge } from '../ui/Badge';

interface EventTimelineProps {
  events: LiveActivityEvent[];
}

export const EventTimeline: React.FC<EventTimelineProps> = ({ events }) => {
  const getStageIcon = (stage: LiveActivityEvent['stage']) => {
    switch (stage) {
      case 'PAYMENT_FAILED':
        return <AlertTriangle className="w-3.5 h-3.5 text-[#F59E0B]" />;
      case 'DIAGNOSING':
        return <Stethoscope className="w-3.5 h-3.5 text-[#3B82F6]" />;
      case 'AI_DECISION':
        return <Brain className="w-3.5 h-3.5 text-[#3B82F6]" />;
      case 'POLICY_CHECK':
        return <ShieldCheck className="w-3.5 h-3.5 text-[#10B981]" />;
      case 'EXECUTED':
        return <Zap className="w-3.5 h-3.5 text-[#3B82F6]" />;
      case 'WEBHOOK_RECEIVED':
        return <Webhook className="w-3.5 h-3.5 text-[#10B981]" />;
      case 'RECOVERED':
        return <CheckCircle2 className="w-3.5 h-3.5 text-[#10B981]" />;
      case 'POLICY_BLOCKED':
        return <AlertTriangle className="w-3.5 h-3.5 text-[#EF4444]" />;
      default:
        return <Brain className="w-3.5 h-3.5 text-[#8B949E]" />;
    }
  };

  const getStageBadge = (stage: LiveActivityEvent['stage']) => {
    switch (stage) {
      case 'RECOVERED':
        return <Badge variant="success">RECOVERED</Badge>;
      case 'POLICY_BLOCKED':
        return <Badge variant="danger">POLICY BLOCKED</Badge>;
      case 'POLICY_CHECK':
        return <Badge variant="info">PASSED</Badge>;
      case 'EXECUTED':
        return <Badge variant="info">EXECUTED</Badge>;
      default:
        return <Badge variant="neutral">{stage}</Badge>;
    }
  };

  return (
    <div className="relative pl-6 space-y-4 before:absolute before:left-2.5 before:top-2 before:bottom-2 before:w-[1px] before:bg-[#242A32]">
      <AnimatePresence initial={false}>
        {events.map((evt) => {
          if (evt.stage === 'POLICY_BLOCKED' && evt.blockedInfo) {
            return (
              <motion.div
                key={evt.id}
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0 }}
                transition={{ duration: 0.25, ease: 'easeOut' }}
                className="relative"
              >
                <div className="absolute -left-6 top-4 w-3 h-3 rounded-full bg-[#EF4444] border-2 border-[#0B0D10] ring-4 ring-[#EF4444]/20 animate-pulse" />
                <PolicyBlockedCard
                  timestamp={evt.timestamp}
                  proposedAction={evt.blockedInfo.proposedAction}
                  reason={evt.blockedInfo.reason}
                  attempts={evt.blockedInfo.attempts}
                  caseId={evt.caseId}
                />
              </motion.div>
            );
          }

          return (
            <motion.div
              key={evt.id}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.25, ease: 'easeOut' }}
              className="relative bg-[#111419] border border-[#242A32] rounded-[4px] p-3.5 hover:border-[#3B4452] transition-colors"
            >
              {/* Timeline Indicator Dot */}
              <div className="absolute -left-[25px] top-4 w-2.5 h-2.5 rounded-full bg-[#3B82F6] border-2 border-[#0B0D10] ring-2 ring-[#3B82F6]/30" />

              <div className="flex items-start justify-between">
                <div className="flex items-center space-x-2">
                  <div className="p-1 rounded bg-[#15191F] border border-[#242A32]">
                    {getStageIcon(evt.stage)}
                  </div>
                  <div>
                    <div className="flex items-center space-x-2">
                      <span className="font-mono text-xs font-semibold text-[#F5F7FA]">
                        {evt.title}
                      </span>
                      {getStageBadge(evt.stage)}
                    </div>
                    <p className="text-xs text-[#8B949E] mt-0.5 font-sans">
                      {evt.subtitle}
                    </p>
                  </div>
                </div>

                <div className="text-right font-mono text-xs text-[#5F6875]">
                  {evt.timestamp}
                </div>
              </div>

              {/* Metadata Key-Values */}
              {evt.metadata && (
                <div className="mt-3 pt-2 border-t border-[#242A32] flex flex-wrap gap-3 font-mono text-[11px]">
                  {Object.entries(evt.metadata).map(([k, v]) => (
                    <div key={k} className="flex items-center space-x-1">
                      <span className="text-[#5F6875] uppercase">{k}:</span>
                      <span className="text-[#F5F7FA] font-medium">{String(v)}</span>
                    </div>
                  ))}
                </div>
              )}
            </motion.div>
          );
        })}
      </AnimatePresence>
    </div>
  );
};
