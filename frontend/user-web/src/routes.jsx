import React from 'react';
import { Routes, Route, Outlet } from 'react-router-dom';
import AppLayout from './components/Layout.jsx';

// 认证相关组件
import { AuthGuard, PlatformAdminGuard, TenantAdminGuard } from './components/Auth/AuthGuard.jsx';
import Login from './pages/auth/Login.jsx';

// 页面组件
import Home from './pages/home/Home.jsx';
import OrderList from './pages/order/OrderList.jsx';
import OrderDetail from './pages/order/OrderDetail.jsx';
import CreateOrder from './pages/order/CreateOrder.jsx';
import LogisticsList from './pages/logistics/LogisticsList.jsx';
import LogisticsDetail from './pages/logistics/LogisticsDetail.jsx';
import CreateLogistics from './pages/logistics/CreateLogistics.jsx';
import ProductList from './pages/product/ProductList.jsx';
import ProductDetail from './pages/product/ProductDetail.jsx';
import CreateProduct from './pages/product/CreateProduct.jsx';
import WarehouseList from './pages/warehouse/WarehouseList.jsx';
import WarehouseDetail from './pages/warehouse/WarehouseDetail.jsx';
import CreateWarehouse from './pages/warehouse/CreateWarehouse.jsx';
import CustomerList from './pages/customer/CustomerList.jsx';
import CustomerDetail from './pages/customer/CustomerDetail.jsx';
import CreateCustomer from './pages/customer/CreateCustomer.jsx';
import FinanceList from './pages/finance/FinanceList.jsx';
import FinanceDetail from './pages/finance/FinanceDetail.jsx';
import CreateFinance from './pages/finance/CreateFinance.jsx';
import PurchaseList from './pages/purchase/PurchaseList.jsx';
import PurchaseDetail from './pages/purchase/PurchaseDetail.jsx';
import CreatePurchase from './pages/purchase/CreatePurchase.jsx';
import PlatformIntegration from './pages/platform-integration/PlatformIntegration.jsx';
import PlatformIntegrationDetail from './pages/platform-integration/PlatformIntegrationDetail.jsx';
import CreatePlatformIntegration from './pages/platform-integration/CreatePlatformIntegration.jsx';

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

// 创建一个带布局的路由包装组件
const LayoutWrapper = () => (
  <AppLayout>
    <Outlet />
  </AppLayout>
);

const AppRoutes = () => {
  return (
    <Routes>
      {/* 公共路由 */}
      <Route path="/login" element={<Login />} />
      
      {/* 需要认证的路由 */}
      <Route element={<AuthGuard />}>
        <Route element={<LayoutWrapper />}>
          <Route index element={<Home />} />
          <Route path="orders" element={<OrderList />} />
          <Route path="orders/:id" element={<OrderDetail />} />
          <Route path="orders/create" element={<CreateOrder />} />
          <Route path="logistics" element={<LogisticsList />} />
          <Route path="logistics/:id" element={<LogisticsDetail />} />
          <Route path="logistics/create" element={<CreateLogistics />} />
          <Route path="products" element={<ProductList />} />
          <Route path="products/:id" element={<ProductDetail />} />
          <Route path="products/create" element={<CreateProduct />} />
          <Route path="warehouses" element={<WarehouseList />} />
          <Route path="warehouses/:id" element={<WarehouseDetail />} />
          <Route path="warehouses/create" element={<CreateWarehouse />} />
          <Route path="customers" element={<CustomerList />} />
          <Route path="customers/:id" element={<CustomerDetail />} />
          <Route path="customers/create" element={<CreateCustomer />} />
          <Route path="finance" element={<FinanceList />} />
          <Route path="finance/:id" element={<FinanceDetail />} />
          <Route path="finance/create" element={<CreateFinance />} />
          <Route path="purchase" element={<PurchaseList />} />
          <Route path="purchase/:id" element={<PurchaseDetail />} />
          <Route path="purchase/create" element={<CreatePurchase />} />
          <Route path="platform-integration" element={<PlatformIntegration />} />
          <Route path="platform-integration/:id" element={<PlatformIntegrationDetail />} />
          <Route path="platform-integration/create" element={<CreatePlatformIntegration />} />
          
          {/* 租户管理 */}
          <Route path="tenants" element={<TenantList />} />
          <Route path="tenants/create" element={<CreateTenant />} />
          <Route path="tenants/:id" element={<TenantDetail />} />
          
          {/* 用户管理 */}
          <Route path="users" element={<UserList />} />
          <Route path="users/create" element={<CreateUser />} />
          <Route path="users/:id" element={<UserDetail />} />
        </Route>
      </Route>
      
      {/* 平台管理员路由 */}
      <Route element={<PlatformAdminGuard />}>
        <Route element={<LayoutWrapper />}>
          <Route path="platform/dashboard" element={<PlatformDashboard />} />
          <Route path="platform/config" element={<SystemConfig />} />
        </Route>
      </Route>
      
      {/* 租户管理员路由 */}
      <Route element={<TenantAdminGuard />}>
        <Route element={<LayoutWrapper />}>
          {/* 租户管理员专属路由 */}
        </Route>
      </Route>
    </Routes>
  );
};

export default AppRoutes;