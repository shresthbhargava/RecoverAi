import React from 'react';

type Variant = 'success' | 'danger' | 'warning' | 'info' | 'neutral';

interface BadgeProps {
  children: React.ReactNode;
  variant?: Variant;
  size?: 'sm' | 'md';
  className?: string;
}

export const Badge: React.FC<BadgeProps> = ({
  children,
  variant = 'neutral',
  size = 'md',
  className = '',
}) => {
  const baseStyles =
    'inline-flex items-center font-medium tracking-wide uppercase font-mono rounded-[2px] transition-colors';

  const sizeStyles = {
    sm: 'px-1.5 py-0.5 text-[10px]',
    md: 'px-2 py-0.5 text-xs',
  };

  const variantStyles = {
    success: 'bg-[#10B981]/15 text-[#10B981] border border-[#10B981]/30',
    danger: 'bg-[#EF4444]/15 text-[#EF4444] border border-[#EF4444]/30',
    warning: 'bg-[#F59E0B]/15 text-[#F59E0B] border border-[#F59E0B]/30',
    info: 'bg-[#3B82F6]/15 text-[#3B82F6] border border-[#3B82F6]/30',
    neutral: 'bg-[#242A32] text-[#8B949E] border border-[#3B4452]',
  };

  return (
    <span className={`${baseStyles} ${sizeStyles[size]} ${variantStyles[variant]} ${className}`}>
      {children}
    </span>
  );
};
