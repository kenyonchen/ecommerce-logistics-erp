import React from 'react';
import { Routes, Route } from 'react-router-dom';

// 认证相关组件
import { AuthGuard, PlatformAdminGuard, TenantAdminGuard } from './components/Auth/AuthGuard.jsx';
import Login from './pages/auth/Login.jsx';

// 页面组件
import Home from './pages/home/Home';
import OrderList from './pages/order/OrderList';
import OrderDetail from './pages/order/OrderDetail';
import CreateOrder from './pages/order/CreateOrder';
import LogisticsList from './pages/logistics/LogisticsList';
import LogisticsDetail from './pages/logistics/LogisticsDetail';
import CreateLogistics from './pages/logistics/CreateLogistics';
import ProductList from './pages/product/ProductList';
import ProductDetail from './pages/product/ProductDetail';
import CreateProduct from './pages/product/CreateProduct';
import WarehouseList from './pages/warehouse/WarehouseList';
import WarehouseDetail from './pages/warehouse/WarehouseDetail';
import CreateWarehouse from './pages/warehouse/CreateWarehouse';
import CustomerList from './pages/customer/CustomerList';
import CustomerDetail from './pages/customer/CustomerDetail';
import CreateCustomer from './pages/customer/CreateCustomer';
import FinanceList from './pages/finance/FinanceList';
import FinanceDetail from './pages/finance/FinanceDetail';
import CreateFinance from './pages/finance/CreateFinance';
import PurchaseList from './pages/purchase/PurchaseList';
import PurchaseDetail from './pages/purchase/PurchaseDetail';
import CreatePurchase from './pages/purchase/CreatePurchase';
import PlatformIntegration from './pages/platform-integration/PlatformIntegration';
import PlatformIntegrationDetail from './pages/platform-integration/PlatformIntegrationDetail';
import CreatePlatformIntegration from './pages/platform-integration/CreatePlatformIntegration';

// 租户管理页面
import TenantList from './pages/tenant/TenantList.jsx';
import CreateTenant from './pages/tenant/CreateTenant.jsx';
import TenantDetail from './pages/tenant/TenantDetail.jsx';

// 用户管理页面
import UserList from './pages/user/UserList.jsx';
import CreateUser from './pages/user/CreateUser.jsx';
import UserDetail from './pages/user/UserDetail.jsx';

// 平台管理页面
import PlatformDashboard from './pages/platform/PlatformDashboard.jsx';
import SystemConfig from './pages/platform/SystemConfig.jsx';

const AppRoutes = () => {
  return (
    <Routes>
      {/* 公共路由 */}
      <Route path="/login" element={<Login />} />
      
      {/* 需要认证的路由 */}
      <Route element={<AuthGuard />}>
        <Route path="/" element={<Home />} />
        <Route path="/orders" element={<OrderList />} />
        <Route path="/orders/:id" element={<OrderDetail />} />
        <Route path="/orders/create" element={<CreateOrder />} />
        <Route path="/logistics" element={<LogisticsList />} />
        <Route path="/logistics/:id" element={<LogisticsDetail />} />
        <Route path="/logistics/create" element={<CreateLogistics />} />
        <Route path="/products" element={<ProductList />} />
        <Route path="/products/:id" element={<ProductDetail />} />
        <Route path="/products/create" element={<CreateProduct />} />
        <Route path="/warehouses" element={<WarehouseList />} />
        <Route path="/warehouses/:id" element={<WarehouseDetail />} />
        <Route path="/warehouses/create" element={<CreateWarehouse />} />
        <Route path="/customers" element={<CustomerList />} />
        <Route path="/customers/:id" element={<CustomerDetail />} />
        <Route path="/customers/create" element={<CreateCustomer />} />
        <Route path="/finance" element={<FinanceList />} />
        <Route path="/finance/:id" element={<FinanceDetail />} />
        <Route path="/finance/create" element={<CreateFinance />} />
        <Route path="/purchase" element={<PurchaseList />} />
        <Route path="/purchase/:id" element={<PurchaseDetail />} />
        <Route path="/purchase/create" element={<CreatePurchase />} />
        <Route path="/platform-integration" element={<PlatformIntegration />} />
        <Route path="/platform-integration/:id" element={<PlatformIntegrationDetail />} />
        <Route path="/platform-integration/create" element={<CreatePlatformIntegration />} />
        
        {/* 租户管理 */}
        <Route path="/tenants" element={<TenantList />} />
        <Route path="/tenants/create" element={<CreateTenant />} />
        <Route path="/tenants/:id" element={<TenantDetail />} />
        
        {/* 用户管理 */}
        <Route path="/users" element={<UserList />} />
        <Route path="/users/create" element={<CreateUser />} />
        <Route path="/users/:id" element={<UserDetail />} />
      </Route>
      
      {/* 平台管理员路由 */}
      <Route element={<PlatformAdminGuard />}>
        <Route path="/platform/dashboard" element={<PlatformDashboard />} />
        <Route path="/platform/config" element={<SystemConfig />} />
      </Route>
      
      {/* 租户管理员路由 */}
      <Route element={<TenantAdminGuard />}>
        {/* 租户管理员专属路由 */}
      </Route>
    </Routes>
  );
};

export default AppRoutes;
