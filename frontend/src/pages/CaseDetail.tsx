import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { CaseDetailView } from '../components/cases/CaseDetailView';
import { Skeleton } from '../components/ui/Skeleton';
import { getRecoveryCaseDetail } from '../api/cases';
import type { RecoveryCaseDetail } from '../api/types';
import { ArrowLeft } from 'lucide-react';


export const CaseDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [caseData, setCaseData] = useState<RecoveryCaseDetail | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchDetail = () => {
    if (!id) return;
    setLoading(true);
    getRecoveryCaseDetail(id)
      .then((res) => setCaseData(res))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    fetchDetail();
  }, [id]);

  if (loading) {
    return (
      <div className="space-y-4">
        <Skeleton height="150px" />
        <Skeleton height="200px" />
        <Skeleton height="150px" />
      </div>
    );
  }

  if (!caseData) {
    return (
      <div className="p-8 text-center text-[#8B949E] font-mono">
        <p>Recovery case not found.</p>
        <button
          onClick={() => navigate('/cases')}
          className="mt-3 px-3 py-1.5 bg-[#15191F] border border-[#242A32] text-xs text-[#F5F7FA] rounded"
        >
          Return to Cases
        </button>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <button
        onClick={() => navigate('/cases')}
        className="flex items-center text-xs font-mono text-[#8B949E] hover:text-[#F5F7FA] transition-colors"
      >
        <ArrowLeft className="w-3.5 h-3.5 mr-1" /> Back to Recovery Cases
      </button>

      <CaseDetailView caseData={caseData} onRefresh={fetchDetail} />
    </div>
  );
};
