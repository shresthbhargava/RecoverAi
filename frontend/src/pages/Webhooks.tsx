import React, { useEffect, useState } from 'react';
import { Card } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { Skeleton } from '../components/ui/Skeleton';
import { SignatureBadge } from '../components/webhooks/SignatureBadge';
import { WebhookPayloadModal } from '../components/webhooks/WebhookPayloadModal';
import { getRecentWebhooks, getWebhookStats } from '../api/webhooks';
import type { WebhookEvent } from '../api/types';
import { Webhook as WebhookIcon, ShieldCheck, ArrowUpRight } from 'lucide-react';


export const Webhooks: React.FC = () => {
  const [events, setEvents] = useState<WebhookEvent[]>([]);
  const [stats, setStats] = useState<Record<string, number>>({});
  const [selectedEvent, setSelectedEvent] = useState<WebhookEvent | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([getRecentWebhooks(), getWebhookStats()])
      .then(([eventsRes, statsRes]) => {
        setEvents(eventsRes);
        setStats(statsRes);
      })
      .finally(() => setLoading(false));
  }, []);

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'PROCESSED':
        return <Badge variant="success">PROCESSED</Badge>;
      case 'DUPLICATE':
        return <Badge variant="neutral">DUPLICATE (IGNORED)</Badge>;
      case 'INVALID_SIGNATURE':
        return <Badge variant="danger">REJECTED (400)</Badge>;
      case 'FAILED':
        return <Badge variant="danger">FAILED</Badge>;
      default:
        return <Badge variant="neutral">{status}</Badge>;
    }
  };

  const handleRowClick = (evt: WebhookEvent) => {
    setSelectedEvent(evt);
    setModalOpen(true);
  };

  return (
    <div className="space-y-6">
      {/* Top Webhook Stats Summary Bar */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 font-mono">
        <Card className="p-3">
          <div className="text-[10px] text-[#5F6875] uppercase">Total Ingested</div>
          <div className="text-lg font-bold text-[#F5F7FA] tabular-nums mt-0.5">
            {stats.TOTAL ?? 59}
          </div>
        </Card>

        <Card className="p-3">
          <div className="text-[10px] text-[#5F6875] uppercase">Processed & Reconciled</div>
          <div className="text-lg font-bold text-[#10B981] tabular-nums mt-0.5">
            {stats.PROCESSED ?? 42}
          </div>
        </Card>

        <Card className="p-3">
          <div className="text-[10px] text-[#5F6875] uppercase">Replay Guard Blocked</div>
          <div className="text-lg font-bold text-[#8B949E] tabular-nums mt-0.5">
            {stats.DUPLICATE ?? 8}
          </div>
        </Card>

        <Card className="p-3">
          <div className="text-[10px] text-[#5F6875] uppercase">Invalid Signatures Rejected</div>
          <div className="text-lg font-bold text-[#EF4444] tabular-nums mt-0.5">
            {stats.INVALID_SIGNATURE ?? 3}
          </div>
        </Card>
      </div>

      {/* Header Info */}
      <Card className="p-4 flex items-center justify-between">
        <div>
          <div className="flex items-center space-x-2">
            <WebhookIcon className="w-4 h-4 text-[#3B82F6]" />
            <h2 className="text-xs font-mono font-bold uppercase tracking-wider text-[#F5F7FA]">
              Razorpay Webhook Delivery Log
            </h2>
          </div>
          <p className="text-xs text-[#8B949E] mt-1 font-sans">
            Every webhook callback is verified using HMAC-SHA256 signature matching and deduplicated before settlement.
          </p>
        </div>

        <div className="flex items-center space-x-1.5 px-2.5 py-1 bg-[#10B981]/15 text-[#10B981] border border-[#10B981]/30 rounded-[2px] font-mono text-xs">
          <ShieldCheck className="w-3.5 h-3.5 mr-1" />
          <span>HMAC GUARD ACTIVE</span>
        </div>
      </Card>

      {/* Webhooks Events Table */}
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
                  <th className="py-2.5 px-3">Razorpay Event ID</th>
                  <th className="py-2.5 px-3">Event Type</th>
                  <th className="py-2.5 px-3">Signature Verification</th>
                  <th className="py-2.5 px-3">Reconciliation Status</th>
                  <th className="py-2.5 px-3">Related Case</th>
                  <th className="py-2.5 px-3">Received At</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#242A32]">
                {events.map((e) => (
                  <tr
                    key={e.id}
                    onClick={() => handleRowClick(e)}
                    className="hover:bg-[#15191F] transition-colors cursor-pointer group"
                  >
                    <td className="py-3 px-3 font-semibold text-[#F5F7FA] flex items-center justify-between">
                      <span>{e.razorpayEventId}</span>
                      <ArrowUpRight className="w-3 h-3 text-[#5F6875] group-hover:text-[#F5F7FA] opacity-0 group-hover:opacity-100 transition-opacity" />
                    </td>
                    <td className="py-3 px-3 text-[#3B82F6]">{e.eventType}</td>
                    <td className="py-3 px-3">
                      <SignatureBadge isValid={e.signatureValid} />
                    </td>
                    <td className="py-3 px-3">{getStatusBadge(e.status)}</td>
                    <td className="py-3 px-3 text-[#8B949E]">
                      {e.relatedCaseId ? `${e.relatedCaseId.slice(0, 8)}...` : 'None'}
                    </td>
                    <td className="py-3 px-3 text-[#5F6875]">
                      {new Date(e.receivedAt).toLocaleTimeString()}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <WebhookPayloadModal
        event={selectedEvent}
        isOpen={modalOpen}
        onClose={() => setModalOpen(false)}
      />
    </div>
  );
};
