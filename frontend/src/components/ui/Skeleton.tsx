import React from 'react';

interface SkeletonProps {
  className?: string;
  width?: string;
  height?: string;
}

export const Skeleton: React.FC<SkeletonProps> = ({ className = '', width, height }) => {
  return (
    <div
      style={{ width, height }}
      className={`animate-pulse bg-[#15191F] border border-[#242A32] rounded-[2px] ${className}`}
    />
  );
};
