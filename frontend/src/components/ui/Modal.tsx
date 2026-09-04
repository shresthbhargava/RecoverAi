import React, { useEffect } from 'react';
import { X } from 'lucide-react';

interface ModalProps {
  isOpen: boolean;
  onClose: () => void;
  title: string;
  subtitle?: string;
  children: React.ReactNode;
}

export const Modal: React.FC<ModalProps> = ({
  isOpen,
  onClose,
  title,
  subtitle,
  children,
}) => {
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    if (isOpen) {
      document.body.style.overflow = 'hidden';
      window.addEventListener('keydown', handleKeyDown);
    }
    return () => {
      document.body.style.overflow = 'unset';
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-[1px]">
      <div className="bg-[#111419] border border-[#242A32] rounded-[4px] w-full max-w-lg overflow-hidden shadow-2xl animate-in fade-in zoom-in-95 duration-150">
        <div className="flex items-center justify-between px-4 py-3 border-b border-[#242A32] bg-[#15191F]">
          <div>
            <h3 className="text-sm font-semibold text-[#F5F7FA] font-sans tracking-tight">
              {title}
            </h3>
            {subtitle && (
              <p className="text-[11px] text-[#8B949E] mt-0.5">{subtitle}</p>
            )}
          </div>
          <button
            onClick={onClose}
            className="text-[#8B949E] hover:text-[#F5F7FA] p-1 rounded hover:bg-[#242A32] transition-colors"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
        <div className="p-4 text-xs font-sans text-[#F5F7FA] max-h-[80vh] overflow-y-auto">
          {children}
        </div>
      </div>
    </div>
  );
};
