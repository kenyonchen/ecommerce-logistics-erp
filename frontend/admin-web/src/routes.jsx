import React from 'react';
import { createBrowserRouter } from 'react-router-dom';
import App from './App';
import PlatformDashboard from './pages/platform/PlatformDashboard';
import SystemConfig from './pages/platform/SystemConfig';
import TenantList from './pages/tenant/TenantList';
import UserList from './pages/user/UserList';

const router = createBrowserRouter([
  {
    path: '/',
    element: <App />,
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