import React from 'react';

interface DataPoint {
  day: string;
  atRisk: number;
  recovered: number;
}

const mockChartData: DataPoint[] = [
  { day: 'Mon', atRisk: 12000, recovered: 4500 },
  { day: 'Tue', atRisk: 18500, recovered: 8200 },
  { day: 'Wed', atRisk: 24000, recovered: 11000 },
  { day: 'Thu', atRisk: 29500, recovered: 13500 },
  { day: 'Fri', atRisk: 34825, recovered: 15000 },
];

export const PerformanceChart: React.FC = () => {
  const maxVal = 40000;
  const height = 140;


  return (
    <div className="w-full flex flex-col justify-between h-full">
      <div className="flex items-center justify-between text-xs font-mono mb-2">
        <div className="flex items-center space-x-4">
          <div className="flex items-center text-[#8B949E]">
            <span className="w-2.5 h-2.5 bg-[#EF4444] rounded-[1px] mr-1.5 inline-block" />
            Revenue at Risk
          </div>
          <div className="flex items-center text-[#8B949E]">
            <span className="w-2.5 h-2.5 bg-[#10B981] rounded-[1px] mr-1.5 inline-block" />
            Recovered
          </div>
        </div>
        <div className="text-[11px] text-[#5F6875]">Last 5 Days</div>
      </div>

      <div className="relative w-full flex-1 flex items-end justify-between pt-4 border-b border-[#242A32]">
        {mockChartData.map((d) => {
          const atRiskH = (d.atRisk / maxVal) * height;
          const recoveredH = (d.recovered / maxVal) * height;

          return (
            <div key={d.day} className="flex-1 flex flex-col items-center group">
              <div className="flex items-end space-x-1.5 h-[140px] w-full justify-center">
                {/* At Risk Bar */}
                <div
                  style={{ height: `${atRiskH}px` }}
                  className="w-3 bg-[#EF4444]/40 border-t border-[#EF4444] rounded-[1px] transition-all group-hover:bg-[#EF4444]/60"
                  title={`At Risk: ₹${d.atRisk.toLocaleString()}`}
                />
                {/* Recovered Bar */}
                <div
                  style={{ height: `${recoveredH}px` }}
                  className="w-3 bg-[#10B981] rounded-[1px] transition-all group-hover:bg-[#10B981]/90"
                  title={`Recovered: ₹${d.recovered.toLocaleString()}`}
                />
              </div>
              <div className="text-[10px] font-mono text-[#8B949E] mt-2 group-hover:text-[#F5F7FA]">
                {d.day}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
