import React, { useEffect } from 'react';
import { CheckCircle2, AlertCircle, X } from 'lucide-react';

interface ToastProps {
  message: string;
  type?: 'success' | 'error' | 'info';
  onClose: () => void;
  duration?: number;
}

export const Toast: React.FC<ToastProps> = ({
  message,
  type = 'success',
  onClose,
  duration = 3000,
}) => {
  useEffect(() => {
    const timer = setTimeout(() => {
      onClose();
    }, duration);
    return () => clearTimeout(timer);
  }, [duration, onClose]);

  const icons = {
    success: <CheckCircle2 className="w-4 h-4 text-[#10B981] mr-2 flex-shrink-0" />,
    error: <AlertCircle className="w-4 h-4 text-[#EF4444] mr-2 flex-shrink-0" />,
    info: <AlertCircle className="w-4 h-4 text-[#3B82F6] mr-2 flex-shrink-0" />,
  };

  const borders = {
    success: 'border-[#10B981]/40',
    error: 'border-[#EF4444]/40',
    info: 'border-[#3B82F6]/40',
  };

  return (
    <div
      className={`fixed bottom-5 right-5 z-50 flex items-center bg-[#15191F] border ${borders[type]} text-[#F5F7FA] px-3.5 py-2.5 rounded-[4px] shadow-xl text-xs font-mono tracking-tight transition-all duration-200`}
    >
      {icons[type]}
      <span className="mr-3">{message}</span>
      <button
        onClick={onClose}
        className="text-[#8B949E] hover:text-[#F5F7FA] p-0.5 rounded transition-colors"
      >
        <X className="w-3.5 h-3.5" />
      </button>
    </div>
  );
};
