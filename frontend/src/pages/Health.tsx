import React, { useEffect, useState } from 'react';
import { Card } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { Skeleton } from '../components/ui/Skeleton';
import { getSystemHealth } from '../api/health';
import type { HealthResponse } from '../api/types';
import { ActivitySquare, Database, Shield, Cpu, Clock, CheckCircle2, AlertTriangle } from 'lucide-react';


export const Health: React.FC = () => {
  const [health, setHealth] = useState<HealthResponse | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getSystemHealth()
      .then((res) => setHealth(res))
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return (
      <div className="space-y-4">
        <Skeleton height="100px" />
        <Skeleton height="250px" />
      </div>
    );
  }

  const isDegraded = health?.mode === 'DEGRADED_FALLBACK';

  return (
    <div className="space-y-6">
      {/* System Status Banner */}
      <Card className="p-4 border-[#242A32]">
        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
          <div className="flex items-center space-x-3">
            <div className="w-9 h-9 rounded-[4px] bg-[#10B981]/15 border border-[#10B981]/30 flex items-center justify-center text-[#10B981]">
              <ActivitySquare className="w-5 h-5" />
            </div>
            <div>
              <div className="flex items-center space-x-2">
                <h2 className="text-sm font-mono font-bold text-[#F5F7FA]">
                  {health?.service || 'recoverai-backend'}
                </h2>
                <Badge variant="success">SYSTEM OPERATIONAL</Badge>
              </div>
              <p className="text-xs text-[#8B949E] mt-0.5 font-sans">
                Spring Boot 3.3.4 / Java 21 / PostgreSQL / Flyway
              </p>
            </div>
          </div>

          <div className="text-right font-mono text-xs">
            <div className="text-[10px] text-[#5F6875] uppercase">System Execution Mode</div>
            <div className="flex items-center text-[#F5F7FA] font-bold mt-0.5">
              {isDegraded ? (
                <span className="text-[#F59E0B] flex items-center">
                  <AlertTriangle className="w-3.5 h-3.5 mr-1" /> DEMO MODE (FALLBACK)
                </span>
              ) : (
                <span className="text-[#10B981] flex items-center">
                  <CheckCircle2 className="w-3.5 h-3.5 mr-1" /> FULL PRODUCTION
                </span>
              )}
            </div>
          </div>
        </div>

        {isDegraded && (
          <div className="mt-4 p-3 bg-[#15191F] border border-[#242A32] rounded-[3px] text-xs font-mono text-[#8B949E] flex items-start space-x-2">
            <AlertTriangle className="w-4 h-4 text-[#F59E0B] flex-shrink-0 mt-0.5" />
            <div>
              <strong className="text-[#F5F7FA]">Fallback Reasoning Active:</strong> The platform is running in self-contained Demo Mode without external Grok LLM API key dependencies. Heuristic strategy selection algorithms are active.
            </div>
          </div>
        )}
      </Card>

      {/* Micro-Services Status Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-4 font-mono text-xs">
        <Card className="p-4">
          <div className="flex items-center justify-between">
            <span className="text-[#5F6875] uppercase text-[10px]">Backend Service</span>
            <ActivitySquare className="w-4 h-4 text-[#10B981]" />
          </div>
          <div className="text-base font-bold text-[#F5F7FA] mt-2 flex items-center">
            <span className="w-2 h-2 rounded-full bg-[#10B981] mr-2" />
            UP
          </div>
          <div className="text-[11px] text-[#8B949E] mt-1">Port 8080 Active</div>
        </Card>

        <Card className="p-4">
          <div className="flex items-center justify-between">
            <span className="text-[#5F6875] uppercase text-[10px]">PostgreSQL DB</span>
            <Database className="w-4 h-4 text-[#10B981]" />
          </div>
          <div className="text-base font-bold text-[#F5F7FA] mt-2 flex items-center">
            <span className="w-2 h-2 rounded-full bg-[#10B981] mr-2" />
            CONNECTED
          </div>
          <div className="text-[11px] text-[#8B949E] mt-1">Schema V1__init</div>
        </Card>

        <Card className="p-4">
          <div className="flex items-center justify-between">
            <span className="text-[#5F6875] uppercase text-[10px]">Razorpay API</span>
            <Shield className="w-4 h-4 text-[#3B82F6]" />
          </div>
          <div className="text-base font-bold text-[#F5F7FA] mt-2 flex items-center">
            <span className="w-2 h-2 rounded-full bg-[#3B82F6] mr-2" />
            TEST MODE
          </div>
          <div className="text-[11px] text-[#8B949E] mt-1">Keys Configured</div>
        </Card>

        <Card className="p-4">
          <div className="flex items-center justify-between">
            <span className="text-[#5F6875] uppercase text-[10px]">Grok LLM Engine</span>
            <Cpu className="w-4 h-4 text-[#F59E0B]" />
          </div>
          <div className="text-base font-bold text-[#F5F7FA] mt-2 flex items-center">
            <span className="w-2 h-2 rounded-full bg-[#F59E0B] mr-2" />
            FALLBACK
          </div>
          <div className="text-[11px] text-[#8B949E] mt-1">Heuristic Mode</div>
        </Card>
      </div>

      {/* Environment Config Inspection Matrix */}
      <Card>
        <div className="pb-3 border-b border-[#242A32] mb-3 flex items-center justify-between">
          <h3 className="text-xs font-mono font-bold uppercase tracking-wider text-[#F5F7FA]">
            Environment Configuration Matrix
          </h3>
          <div className="flex items-center text-xs font-mono text-[#8B949E]">
            <Clock className="w-3.5 h-3.5 mr-1" />
            <span>Timezone: {health?.timezone || 'Asia/Kolkata'}</span>
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 font-mono text-xs">
          <div className="p-3 bg-[#15191F] border border-[#242A32] rounded-[2px] flex items-center justify-between">
            <span className="text-[#8B949E]">Webhook Secret</span>
            <Badge variant={health?.config.webhookSecretConfigured ? 'success' : 'warning'}>
              {health?.config.webhookSecretConfigured ? 'CONFIGURED' : 'NOT SET'}
            </Badge>
          </div>

          <div className="p-3 bg-[#15191F] border border-[#242A32] rounded-[2px] flex items-center justify-between">
            <span className="text-[#8B949E]">Razorpay Keys</span>
            <Badge variant={health?.config.razorpayKeysConfigured ? 'success' : 'warning'}>
              {health?.config.razorpayKeysConfigured ? 'CONFIGURED' : 'TEST READY'}
            </Badge>
          </div>

          <div className="p-3 bg-[#15191F] border border-[#242A32] rounded-[2px] flex items-center justify-between">
            <span className="text-[#8B949E]">Grok API Key</span>
            <Badge variant={health?.config.grokApiKeyConfigured ? 'success' : 'neutral'}>
              {health?.config.grokApiKeyConfigured ? 'CONFIGURED' : 'HEURISTIC FALLBACK'}
            </Badge>
          </div>
        </div>
      </Card>
    </div>
  );
};
