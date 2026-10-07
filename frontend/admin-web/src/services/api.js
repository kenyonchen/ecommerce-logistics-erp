import axios from 'axios';
import { message } from 'antd';

// 创建axios实例
const api = axios.create({
  baseURL: 'http://localhost:8082/api',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json'
  }
});

// 请求拦截器：注入Bearer令牌
api.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  error => Promise.reject(error)
);

// 错误码映射
const ERROR_MESSAGES = {
  'ENTITY_NOT_FOUND': '请求的资源不存在',
  'INVALID_OPERATION': '无效的操作',
  'VALIDATION_ERROR': '参数验证失败',
  'SYSTEM_ERROR': '系统内部错误，请稍后重试',
  'ERROR': '操作失败'
};

// 响应拦截器：解包 Result 信封（{ code: "200", message, data }）
api.interceptors.response.use(
  response => {
    if (response.data.code === '200') {
      return response.data.data;
    }
    const errorMessage = ERROR_MESSAGES[response.data.code] || response.data.message || '请求失败';
    message.error(errorMessage);
    throw new Error(errorMessage);
  },
  error => {
    let errorMessage = '网络错误，请检查网络连接';
    if (error.response) {
      const errorData = error.response.data;
      if (errorData && errorData.code) {
        errorMessage = ERROR_MESSAGES[errorData.code] || errorData.message || '请求失败';
      } else if (error.response.status === 401) {
        errorMessage = '登录已过期，请重新登录';
        window.location.href = '/login';
      } else if (error.response.status === 403) {
        errorMessage = '无权限执行该操作（需要平台管理员角色）';
      } else if (error.response.status === 404) {
        errorMessage = '请求的资源不存在';
      } else if (error.response.status === 500) {
        errorMessage = '系统内部错误，请稍后重试';
      }
    } else if (error.request) {
      errorMessage = '服务器无响应，请稍后重试';
    }
    message.error(errorMessage);
    throw new Error(errorMessage);
  }
);

// 认证API
export const authApi = {
  login: (credentials) => api.post('/auth/login', credentials),
  logout: () => api.post('/auth/logout'),
  getMe: () => api.get('/auth/me'),
  refreshToken: (refreshToken) => api.post('/auth/refresh', { refreshToken })
};

// 租户API
export const tenantApi = {
  getTenants: () => api.get('/tenants'),
  getTenant: (id) => api.get(`/tenants/${id}`),
  createTenant: (tenant) => api.post('/tenants', tenant),
  updateTenant: (id, updates) => api.put(`/tenants/${id}`, updates),
  deleteTenant: (id) => api.delete(`/tenants/${id}`),
  activateTenant: (id) => api.put(`/tenants/${id}/activate`),
  suspendTenant: (id) => api.put(`/tenants/${id}/suspend`),
  deactivateTenant: (id) => api.put(`/tenants/${id}/deactivate`),
  createTenantAdmin: (id, userData) => api.post(`/tenants/${id}/admin`, userData)
};

// 用户API
export const userApi = {
  getUsers: (tenantId) => api.get('/users', { params: { tenantId } }),
  getUser: (id) => api.get(`/users/${id}`),
  createUser: (user) => api.post('/users', user),
  updateUser: (id, updates) => api.put(`/users/${id}`, updates),
  deleteUser: (id) => api.delete(`/users/${id}`),
  activateUser: (id) => api.put(`/users/${id}/activate`),
  deactivateUser: (id) => api.put(`/users/${id}/deactivate`),
  lockUser: (id) => api.put(`/users/${id}/lock`)
};

// 角色API
export const roleApi = {
  getRoles: (tenantId) => api.get('/roles', { params: { tenantId } }),
  getSystemRoles: () => api.get('/roles/system'),
  getRole: (id) => api.get(`/roles/${id}`),
  createRole: (data) => api.post('/roles', data),
  createSystemRole: (data) => api.post('/roles/system', data),
  updateRole: (id, data) => api.put(`/roles/${id}`, data),
  deleteRole: (id) => api.delete(`/roles/${id}`),
  setPermissions: (id, permissions) => api.put(`/roles/${id}/permissions`, { permissions }),
  addPermission: (id, permission) => api.post(`/roles/${id}/permissions`, { permission }),
  removePermission: (id, permission) => api.delete(`/roles/${id}/permissions/${permission}`)
};

// 平台系统配置（暂为本地模拟，等待后端系统配置接口落地）
export const platformApi = {
  getSystemConfig: async () => ({
    general: {
      siteName: '物流ERP系统',
      siteDescription: '综合电商物流ERP管理系统',
      contactEmail: 'admin@example.com',
      contactPhone: '400-123-4567'
    },
    email: {
      smtpHost: 'smtp.example.com',
      smtpPort: 587,
      senderEmail: 'noreply@example.com',
      enabled: true
    },
    security: {
      sessionTimeout: 3600,
      maxLoginAttempts: 5,
      twoFactorAuthRequired: false,
      passwordPolicyEnabled: true
    }
  }),
  saveSystemConfig: async (config) => {
    console.log('Saved config:', config);
    return true;
  }
};

export default api;
