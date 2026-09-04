import React from 'react';
import { Modal } from '../ui/Modal';
import type { WebhookEvent } from '../../api/types';
import { SignatureBadge } from './SignatureBadge';


interface WebhookPayloadModalProps {
  event: WebhookEvent | null;
  isOpen: boolean;
  onClose: () => void;
}

export const WebhookPayloadModal: React.FC<WebhookPayloadModalProps> = ({
  event,
  isOpen,
  onClose,
}) => {
  if (!event) return null;

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={`Webhook Event: ${event.eventType}`}
      subtitle={`Event ID: ${event.razorpayEventId}`}
    >
      <div className="space-y-3 font-mono text-xs">
        <div className="flex items-center justify-between p-2.5 bg-[#15191F] border border-[#242A32] rounded-[2px]">
          <span className="text-[#8B949E]">HMAC Signature</span>
          <SignatureBadge isValid={event.signatureValid} />
        </div>

        <div className="grid grid-cols-2 gap-2">
          <div className="p-2 bg-[#15191F] border border-[#242A32] rounded-[2px]">
            <div className="text-[10px] text-[#5F6875] uppercase">Status</div>
            <div className="text-[#F5F7FA] font-bold mt-0.5">{event.status}</div>
          </div>
          <div className="p-2 bg-[#15191F] border border-[#242A32] rounded-[2px]">
            <div className="text-[10px] text-[#5F6875] uppercase">External Ref</div>
            <div className="text-[#8B949E] mt-0.5 truncate">{event.externalRef || 'N/A'}</div>
          </div>
        </div>

        <div>
          <div className="text-[10px] text-[#5F6875] uppercase mb-1">Reconciliation Note</div>
          <div className="p-2.5 bg-[#15191F] border border-[#242A32] text-[#F5F7FA] rounded-[2px]">
            {event.note}
          </div>
        </div>

        <div>
          <div className="text-[10px] text-[#5F6875] uppercase mb-1">Audit Metadata</div>
          <pre className="p-3 bg-[#0B0D10] border border-[#242A32] text-[#10B981] text-[11px] rounded-[2px] overflow-x-auto">
            {JSON.stringify(
              {
                id: event.id,
                razorpayEventId: event.razorpayEventId,
                eventType: event.eventType,
                signatureValid: event.signatureValid,
                status: event.status,
                relatedCaseId: event.relatedCaseId,
                externalRef: event.externalRef,
                receivedAt: event.receivedAt,
                processedAt: event.processedAt,
              },
              null,
              2
            )}
          </pre>
        </div>
      </div>
    </Modal>
  );
};
