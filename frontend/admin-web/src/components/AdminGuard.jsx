import { Navigate } from 'react-router-dom';
import { Result, Button } from 'antd';
import { useAuth } from '../context/AuthContext';

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

export default AdminGuard;
