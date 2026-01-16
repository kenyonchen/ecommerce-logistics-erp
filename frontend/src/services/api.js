import axios from 'axios';
import { message } from 'antd';

// 创建axios实例
const api = axios.create({
  baseURL: 'http://localhost:8080/api',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json'
  }
});

// 请求拦截器
api.interceptors.request.use(
  config => {
    // 从本地存储获取token
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  error => {
    return Promise.reject(error);
  }
);

// 错误码映射
const ERROR_MESSAGES = {
  'ENTITY_NOT_FOUND': '请求的资源不存在',
  'INVALID_OPERATION': '无效的操作',
  'VALIDATION_ERROR': '参数验证失败',
  'SYSTEM_ERROR': '系统内部错误，请稍后重试',
  'ERROR': '操作失败'
};

// 响应拦截器
api.interceptors.response.use(
  response => {
    // 后端返回的格式是 { code: "200", message: 'success', data: {} }
    if (response.data.code === "200") {
      return response.data.data;
    } else {
      // 处理错误
      const errorMessage = ERROR_MESSAGES[response.data.code] || response.data.message || '请求失败';
      message.error(errorMessage);
      throw new Error(errorMessage);
    }
  },
  error => {
    // 处理网络错误或其他错误
    let errorMessage = '网络错误，请检查网络连接';
    
    if (error.response) {
      // 服务器返回错误
      const errorData = error.response.data;
      if (errorData.code) {
        errorMessage = ERROR_MESSAGES[errorData.code] || errorData.message || '请求失败';
      } else if (error.response.status === 401) {
        errorMessage = '登录已过期，请重新登录';
        // 跳转到登录页面
        window.location.href = '/login';
      } else if (error.response.status === 404) {
        errorMessage = '请求的资源不存在';
      } else if (error.response.status === 500) {
        errorMessage = '系统内部错误，请稍后重试';
      }
    } else if (error.request) {
      // 请求已发出但没有收到响应
      errorMessage = '服务器无响应，请稍后重试';
    }
    
    message.error(errorMessage);
    throw new Error(errorMessage);
  }
);

// 认证相关API
export const authApi = {
  login: (credentials) => api.post('/auth/login', credentials),
  logout: () => api.post('/auth/logout'),
  refreshToken: (data) => api.post('/auth/refresh', data)
};

// 租户相关API
export const tenantApi = {
  createTenant: (tenantData) => api.post('/tenants', tenantData),
  getTenants: () => api.get('/tenants'),
  getTenantById: (id) => api.get(`/tenants/${id}`),
  updateTenant: (id, tenantData) => api.put(`/tenants/${id}`, tenantData),
  activateTenant: (id) => api.put(`/tenants/${id}/activate`),
  suspendTenant: (id) => api.put(`/tenants/${id}/suspend`),
  deactivateTenant: (id) => api.put(`/tenants/${id}/deactivate`),
  deleteTenant: (id) => api.delete(`/tenants/${id}`),
  createTenantAdmin: (id, userData) => api.post(`/tenants/${id}/admin`, userData)
};

// 用户相关API
export const userApi = {
  createUser: (userData) => api.post('/users', userData),
  getUsers: (tenantId) => api.get('/users', { params: { tenantId } }),
  getUserById: (id) => api.get(`/users/${id}`),
  updateUser: (id, userData) => api.put(`/users/${id}`, userData),
  updatePassword: (id, password) => api.put(`/users/${id}/password`, { password }),
  activateUser: (id) => api.put(`/users/${id}/activate`),
  deactivateUser: (id) => api.put(`/users/${id}/deactivate`),
  lockUser: (id) => api.put(`/users/${id}/lock`),
  deleteUser: (id) => api.delete(`/users/${id}`),
  assignRole: (id, roleId) => api.post(`/users/${id}/roles`, { roleId }),
  removeRole: (id, roleId) => api.delete(`/users/${id}/roles/${roleId}`)
};

// 角色相关API
export const roleApi = {
  createRole: (roleData) => api.post('/roles', roleData),
  createSystemRole: (roleData) => api.post('/roles/system', roleData),
  getRoles: (tenantId) => api.get('/roles', { params: { tenantId } }),
  getSystemRoles: () => api.get('/roles/system'),
  getRoleById: (id) => api.get(`/roles/${id}`),
  updateRole: (id, roleData) => api.put(`/roles/${id}`, roleData),
  deleteRole: (id) => api.delete(`/roles/${id}`),
  addPermission: (id, permission) => api.post(`/roles/${id}/permissions`, { permission }),
  removePermission: (id, permission) => api.delete(`/roles/${id}/permissions/${permission}`),
  setPermissions: (id, permissions) => api.put(`/roles/${id}/permissions`, { permissions })
};

// 订单相关API
export const orderApi = {
  createOrder: (orderData) => api.post('/orders', orderData),
  getOrderById: (id) => api.get(`/orders/${id}`),
  updateOrderStatus: (id, status) => api.put(`/orders/${id}/status`, null, { params: { status } }),
  getOrdersByCustomer: (customerId) => api.get(`/orders/customer/${customerId}`)
};

// 物流相关API
export const logisticsApi = {
  // 根据实际后端API补充
};

// 产品相关API
export const productApi = {
  // 根据实际后端API补充
};

// 仓储相关API
export const warehouseApi = {
  // 根据实际后端API补充
};

// 客户相关API
export const customerApi = {
  // 根据实际后端API补充
};

// 财务相关API
export const financeApi = {
  // 根据实际后端API补充
};

// 采购相关API
export const purchaseApi = {
  // 根据实际后端API补充
};

// 平台集成相关API
export const platformIntegrationApi = {
  // 根据实际后端API补充
};

export default api;
