import React, { createContext, useState, useContext, useEffect } from 'react';
import { authApi } from '../services/api.js';

// 创建认证上下文
const AuthContext = createContext();

// 认证上下文提供者（平台管理端：要求 PLATFORM_ADMIN 角色）
export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [loading, setLoading] = useState(true);

  const clearSession = () => {
    ['token', 'refreshToken', 'username', 'tenantId', 'roles', 'permissions'].forEach(
      key => localStorage.removeItem(key)
    );
  };

  const applyProfile = (me) => {
    if (!me) return false;
    const roles = me.roles ? me.roles.map(r => r.name) : [];
    localStorage.setItem('username', me.username || '');
    if (me.tenantId) localStorage.setItem('tenantId', me.tenantId);
    localStorage.setItem('roles', JSON.stringify(roles));
    localStorage.setItem('permissions', JSON.stringify(me.permissions || []));
    setUser({ ...me, roles, permissions: me.permissions || [] });
    setIsAuthenticated(true);
    return true;
  };

  useEffect(() => {
    const init = async () => {
      const token = localStorage.getItem('token');
      if (token) {
        try {
          const me = await authApi.getMe();
          if (!applyProfile(me)) {
            clearSession();
          }
        } catch (error) {
          clearSession();
        }
      }
      setLoading(false);
    };
    init();
  }, []);

  const login = (responseData) => {
    const token = responseData?.token;
    if (!token) {
      throw new Error('登录响应缺少令牌');
    }
    localStorage.setItem('token', token);
    if (responseData.refreshToken) {
      localStorage.setItem('refreshToken', responseData.refreshToken);
    }
    localStorage.setItem('username', responseData.username || '');
    localStorage.setItem('roles', JSON.stringify(responseData.roles || []));
    setUser({
      username: responseData.username,
      tenantId: responseData.tenantId,
      roles: responseData.roles || [],
      permissions: [],
    });
    setIsAuthenticated(true);
    // 异步补全用户资料
    authApi.getMe().then(applyProfile).catch(() => {});
    return true;
  };

  const logout = async () => {
    try {
      await authApi.logout();
    } catch (error) {
      // 后端登出失败不阻塞本地登出
    }
    clearSession();
    setUser(null);
    setIsAuthenticated(false);
  };

  const hasRole = (roleNames) => {
    if (!user || !user.roles) return false;
    const required = Array.isArray(roleNames) ? roleNames : [roleNames];
    return required.some(role => user.roles.includes(role));
  };

  const value = { user, isAuthenticated, loading, login, logout, hasRole };

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

export default AuthContext;
