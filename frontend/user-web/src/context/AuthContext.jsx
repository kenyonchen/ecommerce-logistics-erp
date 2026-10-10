import React, { createContext, useState, useContext, useEffect } from 'react';
import { authApi } from '../services/api.js';

// 创建认证上下文
const AuthContext = createContext();

// localStorage 键
const KEYS = {
  token: 'token',
  refreshToken: 'refreshToken',
  username: 'username',
  tenantId: 'tenantId',
  roles: 'roles',
  permissions: 'permissions',
};

// 认证上下文提供者
export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [loading, setLoading] = useState(true);
  // 权限点明细只有 /auth/me 会返回，登录响应里没有。未加载完成前界面退回按角色控制，
  // 否则登录成功的那一瞬 permissions 还是空数组，整个菜单会被判成"无权限"而全部消失。
  const [permissionsLoaded, setPermissionsLoaded] = useState(false);

  // 清空本地存储会话
  const clearSession = () => {
    Object.values(KEYS).forEach(key => localStorage.removeItem(key));
    setPermissionsLoaded(false);
  };

  // 从 /auth/me 同步当前用户资料（用于刷新页面后恢复会话）
  const refreshProfile = async () => {
    try {
      const me = await authApi.getMe();
      applyProfile(me);
      return me;
    } catch {
      // 会话失效（token 过期/非法）
      clearSession();
      setUser(null);
      setIsAuthenticated(false);
      return null;
    }
  };

  // 将 /auth/me 或登录响应中的用户信息写入状态与本地存储
  const applyProfile = (me) => {
    if (!me) return;
    const roles = me.roles ? me.roles.map(r => r.name) : [];
    const permissions = me.permissions || [];
    localStorage.setItem(KEYS.username, me.username || '');
    if (me.tenantId) localStorage.setItem(KEYS.tenantId, me.tenantId);
    localStorage.setItem(KEYS.roles, JSON.stringify(roles));
    localStorage.setItem(KEYS.permissions, JSON.stringify(permissions));
    setUser({ ...me, roles, permissions });
    setIsAuthenticated(true);
    setPermissionsLoaded(true);
  };

  // 初始化：有 token 时拉取当前用户信息校验会话
  useEffect(() => {
    const init = async () => {
      const token = localStorage.getItem(KEYS.token);
      if (token) {
        await refreshProfile();
      }
      setLoading(false);
    };
    init();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 登录：responseData 为拦截器解包后的 TokenResponse
  // { token, refreshToken, username, tenantId, roles }
  const login = (responseData) => {
    const token = responseData?.token;
    if (!token) {
      throw new Error('登录响应缺少令牌');
    }
    localStorage.setItem(KEYS.token, token);
    if (responseData.refreshToken) {
      localStorage.setItem(KEYS.refreshToken, responseData.refreshToken);
    }
    localStorage.setItem(KEYS.username, responseData.username || '');
    if (responseData.tenantId) {
      localStorage.setItem(KEYS.tenantId, responseData.tenantId);
    }
    localStorage.setItem(KEYS.roles, JSON.stringify(responseData.roles || []));
    setPermissionsLoaded(false);
    setUser({
      username: responseData.username,
      tenantId: responseData.tenantId,
      roles: responseData.roles || [],
      permissions: [],
    });
    setIsAuthenticated(true);
    // 异步补全用户资料（权限明细等），失败不影响登录
    refreshProfile();
    return true;
  };

  // 登出：调用后端并清理本地会话
  const logout = async () => {
    try {
      await authApi.logout();
    } catch {
      // 后端登出失败不阻塞本地登出（JWT 无状态）
    }
    clearSession();
    setUser(null);
    setIsAuthenticated(false);
  };

  // 检查是否认证
  const checkAuth = () => {
    return !!localStorage.getItem(KEYS.token);
  };

  // 是否拥有任一角色
  const hasRole = (roleNames) => {
    if (!user || !user.roles) return false;
    const required = Array.isArray(roleNames) ? roleNames : [roleNames];
    return required.some(role => user.roles.includes(role));
  };

  // 是否拥有任一权限
  const hasPermission = (permissionNames) => {
    if (!user || !user.permissions) return false;
    const required = Array.isArray(permissionNames) ? permissionNames : [permissionNames];
    return required.some(permission => user.permissions.includes(permission));
  };

  // 界面级权限判定（菜单/按钮/路由入口）。两个约定：
  // 1. 权限点明细没到位时放行，交由角色控制兜底，避免登录成功那一瞬入口全消失；
  // 2. 这里只决定「展示什么入口」，拦截靠后端 @PreAuthorize —— 藏起按钮不是安全边界。
  const can = (permission) => {
    if (!permission || !permissionsLoaded) return true;
    return hasPermission(permission);
  };

  const value = {
    user,
    isAuthenticated,
    loading,
    permissionsLoaded,
    login,
    logout,
    checkAuth,
    hasRole,
    hasPermission,
    can,
    refreshProfile,
  };

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
};

// Provider 与钩子同文件是 Context 惯例，豁免 react-refresh 限制
// eslint-disable-next-line react-refresh/only-export-components
export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

// 导出认证上下文
export default AuthContext;
