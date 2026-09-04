import React from 'react';

interface CardProps extends React.HTMLAttributes<HTMLDivElement> {
  children: React.ReactNode;
  variant?: 'primary' | 'secondary';
  hoverable?: boolean;
  className?: string;
}

export const Card: React.FC<CardProps> = ({
  children,
  variant = 'primary',
  hoverable = false,
  className = '',
  ...props
}) => {
  const bgClass = variant === 'primary' ? 'bg-[#111419]' : 'bg-[#15191F]';
  const hoverClass = hoverable
    ? 'hover:-translate-y-[1px] hover:border-[#3B4452] transition-all duration-200 cursor-pointer'
    : '';

  return (
    <div
      className={`${bgClass} border border-[#242A32] rounded-[4px] p-4 text-[#F5F7FA] ${hoverClass} ${className}`}
      {...props}
    >
      {children}
    </div>
  );
};
