import React, { createContext, useState, useContext, useEffect } from 'react';

// 创建认证上下文
const AuthContext = createContext();

// 认证上下文提供者
export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [loading, setLoading] = useState(true);

  // 初始化时检查本地存储中的token
  useEffect(() => {
    const token = localStorage.getItem('token');
    const username = localStorage.getItem('username');
    
    if (token && username) {
      setUser({ username });
      setIsAuthenticated(true);
    }
    
    setLoading(false);
  }, []);

  // 登录
  const login = (userData) => {
    localStorage.setItem('token', userData.token);
    localStorage.setItem('username', userData.username || userData.user?.username);
    setUser({ username: userData.username || userData.user?.username });
    setIsAuthenticated(true);
  };

  // 登出
  const logout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('username');
    setUser(null);
    setIsAuthenticated(false);
  };

  // 检查是否认证
  const checkAuth = () => {
    const token = localStorage.getItem('token');
    return !!token;
  };

  const value = {
    user,
    isAuthenticated,
    loading,
    login,
    logout,
    checkAuth
  };

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
};

// 使用认证上下文的钩子
export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

// 导出认证上下文
export default AuthContext;