import React from 'react';
import { Routes, Route, Outlet } from 'react-router-dom';
import AppLayout from './components/Layout.jsx';

// 认证相关组件
import { AuthGuard, PlatformAdminGuard, PermissionGuard } from './components/Auth/AuthGuard.jsx';
import Login from './pages/auth/Login.jsx';

// 页面组件
import Home from './pages/home/Home.jsx';
import OrderList from './pages/order/OrderList.jsx';
import OrderDetail from './pages/order/OrderDetail.jsx';
import CreateOrder from './pages/order/CreateOrder.jsx';
import OrderImport from './pages/order/OrderImport.jsx';
import LogisticsList from './pages/logistics/LogisticsList.jsx';
import LogisticsDetail from './pages/logistics/LogisticsDetail.jsx';
import CreateLogistics from './pages/logistics/CreateLogistics.jsx';
import LogisticsImport from './pages/logistics/LogisticsImport.jsx';
import LogisticsChannels from './pages/logistics/LogisticsChannels.jsx';
import ProductList from './pages/product/ProductList.jsx';
import ProductDetail from './pages/product/ProductDetail.jsx';
import CreateProduct from './pages/product/CreateProduct.jsx';
import EditProduct from './pages/product/EditProduct.jsx';
import ProductImport from './pages/product/ProductImport.jsx';
import WarehouseList from './pages/warehouse/WarehouseList.jsx';
import WarehouseDetail from './pages/warehouse/WarehouseDetail.jsx';
import CreateWarehouse from './pages/warehouse/CreateWarehouse.jsx';
import ShopWarehouseSync from './pages/warehouse/ShopWarehouseSync.jsx';
import CustomerList from './pages/customer/CustomerList.jsx';
import CustomerDetail from './pages/customer/CustomerDetail.jsx';
import CustomerImport from './pages/customer/CustomerImport.jsx';
import CreateCustomer from './pages/customer/CreateCustomer.jsx';
import FinanceList from './pages/finance/FinanceList.jsx';
import FinanceDetail from './pages/finance/FinanceDetail.jsx';
import CreateFinance from './pages/finance/CreateFinance.jsx';
import FinanceImport from './pages/finance/FinanceImport.jsx';
import PurchaseList from './pages/purchase/PurchaseList.jsx';
import PurchaseDetail from './pages/purchase/PurchaseDetail.jsx';
import CreatePurchase from './pages/purchase/CreatePurchase.jsx';
import PlatformIntegration from './pages/platform-integration/PlatformIntegration.jsx';
import PlatformIntegrationDetail from './pages/platform-integration/PlatformIntegrationDetail.jsx';
import CreatePlatformIntegration from './pages/platform-integration/CreatePlatformIntegration.jsx';
import ShopList from './pages/shop/ShopList.jsx';

// 租户管理页面
import TenantList from './pages/tenant/TenantList.jsx';
import CreateTenant from './pages/tenant/CreateTenant.jsx';
import TenantDetail from './pages/tenant/TenantDetail.jsx';

// 用户管理页面
import UserList from './pages/user/UserList.jsx';
import CreateUser from './pages/user/CreateUser.jsx';
import UserDetail from './pages/user/UserDetail.jsx';

