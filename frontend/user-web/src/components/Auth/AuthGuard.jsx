import React from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext.jsx';
import { Spin, Card, Result, Button } from 'antd';

/**
 * 认证守卫组件：未登录跳转登录页；指定角色时不匹配则显示无权限
 */
const AuthGuard = ({ requiredRoles = [] }) => {
  const { isAuthenticated, loading, user, hasRole } = useAuth();
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

  // 角色检查：requiredRoles 非空时要求用户拥有其中任一角色
  if (requiredRoles.length > 0 && !hasRole(requiredRoles)) {
    return (
      <Result
        status="403"
        title="无权限访问"
        subTitle={`该页面需要以下角色之一：${requiredRoles.join(' / ')}`}
        extra={<Button type="primary" href="/">返回首页</Button>}
      />
    );
  }

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
  return <AuthGuard requiredRoles={['TENANT_ADMIN', 'PLATFORM_ADMIN']} />;
};

export { AuthGuard, PlatformAdminGuard, TenantAdminGuard };
