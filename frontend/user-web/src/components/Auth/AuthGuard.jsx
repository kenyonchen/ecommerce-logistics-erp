import React from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext.jsx';
import { Spin, Card, Result, Button } from 'antd';

/**
 * 认证守卫组件：未登录跳转登录页；指定角色/权限点时不匹配则显示无权限。
 *
 * 作为布局路由的 element 用时不传 children（渲染 <Outlet/>）；
 * 想在单条路由上就地校验也可以直接包页面：<AuthGuard requiredPermissions={['order:write']}><CreateOrder/></AuthGuard>
 */
const AuthGuard = ({ requiredRoles = [], requiredPermissions = [], children }) => {
  const { isAuthenticated, loading, hasRole, hasPermission, permissionsLoaded } = useAuth();
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
          <Spin size="large" />
          <div style={{ marginTop: 12 }}>加载中...</div>
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

  // 权限点检查：与菜单入口同一份目录。权限明细尚未到位时先放行，
  // 否则会在全新登录跳转的瞬间闪一次 403（角色检查此时已经生效）。
  if (requiredPermissions.length > 0 && permissionsLoaded && !hasPermission(requiredPermissions)) {
    return (
      <Result
        status="403"
        title="无权限访问"
        subTitle={`该页面需要以下权限点之一：${requiredPermissions.join(' / ')}`}
        extra={<Button type="primary" href="/">返回首页</Button>}
      />
    );
  }

  return children ?? <Outlet />;
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

/**
 * 权限点守卫：路由级二次校验（菜单已按同一份目录收过入口，这里兜住直接敲 URL 的情况）
 */
const PermissionGuard = ({ permission, children }) => {
  return <AuthGuard requiredPermissions={[permission]}>{children}</AuthGuard>;
};

export { AuthGuard, PlatformAdminGuard, TenantAdminGuard, PermissionGuard };