// 角色管理页面
import RoleList from './pages/role/RoleList.jsx';

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

          {/* 交易管理：列表/详情按 :read 收敛，建单与导入各按自己的权限点 */}
          <Route element={<PermissionGuard permission="order:read" />}>
            <Route path="orders" element={<OrderList />} />
            <Route path="orders/:id" element={<OrderDetail />} />
          </Route>
          <Route element={<PermissionGuard permission="order:write" />}>
            <Route path="orders/create" element={<CreateOrder />} />
          </Route>
          <Route element={<PermissionGuard permission="order:import" />}>
            <Route path="orders/import" element={<OrderImport />} />
          </Route>
          <Route element={<PermissionGuard permission="customer:read" />}>
            <Route path="customers" element={<CustomerList />} />
            <Route path="customers/:id" element={<CustomerDetail />} />
          </Route>
          <Route element={<PermissionGuard permission="customer:write" />}>
            <Route path="customers/create" element={<CreateCustomer />} />
          </Route>
          <Route element={<PermissionGuard permission="customer:import" />}>
            <Route path="customers/import" element={<CustomerImport />} />
          </Route>

          {/* 物流履约 */}
          <Route element={<PermissionGuard permission="logistics:read" />}>
            <Route path="logistics" element={<LogisticsList />} />
            <Route path="logistics/:id" element={<LogisticsDetail />} />
            <Route path="logistics/channels" element={<LogisticsChannels />} />
          </Route>
          <Route element={<PermissionGuard permission="logistics:write" />}>
            <Route path="logistics/create" element={<CreateLogistics />} />
          </Route>
          <Route element={<PermissionGuard permission="logistics:import" />}>
            <Route path="logistics/import" element={<LogisticsImport />} />
          </Route>

          {/* 商品与仓储 */}
          <Route element={<PermissionGuard permission="product:read" />}>
            <Route path="products" element={<ProductList />} />
            <Route path="products/:id" element={<ProductDetail />} />
          </Route>
          <Route element={<PermissionGuard permission="product:write" />}>
            <Route path="products/create" element={<CreateProduct />} />
            <Route path="products/:id/edit" element={<EditProduct />} />
          </Route>
          <Route element={<PermissionGuard permission="product:import" />}>
            <Route path="products/import" element={<ProductImport />} />
          </Route>
          <Route element={<PermissionGuard permission="warehouse:read" />}>
            <Route path="warehouses" element={<WarehouseList />} />
            <Route path="warehouses/:id" element={<WarehouseDetail />} />
            {/* 店铺仓库对接页要读本地仓库列表，入口跟着 warehouse:read 走 */}
            <Route path="warehouses/shop" element={<ShopWarehouseSync />} />
          </Route>
          <Route element={<PermissionGuard permission="warehouse:write" />}>
            <Route path="warehouses/create" element={<CreateWarehouse />} />
          </Route>
          <Route element={<PermissionGuard permission="shop:read" />}>
            <Route path="shops" element={<ShopList />} />
          </Route>

          {/* 采购与财务 */}
          <Route element={<PermissionGuard permission="purchase:read" />}>
            <Route path="purchase" element={<PurchaseList />} />
            <Route path="purchase/:id" element={<PurchaseDetail />} />
          </Route>
          <Route element={<PermissionGuard permission="purchase:write" />}>
            <Route path="purchase/create" element={<CreatePurchase />} />
          </Route>
          <Route element={<PermissionGuard permission="finance:read" />}>
            <Route path="finance" element={<FinanceList />} />
            <Route path="finance/:id" element={<FinanceDetail />} />
          </Route>
          <Route element={<PermissionGuard permission="finance:write" />}>
            <Route path="finance/create" element={<CreateFinance />} />
          </Route>
          <Route element={<PermissionGuard permission="finance:import" />}>
            <Route path="finance/import" element={<FinanceImport />} />
          </Route>

          {/* 平台集成：列表与详情后端对已认证用户开放，建/改/同步才要 platform:manage */}
          <Route path="platform-integration" element={<PlatformIntegration />} />
          <Route path="platform-integration/:id" element={<PlatformIntegrationDetail />} />
          <Route element={<PermissionGuard permission="platform:manage" />}>
            <Route path="platform-integration/create" element={<CreatePlatformIntegration />} />
          </Route>

          {/* 本租户内的子账号与角色 */}
          <Route element={<PermissionGuard permission="user:manage" />}>
            <Route path="users" element={<UserList />} />
            <Route path="users/create" element={<CreateUser />} />
            <Route path="users/:id" element={<UserDetail />} />
          </Route>
          <Route element={<PermissionGuard permission="role:manage" />}>
            <Route path="roles" element={<RoleList />} />
          </Route>

          {/* 租户管理：整段只给平台管理员（与 SecurityConfig 对 /api/tenants/** 的角色要求一致） */}
          <Route element={<PlatformAdminGuard />}>
            <Route path="tenants" element={<TenantList />} />
            <Route path="tenants/create" element={<CreateTenant />} />
            <Route path="tenants/:id" element={<TenantDetail />} />
          </Route>
        </Route>
      </Route>
      
      {/* 平台管理员路由 */}
      <Route element={<PlatformAdminGuard />}>
        <Route element={<LayoutWrapper />}>
          <Route path="platform/dashboard" element={<PlatformDashboard />} />
          <Route path="platform/config" element={<SystemConfig />} />
        </Route>
      </Route>
    </Routes>
  );
};

export default AppRoutes;