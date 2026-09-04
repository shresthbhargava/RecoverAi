import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AppLayout } from './components/layout/AppLayout';
import { Dashboard } from './pages/Dashboard';
import { Cases } from './pages/Cases';
import { CaseDetail } from './pages/CaseDetail';
import { Activity } from './pages/Activity';
import { Policies } from './pages/Policies';
import { Decisions } from './pages/Decisions';
import { Webhooks } from './pages/Webhooks';
import { Health } from './pages/Health';

export const App: React.FC = () => {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<AppLayout />}>
          <Route index element={<Navigate to="/dashboard" replace />} />
          <Route path="dashboard" element={<Dashboard />} />
          <Route path="cases" element={<Cases />} />
          <Route path="cases/:id" element={<CaseDetail />} />
          <Route path="activity" element={<Activity />} />
          <Route path="policies" element={<Policies />} />
          <Route path="decisions" element={<Decisions />} />
          <Route path="webhooks" element={<Webhooks />} />
          <Route path="health" element={<Health />} />
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
};

export default App;
