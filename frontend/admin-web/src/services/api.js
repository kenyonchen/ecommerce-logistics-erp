// API 服务模拟
const mockTenants = [
  {
    id: '1',
    name: '阿里巴巴',
    description: '电商平台',
    contactEmail: 'contact@alibaba.com',
    contactPhone: '13800138001',
    status: 'ACTIVE',
    createdAt: new Date().toISOString()
  },
  {
    id: '2',
    name: '京东',
    description: '电商平台',
    contactEmail: 'contact@jd.com',
    contactPhone: '13900139001',
    status: 'ACTIVE',
    createdAt: new Date().toISOString()
  }
];

const mockUsers = [
  {
    id: '1',
    username: 'admin',
    email: 'admin@example.com',
    phone: '13800138001',
    firstName: 'Admin',
    lastName: 'User',
    status: 'ACTIVE',
    createdAt: new Date().toISOString()
  }
];

// 模拟 API 延迟
const delay = (ms) => new Promise(resolve => setTimeout(resolve, ms));

// 租户 API
export const tenantApi = {
  getTenants: async () => {
    await delay(500);
    return mockTenants;
  },
  getTenant: async (id) => {
    await delay(300);
    return mockTenants.find(tenant => tenant.id === id);
  },
  createTenant: async (tenant) => {
    await delay(500);
    const newTenant = {
      id: Date.now().toString(),
      ...tenant,
      status: 'ACTIVE',
      createdAt: new Date().toISOString()
    };
    mockTenants.push(newTenant);
    return newTenant;
  },
  updateTenant: async (id, updates) => {
    await delay(500);
    const index = mockTenants.findIndex(tenant => tenant.id === id);
    if (index !== -1) {
      mockTenants[index] = { ...mockTenants[index], ...updates };
      return mockTenants[index];
    }
    throw new Error('Tenant not found');
  },
  deleteTenant: async (id) => {
    await delay(500);
    const index = mockTenants.findIndex(tenant => tenant.id === id);
    if (index !== -1) {
      mockTenants.splice(index, 1);
      return true;
    }
    throw new Error('Tenant not found');
  }
};

// 用户 API
export const userApi = {
  getUsers: async (tenantId) => {
    await delay(500);
    return mockUsers;
  },
  getUser: async (id) => {
    await delay(300);
    return mockUsers.find(user => user.id === id);
  },
  createUser: async (user) => {
    await delay(500);
    const newUser = {
      id: Date.now().toString(),
      ...user,
      status: 'ACTIVE',
      createdAt: new Date().toISOString()
    };
    mockUsers.push(newUser);
    return newUser;
  },
  updateUser: async (id, updates) => {
    await delay(500);
    const index = mockUsers.findIndex(user => user.id === id);
    if (index !== -1) {
      mockUsers[index] = { ...mockUsers[index], ...updates };
      return mockUsers[index];
    }
    throw new Error('User not found');
  },
  deleteUser: async (id) => {
    await delay(500);
    const index = mockUsers.findIndex(user => user.id === id);
    if (index !== -1) {
      mockUsers.splice(index, 1);
      return true;
    }
    throw new Error('User not found');
  }
};

// 角色 API
export const roleApi = {
  getRoles: async (tenantId) => {
    await delay(300);
    return [
      { id: '1', name: 'Admin', description: '管理员角色' },
      { id: '2', name: 'User', description: '普通用户角色' }
    ];
  }
};

// 平台 API
export const platformApi = {
  getSystemConfig: async () => {
    await delay(300);
    return {
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
    };
  },
  saveSystemConfig: async (config) => {
    await delay(500);
    console.log('Saved config:', config);
    return true;
  }
};

// 认证 API
export const authApi = {
  login: async (credentials) => {
    await delay(800);
    if (credentials.username === 'admin' && credentials.password === 'admin123') {
      return {
        token: 'mock-jwt-token',
        user: {
          id: '1',
          username: 'admin',
          email: 'admin@example.com',
          role: 'ADMIN'
        }
      };
    }
    throw new Error('Invalid credentials');
  },
  logout: async () => {
    await delay(300);
    return true;
  }
};