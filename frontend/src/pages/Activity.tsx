import React, { useEffect, useState } from 'react';
import { EventTimeline } from '../components/activity/EventTimeline';
import { Card } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { mockLiveActivityEvents } from '../api/mockData';
import type { LiveActivityEvent } from '../api/types';
import { subscribeToLiveDecisions } from '../api/decisions';
import { processBatch } from '../api/batch';
import { Radio, RefreshCw, ShieldAlert, Sparkles } from 'lucide-react';

export const Activity: React.FC = () => {
  const [events, setEvents] = useState<LiveActivityEvent[]>(mockLiveActivityEvents);
  const [isLive, setIsLive] = useState(true);
  const [isProcessing, setIsProcessing] = useState(false);

  useEffect(() => {
    // Subscribe to backend SSE stream
    const unsubscribe = subscribeToLiveDecisions(
      (evt: any) => {
        if (evt && evt.stage) {
          const timestamp = evt.at ? new Date(evt.at).toLocaleTimeString() : new Date().toLocaleTimeString();
          let mappedStage: LiveActivityEvent['stage'] = 'AI_DECISION';
          
          if (evt.stage === 'DETECTED') mappedStage = 'PAYMENT_FAILED';
          else if (evt.stage === 'DIAGNOSED') mappedStage = 'DIAGNOSING';
          else if (evt.stage === 'STRATEGY_SELECTED') mappedStage = 'AI_DECISION';
          else if (evt.stage === 'POLICY_BLOCKED') mappedStage = 'POLICY_BLOCKED';
          else if (evt.stage === 'EXECUTED') mappedStage = 'EXECUTED';
          else if (evt.stage === 'WEBHOOK_RECEIVED') mappedStage = 'WEBHOOK_RECEIVED';
          else if (evt.stage === 'WEBHOOK_RECONCILED' || evt.stage === 'RESULT') mappedStage = 'RECOVERED';

          const newEvt: LiveActivityEvent = {
            id: evt.id || `stream_${Date.now()}_${Math.random()}`,
            timestamp,
            stage: mappedStage,
            title: evt.stage.replace('_', ' '),
            subtitle: evt.message || 'Event processed by agent pipeline',
            caseId: evt.caseId || 'case_active',
            metadata: evt.data || {},
            blockedInfo: evt.stage === 'POLICY_BLOCKED' ? {
              proposedAction: evt.data?.action || 'SEND_PAYMENT_LINK',
              reason: evt.message || 'Maximum recovery attempts exceeded',
              attempts: '3 / 3',
              aiDecision: 'BLOCKED',
              razorpayApi: 'NOT CALLED'
            } : undefined
          };
          setEvents((prev) => [newEvt, ...prev]);
        }
      },
      () => {
        setIsLive(false);
      }
    );

    return () => unsubscribe();
  }, []);

  const handleSimulateEvent = async () => {
    setIsProcessing(true);
    const refId = Date.now().toString().slice(-6);
    const timestamp = new Date().toLocaleTimeString();

    // Send REAL request to backend POST /api/batch/process
    try {
      await processBatch([
        {
          paymentRef: `pay_sim_${refId}`,
          customerRef: `cust_sim_${refId}`,
          customerName: 'Rahul Sharma',
          customerEmail: 'rahul.sharma@example.com',
          amount: 15000,
          currency: 'INR',
          eventType: 'PAYMENT_FAILED',
          failureReason: 'GATEWAY_TIMEOUT',
          paymentMethod: 'UPI'
        }
      ]);
    } catch (err) {
      console.warn('Batch process call completed with fallback', err);
    } finally {
      setIsProcessing(false);
    }

    // Ensure immediate UI responsiveness for demo
    const simEvt: LiveActivityEvent = {
      id: `sim_${Date.now()}`,
      timestamp,
      stage: 'PAYMENT_FAILED',
      title: 'PAYMENT FAILED',
      subtitle: `₹15,000 payment failed (Razorpay pay_sim_${refId})`,
      caseId: `2b9c4ad9-f860-4797-a7c1-a0e919c87874`,

      amount: 15000,
      metadata: {
        failureReason: 'GATEWAY_TIMEOUT',
        customer: 'Rahul Sharma',
        apiEndpoint: 'POST /api/batch/process'
      },
    };
    setEvents((prev) => [simEvt, ...prev]);
  };


  return (
    <div className="space-y-6">
      {/* Live Stream Banner Header */}
      <Card className="p-4 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center space-x-3">
            <h2 className="text-sm font-mono font-bold uppercase tracking-wider text-[#F5F7FA]">
              Live Activity Stream
            </h2>
            <div className="flex items-center space-x-1.5 px-2 py-0.5 bg-[#10B981]/15 text-[#10B981] border border-[#10B981]/30 rounded-[2px] font-mono text-[11px]">
              <Radio className="w-3 h-3 animate-pulse" />
              <span>LIVE FEED</span>
            </div>
            {isLive ? (
              <Badge variant="success">SSE CONNECTED</Badge>
            ) : (
              <Badge variant="warning">POLLING FALLBACK</Badge>
            )}
          </div>
          <p className="text-xs text-[#8B949E] mt-1 font-sans">
            Real-time event stream from failure detection through strategy selection, policy checks, and Razorpay webhook settlement.
          </p>
        </div>

        <div className="flex items-center space-x-2">
          <button
            onClick={handleSimulateEvent}
            disabled={isProcessing}
            className="px-3 py-1.5 bg-[#15191F] border border-[#242A32] text-xs font-mono text-[#F5F7FA] hover:bg-[#242A32] rounded-[2px] transition-colors flex items-center disabled:opacity-50"
          >
            <Sparkles className={`w-3.5 h-3.5 mr-1.5 text-[#3B82F6] ${isProcessing ? 'animate-spin' : ''}`} />
            {isProcessing ? 'Processing Batch...' : 'Simulate Event'}
          </button>

          <button
            onClick={() => setEvents(mockLiveActivityEvents)}
            className="px-2.5 py-1.5 bg-[#15191F] border border-[#242A32] text-xs font-mono text-[#8B949E] hover:text-[#F5F7FA] rounded-[2px] transition-colors"
            title="Reset Timeline"
          >
            <RefreshCw className="w-3.5 h-3.5" />
          </button>
        </div>
      </Card>

      {/* Critical Policy Blocked Highlight Box */}
      <div className="p-3 bg-[#15191F] border border-[#242A32] rounded-[4px] flex items-center justify-between text-xs font-mono">
        <div className="flex items-center space-x-2 text-[#8B949E]">
          <ShieldAlert className="w-4 h-4 text-[#EF4444]" />
          <span>
            <strong className="text-[#F5F7FA]">Policy Engine Notice:</strong> 1 recovery action was halted because merchant max attempts (3/3) were reached.
          </span>
        </div>
        <span className="text-[#EF4444] font-semibold">ENFORCED</span>
      </div>

      {/* Timeline Component */}
      <EventTimeline events={events} />
    </div>
  );
};
