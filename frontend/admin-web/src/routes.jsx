import React from 'react';
import { createBrowserRouter } from 'react-router-dom';
import App from './App';
import PlatformDashboard from './pages/platform/PlatformDashboard';
import SystemConfig from './pages/platform/SystemConfig';
import AuditLogList from './pages/platform/AuditLogList';
import TenantList from './pages/tenant/TenantList';
import UserList from './pages/user/UserList';
import Login from './pages/Login';
import AdminGuard from './components/AdminGuard';

const router = createBrowserRouter([
  {
    path: '/login',
    element: <Login />
  },
  {
    path: '/',
    element: (
      <AdminGuard>
        <App />
      </AdminGuard>
    ),
    children: [
      {
        path: '/',
        element: <PlatformDashboard />
      },
      {
        path: '/platform/dashboard',
        element: <PlatformDashboard />
      },
      {
        path: '/platform/config',
        element: <SystemConfig />
      },
      {
        path: '/platform/audit-logs',
        element: <AuditLogList />
      },
      {
        path: '/tenants',
        element: <TenantList />
      },
      {
        path: '/users',
        element: <UserList />
      }
    ]
  }
]);

export default router;
