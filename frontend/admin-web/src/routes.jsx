import React from 'react';
import { createBrowserRouter, Navigate } from 'react-router-dom';
import App from './App';
import PlatformDashboard from './pages/platform/PlatformDashboard';
import SystemConfig from './pages/platform/SystemConfig';
import TenantList from './pages/tenant/TenantList';
import UserList from './pages/user/UserList';
import Login from './pages/Login';
import { useAuth } from './context/AuthContext';
import { Result, Button } from 'antd';

// 登录守卫：未登录跳转登录页，非平台管理员显示无权限
const AdminGuard = ({ children }) => {
  const { isAuthenticated, loading, hasRole } = useAuth();
  if (loading) {
    return null;
  }
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  if (!hasRole('PLATFORM_ADMIN')) {
    return (
      <Result
        status="403"
        title="无权限访问"
        subTitle="平台管理端仅允许平台管理员（PLATFORM_ADMIN）访问"
        extra={<Button type="primary" href="/login">重新登录</Button>}
      />
    );
  }
  return children;
};

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
