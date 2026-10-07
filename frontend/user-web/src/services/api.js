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
    // 文件下载（模板、面单）等二进制响应直接透传，不走统一结果包装解包
    if (response.config?.responseType === 'blob' || response.data instanceof Blob) {
      return response.data;
    }
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
    let errorCode;
    let errorStatus;

    if (error.response) {
      // 服务器返回错误
      errorStatus = error.response.status;
      const errorData = error.response.data;
      if (errorData.code) {
        errorCode = errorData.code;
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
    // 附加业务码与HTTP 状态，页面可据此区分错误类型（如 403 已被上面提示过，无需重复弹窗）
    const requestError = new Error(errorMessage);
    requestError.code = errorCode;
    requestError.status = errorStatus;
    throw requestError;
  }
);

// 认证相关API
export const authApi = {
  login: (credentials) => api.post('/auth/login', credentials),
  logout: () => api.post('/auth/logout'),
  refreshToken: (refreshToken) => api.post('/auth/refresh', { refreshToken }),
  getMe: () => api.get('/auth/me')
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
  // 分页条件查询：{ tenantId, orderNumber, customerId, customerName, status, platform, keyword,
  //               startDate, endDate, page, size }
  getOrders: (params) => api.get('/orders', { params }),
  getOrderById: (id) => api.get(`/orders/${id}`),
  getOrderByNumber: (orderNumber) => api.get(`/orders/number/${orderNumber}`),
  updateOrderStatus: (id, status) => api.put(`/orders/${id}/status`, null, { params: { status } }),
  cancelOrder: (id) => api.post(`/orders/${id}/cancel`),
  deleteOrder: (id) => api.delete(`/orders/${id}`),
  getOrdersByCustomer: (customerId) => api.get(`/orders/customer/${customerId}`),
  getStatuses: () => api.get('/orders/statuses')
};

