import React from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext.jsx';
import { Spin, Card } from 'antd';

/**
 * 认证守卫组件
 */
const AuthGuard = ({ requiredRoles = [] }) => {
  const { isAuthenticated, loading, user } = useAuth();
  const location = useLocation();

  if (loading) {
    return (
      <div style={{ 
        minHeight: '100vh', 
        display: 'flex', 
        alignItems: 'center', 
        justifyContent: 'center',
        backgroundColor: '#f0f2f5'
      }}>
        <Card style={{ width: 300, textAlign: 'center', padding: '40px' }}>
          <Spin size="large" tip="加载中..." />
        </Card>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  // 这里可以添加角色检查逻辑
  // if (requiredRoles.length > 0 && !requiredRoles.includes(user.role)) {
  //   return <Navigate to="/unauthorized" replace />;
  // }

  return <Outlet />;
};

/**
 * 平台管理员守卫
 */
const PlatformAdminGuard = () => {
  return <AuthGuard requiredRoles={['PLATFORM_ADMIN']} />;
};

/**
 * 租户管理员守卫
 */
const TenantAdminGuard = () => {
  return <AuthGuard requiredRoles={['TENANT_ADMIN']} />;
};

export { AuthGuard, PlatformAdminGuard, TenantAdminGuard };