// 第三方平台订单导入相关API
export const orderImportApi = {
  getTargetFields: () => api.get('/orders/import/target-fields'),
  getTransforms: () => api.get('/orders/import/transforms'),
  getOrderStatuses: () => api.get('/orders/import/order-statuses'),
  getFieldMappings: (platform, tenantId) =>
    api.get('/orders/import/field-mappings', { params: { platform, tenantId } }),
  saveFieldMappings: (data) => api.put('/orders/import/field-mappings', data),
  preview: (data) => api.post('/orders/import/preview', data),
  upload: (file, { tenantId, platform, mode = 'preview', updateExisting = true }) => {
    const formData = new FormData();
    formData.append('file', file);
    if (tenantId) formData.append('tenantId', tenantId);
    if (platform) formData.append('platform', platform);
    formData.append('mode', mode);
    formData.append('updateExisting', String(updateExisting));
    return api.post('/orders/import/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    });
  },
  import: (data) => api.post('/orders/import', data),
  getJobs: (params) => api.get('/orders/import/jobs', { params }),
  getJob: (jobId) => api.get(`/orders/import/jobs/${jobId}`),
  downloadTemplate: () => api.get('/orders/import/template', { responseType: 'blob' })
};

// 物流相关API
export const logisticsApi = {
  // 分页条件查询：{ tenantId, trackingNumber, orderId, channelId, status, keyword,
  //               startDate, endDate, page, size }
  getLogisticsOrders: (params) => api.get('/logistics', { params }),
  getLogisticsById: (id) => api.get(`/logistics/${id}`),
  getByOrderId: (orderId) => api.get(`/logistics/order/${orderId}`),
  createLogistics: (data) => api.post('/logistics', data),
  // data: { status, description }
  updateStatus: (id, data) => api.put(`/logistics/${id}/status`, data),
  ship: (id, { shippedAt, trackingNumber } = {}) =>
    api.post(`/logistics/${id}/ship`, null, { params: { shippedAt, trackingNumber } }),
  cancel: (id) => api.post(`/logistics/${id}/cancel`),
  generateLabel: (id) => api.post(`/logistics/${id}/label`),
  track: (id) => api.post(`/logistics/${id}/track`),
  // data: { time, status, description, location }
  addTrace: (id, data) => api.post(`/logistics/${id}/traces`, data),
  deleteLogistics: (id) => api.delete(`/logistics/${id}`),
  // 批量发货：{ tenantId, items: [{ orderNumber | externalOrderId, channelCode | logisticsChannelId,
  //             trackingNumber, carrier, shippedAt }] }
  batchShip: (data) => api.post('/logistics/batch-ship', data),
  getStatuses: () => api.get('/logistics/statuses')
};

// 物流渠道相关API
export const logisticsChannelApi = {
  // params: { country, onlyActive }
  getChannels: (params) => api.get('/logistics-channels', { params }),
  getChannelById: (id) => api.get(`/logistics-channels/${id}`),
  createChannel: (data) => api.post('/logistics-channels', data),
  updateChannel: (id, data) => api.put(`/logistics-channels/${id}`, data),
  changeStatus: (id, active) => api.put(`/logistics-channels/${id}/status`, null, { params: { active } }),
  deleteChannel: (id) => api.delete(`/logistics-channels/${id}`),
  // 运费试算：weight 单位为克
  calculateFee: (id, weight, country) =>
    api.get(`/logistics-channels/${id}/fee`, { params: { weight, country } })
};

// 第三方平台物流数据导入相关API
export const logisticsImportApi = {
  getTargetFields: () => api.get('/logistics/import/target-fields'),
  getTransforms: () => api.get('/logistics/import/transforms'),
  getLogisticsStatuses: () => api.get('/logistics/import/logistics-statuses'),
  getFieldMappings: (platform, tenantId) =>
    api.get('/logistics/import/field-mappings', { params: { platform, tenantId } }),
  saveFieldMappings: (data) => api.put('/logistics/import/field-mappings', data),
  preview: (data) => api.post('/logistics/import/preview', data),
  upload: (file, { tenantId, platform, mode = 'preview', updateExisting = true }) => {
    const formData = new FormData();
    formData.append('file', file);
    if (tenantId) formData.append('tenantId', tenantId);
    if (platform) formData.append('platform', platform);
    formData.append('mode', mode);
    formData.append('updateExisting', String(updateExisting));
    return api.post('/logistics/import/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    });
  },
  import: (data) => api.post('/logistics/import', data),
  // params: { integrationId, tenantId, orderNumber, trackingNumber, shippedFrom, shippedTo, limit, useSampleData }
  sync: (params) => api.post('/logistics/import/sync', null, { params }),
  getJobs: (params) => api.get('/logistics/import/jobs', { params }),
  getJob: (jobId) => api.get(`/logistics/import/jobs/${jobId}`),
  downloadTemplate: () => api.get('/logistics/import/template', { responseType: 'blob' })
};

// 产品相关API
export const productApi = {
  // 列表与查询（服务端分页）
  getProducts: (params) => api.get('/products', { params }),
  getProductById: (id) => api.get(`/products/${id}`),
  getProductBySkuCode: (skuCode) => api.get(`/products/sku/${skuCode}`),
  createProduct: (data) => api.post('/products', data),
  updateProduct: (id, data) => api.put(`/products/${id}`, data),
  deleteProduct: (id, tenantId) => api.delete(`/products/${id}`, { params: { tenantId } }),
  activateProduct: (id) => api.put(`/products/${id}/activate`),
  deactivateProduct: (id) => api.put(`/products/${id}/deactivate`),
  addSku: (id, data) => api.post(`/products/${id}/skus`, data),
  removeSku: (id, skuCode, tenantId) => api.delete(`/products/${id}/skus/${skuCode}`, { params: { tenantId } }),
  batchChangeActive: (productIds, active, tenantId) =>
    api.put('/products/batch/active', { productIds, active, tenantId }),
  batchDelete: (productIds, tenantId) => api.delete('/products/batch', { data: { productIds, tenantId } })
};

// 第三方平台商品导入相关API
export const productImportApi = {
  getTargetFields: () => api.get('/products/import/target-fields'),
  getTransforms: () => api.get('/products/import/transforms'),
  getFieldMappings: (platform, tenantId) =>
    api.get('/products/import/field-mappings', { params: { platform, tenantId } }),
  saveFieldMappings: (data) => api.put('/products/import/field-mappings', data),
  preview: (data) => api.post('/products/import/preview', data),
  upload: (file, { tenantId, platform, mode = 'preview', updateExisting = true }) => {
    const formData = new FormData();
    formData.append('file', file);
    if (tenantId) formData.append('tenantId', tenantId);
    if (platform) formData.append('platform', platform);
    formData.append('mode', mode);
    formData.append('updateExisting', String(updateExisting));
    return api.post('/products/import/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    });
  },
  import: (data) => api.post('/products/import', data),
  getJobs: (params) => api.get('/products/import/jobs', { params }),
  getJob: (jobId) => api.get(`/products/import/jobs/${jobId}`),
  downloadTemplate: () => api.get('/products/import/template', { responseType: 'blob' })
};

// 仓储相关API
export const warehouseApi = {
  getWarehouses: () => api.get('/warehouses'),
  getWarehouseById: (id) => api.get(`/warehouses/${id}`),
  getWarehouseByCode: (code) => api.get(`/warehouses/code/${code}`),
  createWarehouse: (data) => api.post('/warehouses', data),
  updateWarehouse: (id, data) => api.put(`/warehouses/${id}`, data),
  deleteWarehouse: (id) => api.delete(`/warehouses/${id}`),
  changeStatus: (id, active) => api.put(`/warehouses/${id}/status`, null, { params: { active } }),
  addLocation: (id, data) => api.post(`/warehouses/${id}/locations`, data),
  getInventory: (warehouseId) => api.get(`/warehouses/${warehouseId}/inventory`),
  inventoryIn: (warehouseId, data) => api.post(`/warehouses/${warehouseId}/inventory/in`, data),
  inventoryOut: (warehouseId, data) => api.post(`/warehouses/${warehouseId}/inventory/out`, data),
  countInventory: (warehouseId, productId, actualQuantity) =>
    api.post(`/warehouses/${warehouseId}/inventory/count`, null, { params: { productId, actualQuantity } }),
  moveInventory: (warehouseId, data) => api.post(`/warehouses/${warehouseId}/inventory/move`, data)
};

// 第三方平台店铺仓库对接相关API
export const shopWarehouseApi = {
  getShopWarehouses: (params) => api.get('/warehouses/shop', { params }),
  getShopWarehouseById: (id) => api.get(`/warehouses/shop/${id}`),
  bindShopWarehouse: (data) => api.post('/warehouses/shop/bind', data),
  unbindShopWarehouse: (id) => api.post(`/warehouses/shop/${id}/unbind`),
  syncFromPlatform: (integrationId, params) =>
    api.post(`/warehouses/shop/sync/${integrationId}`, null, { params })
};

// 第三方平台店铺仓库导入相关API
export const warehouseImportApi = {
  getTargetFields: () => api.get('/warehouses/import/target-fields'),
  getTransforms: () => api.get('/warehouses/import/transforms'),
  getFieldMappings: (platform, tenantId) =>
    api.get('/warehouses/import/field-mappings', { params: { platform, tenantId } }),
  saveFieldMappings: (data) => api.put('/warehouses/import/field-mappings', data),
  preview: (data) => api.post('/warehouses/import/preview', data),
  upload: (file, { tenantId, platform, mode = 'preview', updateExisting = true }) => {
    const formData = new FormData();
    formData.append('file', file);
    if (tenantId) formData.append('tenantId', tenantId);
    if (platform) formData.append('platform', platform);
    formData.append('mode', mode);
    formData.append('updateExisting', String(updateExisting));
    return api.post('/warehouses/import/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    });
  },
  import: (data) => api.post('/warehouses/import', data),
  getJobs: (params) => api.get('/warehouses/import/jobs', { params }),
  getJob: (jobId) => api.get(`/warehouses/import/jobs/${jobId}`),
  downloadTemplate: () => api.get('/warehouses/import/template', { responseType: 'blob' })
};

// 客户相关API
export const customerApi = {
  // 分页条件查询：{ tenantId, customerId, customerName, email, phone, status, sourcePlatform,
  //               keyword, createdFrom, createdTo, page, size, sort }
  getCustomers: (params) => api.get('/customers', { params }),
  getStatusOptions: () => api.get('/customers/status-options'),
  getCustomerById: (id) => api.get(`/customers/${id}`),
  createCustomer: (data) => api.post('/customers', data),
  updateCustomer: (id, data) => api.put(`/customers/${id}`, data),
  activateCustomer: (id) => api.put(`/customers/${id}/activate`),
  deactivateCustomer: (id) => api.put(`/customers/${id}/deactivate`),
  suspendCustomer: (id) => api.put(`/customers/${id}/suspend`),
  deleteCustomer: (id) => api.delete(`/customers/${id}`)
};

// 第三方平台客户（买家）导入相关API
export const customerImportApi = {
  getTargetFields: () => api.get('/customers/import/target-fields'),
  getTransforms: () => api.get('/customers/import/transforms'),
  getFieldMappings: (platform, tenantId) =>
    api.get('/customers/import/field-mappings', { params: { platform, tenantId } }),
  saveFieldMappings: (data) => api.put('/customers/import/field-mappings', data),
  // sourceType: CUSTOMERS（平台买家接口）| ORDERS（从平台订单抽取买家）
  preview: (data) => api.post('/customers/import/preview', data),
  upload: (file, { tenantId, platform, sourceType = 'CUSTOMERS', mode = 'preview', updateExisting = true }) => {
    const formData = new FormData();
    formData.append('file', file);
    if (tenantId) formData.append('tenantId', tenantId);
    if (platform) formData.append('platform', platform);
    formData.append('sourceType', sourceType);
    formData.append('mode', mode);
    formData.append('updateExisting', String(updateExisting));
    return api.post('/customers/import/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    });
  },
  import: (data) => api.post('/customers/import', data),
  getJobs: (params) => api.get('/customers/import/jobs', { params }),
  getJob: (jobId) => api.get(`/customers/import/jobs/${jobId}`),
  downloadTemplate: () => api.get('/customers/import/template', { responseType: 'blob' })
};

// 财务相关API
export const financeApi = {
  // 分页条件查询：{ page, size, type, status, referenceId, referenceType, from, to, tenantId }
  // from/to 为 'YYYY-MM-DDTHH:mm:ss' 本地时间串（不带 Z 偏移），按 createdAt 区间过滤；page 从 0 开始，返回 PageResult<FinancialTransactionDto>
  getTransactions: (params) => api.get('/finance/transactions', { params }),
  getTransactionById: (transactionId) => api.get(`/finance/transactions/${transactionId}`),
  // data: { type, amount, currency, referenceId, referenceType, description }
  createTransaction: (data) => api.post('/finance/transactions', data),
  // 状态推进（仅 PENDING 可推进）
  completeTransaction: (id) => api.put(`/finance/transactions/${id}/complete`),
  failTransaction: (id) => api.put(`/finance/transactions/${id}/fail`),
  cancelTransaction: (id) => api.put(`/finance/transactions/${id}/cancel`),
  // 按关联单据查询同类流水（详情页关联流水）
  getTransactionsByReference: (referenceId, referenceType) =>
    api.get('/finance/transactions/by-reference', { params: { referenceId, referenceType } })
};

// 第三方平台财务流水导入相关API
export const financeImportApi = {
  getTargetFields: () => api.get('/finance/import/target-fields'),
  getTransforms: () => api.get('/finance/import/transforms'),
  getTransactionTypes: () => api.get('/finance/import/transaction-types'),
  getFieldMappings: (platform, tenantId) =>
    api.get('/finance/import/field-mappings', { params: { platform, tenantId } }),
  saveFieldMappings: (data) => api.put('/finance/import/field-mappings', data),
  preview: (data) => api.post('/finance/import/preview', data),
  upload: (file, { tenantId, platform, mode = 'preview', updateExisting = true }) => {
    const formData = new FormData();
    formData.append('file', file);
    if (tenantId) formData.append('tenantId', tenantId);
    if (platform) formData.append('platform', platform);
    formData.append('mode', mode);
    formData.append('updateExisting', String(updateExisting));
    return api.post('/finance/import/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    });
  },
  // 批量导入：{ tenantId, platform, rows, updateExisting, fileName }
  import: (data) => api.post('/finance/import', data),
  getJobs: (params) => api.get('/finance/import/jobs', { params }),
  getJob: (jobId) => api.get(`/finance/import/jobs/${jobId}`),
  downloadTemplate: () => api.get('/finance/import/template', { responseType: 'blob' })
};

// 采购相关API
// 列表是服务端分页 + 条件查询，返回 PageResult：{ content, total, page, size }（page 从 0 开始）；
// status 为 PurchaseStatus 枚举名（PENDING / APPROVED / REJECTED / IN_TRANSIT / DELIVERED / CANCELLED），
// 出参统一为 PurchaseOrderDto，其中 statusLabel 是中文名、allowedNextStatuses 是服务端算出的可流转状态（按钮门禁以此为准）
// 创建/加明细/改明细都是 JSON 请求体；tenantId 走 query 参数（后端 TenantIsolationAspect 校验其与登录租户一致，
// 缺省值 default-tenant 只对种子租户成立，所以业务页必须显式传）
export const purchaseApi = {
  // params: { tenantId, purchaseOrderId, supplierId, supplierName, warehouseId, status,
  //           minTotalAmount, startDate, endDate, page, size }
  getPurchaseOrders: (params) => api.get('/purchases', { params }),
  getStatuses: () => api.get('/purchases/statuses'),
  getPurchaseOrderById: (purchaseOrderId, tenantId) =>
    api.get(`/purchases/${purchaseOrderId}`, { params: { tenantId } }),
  // data: { tenantId, supplierId, supplierName, warehouseId, items: [{ skuCode, productName, quantity, unitPrice }] }
  // 明细随单头一次提交，金额由服务端按明细小计重算
  createPurchaseOrder: (data) => api.post('/purchases', data),
  // data: { skuCode, productName, quantity, unitPrice }；返回体里的明细行 id 由服务端生成，可直接用于改删
  addPurchaseOrderItem: (purchaseOrderId, data, tenantId) =>
    api.post(`/purchases/${purchaseOrderId}/items`, data, { params: { tenantId } }),
  // data: { quantity, unitPrice }
  updatePurchaseOrderItem: (purchaseOrderId, itemId, data, tenantId) =>
    api.put(`/purchases/${purchaseOrderId}/items/${itemId}`, data, { params: { tenantId } }),
  removePurchaseOrderItem: (purchaseOrderId, itemId, tenantId) =>
    api.delete(`/purchases/${purchaseOrderId}/items/${itemId}`, { params: { tenantId } }),
  // 状态机：PENDING→approve/reject/cancel，APPROVED→in-transit/cancel，IN_TRANSIT→delivered
  // 审批动作 data: { approvedBy, comment }；approvedBy 省略时后端记登录账号，拒绝必须填 comment
  approvePurchaseOrder: (purchaseOrderId, data, tenantId) =>
    api.put(`/purchases/${purchaseOrderId}/approve`, data || {}, { params: { tenantId } }),
  rejectPurchaseOrder: (purchaseOrderId, data, tenantId) =>
    api.put(`/purchases/${purchaseOrderId}/reject`, data || {}, { params: { tenantId } }),
  // 送达会同步完成收货入库与采购应付入账
  markPurchaseOrderAsInTransit: (purchaseOrderId, tenantId) =>
    api.put(`/purchases/${purchaseOrderId}/in-transit`, null, { params: { tenantId } }),
  markPurchaseOrderAsDelivered: (purchaseOrderId, tenantId) =>
    api.put(`/purchases/${purchaseOrderId}/delivered`, null, { params: { tenantId } }),
  cancelPurchaseOrder: (purchaseOrderId, tenantId) =>
    api.put(`/purchases/${purchaseOrderId}/cancel`, null, { params: { tenantId } })
};

// 平台集成相关API
export const platformIntegrationApi = {
  getIntegrations: () => api.get('/platform-integrations'),
  getIntegrationById: (id) => api.get(`/platform-integrations/${id}`),
  createIntegration: (data) => api.post('/platform-integrations', data),
  updateIntegration: (id, data) => api.put(`/platform-integrations/${id}`, data),
  deleteIntegration: (id) => api.delete(`/platform-integrations/${id}`),
  activate: (id) => api.post(`/platform-integrations/${id}/activate`),
  deactivate: (id) => api.post(`/platform-integrations/${id}/deactivate`),
  syncOrders: (id, params) =>
    api.post(`/platform-integrations/${id}/sync-orders`, null, { params }),
  syncCustomers: (id, params) =>
    api.post(`/platform-integrations/${id}/sync-customers`, null, { params }),
  syncProducts: (id, tenantId) =>
    api.post(`/platform-integrations/${id}/sync-products`, null, { params: { tenantId } }),
  syncInventory: (id) => api.post(`/platform-integrations/${id}/sync-inventory`),
  // params: { tenantId, orderNumber, trackingNumber, shippedFrom, shippedTo, limit, useSampleData }
  syncLogistics: (id, params) =>
    api.post(`/platform-integrations/${id}/sync-logistics`, null, { params }),
  // 连通性测试：失败也返回 200，结果在 data.success/data.message 里
  testConnection: (id) => api.post(`/platform-integrations/${id}/test-connection`),
  // 发货回写：body { logisticsOrderId, notifyCustomer }
  pushFulfillment: (id, data) => api.post(`/platform-integrations/${id}/push-fulfillment`, data)
};

// 系统概览相关API（首页）
export const overviewApi = {
  // params: { tenantId, recentOrderLimit }
  // 返回 SystemOverviewDto：订单各状态计数、产品/仓库/客户/采购/平台集成总数、
  // totalRevenue（财务流水 income + success 的金额合计）、recentOrders（按创建时间倒序）
  // tenantId 省略时后端取登录租户；业务页与种子租户不一致时需显式传
  getSystemOverview: (params) => api.get('/overview', { params })
};

// 店铺相关API（对应后端 /api/shops）
// 权限：列表/详情 shop:read；建店/改名/启停/回写策略/删除 shop:write；凭据写入 shop:credential_write
// 凭据语义：不填(null)=保留原值，填空串=清除该字段；任何写入都会把授权状态打回 PENDING
export const shopApi = {
  // params: { platform }
  getShops: (params) => api.get('/shops', { params }),
  getShopById: (id) => api.get(`/shops/${id}`),
  // data: { integrationId?, platform, shopCode, shopName?, region?, marketplaceId? }
  createShop: (data) => api.post('/shops', data),
  // data: { shopName?, active? }
  updateShop: (id, data) => api.put(`/shops/${id}`, data),
  // data: { apiKey?, apiSecret?, accessToken?, refreshToken?, tokenExpiresAt?,
  //         marketplaceId?, region?, externalShopId?, storeUrl?, apiVersion? }
  bindCredentials: (id, data) => api.put(`/shops/${id}/credentials`, data),
  // policy: AUTO / MANUAL / OFF
  changePushBackPolicy: (id, policy) => api.put(`/shops/${id}/push-back-policy`, { pushBackPolicy: policy }),
  deleteShop: (id) => api.delete(`/shops/${id}`)
};

// 平台相关API
export const platformApi = {
  // 根据实际后端API补充
};

export default api;
