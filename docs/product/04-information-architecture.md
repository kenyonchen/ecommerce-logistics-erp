# 04 · 信息架构与导航地图

> 定义"用户在哪里做什么"。如图所示包含：**双前端定位 → 站点地图 → 路由表 → 页面清单 → 页面↔接口映射 → 角色可见范围 → 已知 IA 问题**。
> 代码基线：`main@bfbce09`；核对日期 2026-01-25。

---

## 1. 双前端定位

| 前端 | 路径 | 端口 | 目标用户 | 定位 | 当前状态 |
|---|---|---|---|---|---|
| **user-web** | `frontend/user-web` | ~~5173~~ → **5120**（🔄 2026-10-10：`vite.config.js:9` 已写死 `server.port`） | 卖家团队全员 | 业务主工作台，承载全部 9 个业务上下文 + 租户/用户管理 | ~~骨架完整，33 个页面；**7 个页面真正联网**~~ → 🔄 2026-10-10：`pages/` 下 **46 个页面文件全部接真实接口**（见 [known-issues §16](../qa/known-issues.md) 对 ISS-015 的收口） |
| **admin-web** | `frontend/admin-web` | ~~5174~~ → **5110**（🔄 2026-10-10：`vite.config.js:9` 已写死） | 平台运营方 | SaaS 后台：租户开户、系统参数、平台监控 | ~~4 个页面，**全部 mock**，无登录页、无鉴权~~ → 🔄 2026-10-10：**6 个页面全部联网**（含登录页与 `AdminGuard` 的 `PLATFORM_ADMIN` 路由守卫），`platformApi` 硬编码桩已删（ISS-026 已修） |

> ⚠️ 当前 admin-web 的 4 个页面与 user-web 的同名文件内容高度重复（属于复制产物），且数据来自 `admin-web/src/services/api.js` 里的硬编码模拟数据（含假 JWT `mock-jwt-token`）。**短期建议**：不要把 admin-web 当作独立产品对外演示；**中期建议**：明确两者边界后重构公共样式与 services（见 [`../adr/0005-dual-frontend-split.md`](../adr/0005-dual-frontend-split.md)）。

---

## 2. 站点地图（user-web）

```
/login                              登录
└── 需登录（AuthGuard + 侧边栏布局）
    ├── /                           首页 · 工作台
    ├── /orders                     订单列表
    │   ├── /orders/:id             订单详情
    │   └── /orders/create          创建订单
    ├── /logistics                  物流单列表
    │   ├── /logistics/:id          物流单详情
    │   └── /logistics/create       创建物流单
    ├── /products                   产品列表
    │   ├── /products/:id           产品详情
    │   └── /products/create        创建产品
    ├── /warehouses                 仓库列表
    │   ├── /warehouses/:id         仓库详情
    │   └── /warehouses/create      创建仓库
    ├── /customers                  客户列表
    │   ├── /customers/:id          客户详情
    │   └── /customers/create       创建客户
    ├── /finance                    财务流水列表
    │   ├── /finance/:id            流水详情
    │   └── /finance/create         创建流水
    ├── /purchase                   采购单列表
    │   ├── /purchase/:id           采购单详情
    │   └── /purchase/create        创建采购单
    ├── /platform-integration       平台集成列表
    │   ├── /platform-integration/:id     集成详情
    │   └── /platform-integration/create  创建集成
    ├── /tenants                    租户列表        （无菜单入口）
    │   ├── /tenants/:id            租户详情        （无菜单入口）
    │   └── /tenants/create         创建租户        （无菜单入口）
    └── /users                      用户列表        （无菜单入口）
        ├── /users/:id              用户详情        （无菜单入口）
        └── /users/create           创建用户        （无菜单入口）

└── 平台管理员（PlatformAdminGuard）                 （无菜单入口）
    ├── /platform/dashboard         平台仪表盘
    └── /platform/config            系统配置

└── 租户管理员（TenantAdminGuard）                    路由容器为空，无子路由
```

**admin-web 站点地图**

```
/ 及 /platform/dashboard   平台仪表盘（mock）
/platform/config           系统配置（mock）
/tenants                   租户列表（mock）
/users                     用户列表（mock）
```

> 🔄 **2026-01-25 逐页复测（admin-web 共 5 个 `.jsx`）**：上表的「mock」标注已部分过期。实测 `Login.jsx`（1 个 `*Api` 调用）、`TenantList.jsx`（4 个）、`UserList.jsx`（6 个）**已走真实接口**；`PlatformDashboard.jsx` 无 `services/api` import（纯写死）；`SystemConfig.jsx` **有 import 但 0 个调用点**。合计 **3 真 / 2 假**，与 ISS-026 口径一致。另注意 admin-web 的 `platformApi`（`services/api.js:116-143`）是**硬编码桩**，其 `saveSystemConfig`（`:137`）只 `console.log`（`:138`）——它和 user-web 那个"空对象 `{}`"的 `platformApi` 形态不同，别混为一谈。

---

## 3. 路由表（user-web，完整）

| # | 路径 | 页面组件 | 模块 | 守卫 | 侧边栏菜单 | 数据来源 |
|---|---|---|---|---|---|---|
| 1 | `/login` | `pages/auth/Login.jsx` | 认证 | 无 | — | ✅ 真实接口 |
| 2 | `/` | `pages/home/Home.jsx` | 首页 | AuthGuard | ✅ 首页 | ⚪ 写死常量 |
| 3 | `/orders` | `pages/order/OrderList.jsx` | 订单 | AuthGuard | ✅ 订单管理 | ~~⚪ ⚪ mock~~ → ✅ 真实接口 |
| 4 | `/orders/:id` | `pages/order/OrderDetail.jsx` | 订单 | AuthGuard | — | ~~⚪ ⚪ mock~~ → ✅ 真实接口 |
| 5 | `/orders/create` | `pages/order/CreateOrder.jsx` | 订单 | AuthGuard | — | ✅ 真实接口 |
| 6 | `/logistics` | `pages/logistics/LogisticsList.jsx` | 物流 | AuthGuard | ✅ 物流管理 | ~~⚪ ⚪ mock~~ → ✅ 真实接口 |
| 7 | `/logistics/:id` | `pages/logistics/LogisticsDetail.jsx` | 物流 | AuthGuard | — | ~~⚪ ⚪ mock~~ → ✅ 真实接口 |
| 8 | `/logistics/create` | `pages/logistics/CreateLogistics.jsx` | 物流 | AuthGuard | — | ~~⚪ ⚪ 未提交请求~~ → ✅ 真实接口 |
| 9 | `/products` | `pages/product/ProductList.jsx` | 产品 | AuthGuard | ✅ 产品管理 | ~~⚪ ⚪ mock~~ → ✅ 真实接口 |
| 10 | `/products/:id` | `pages/product/ProductDetail.jsx` | 产品 | AuthGuard | — | ~~⚪ ⚪ mock~~ → ✅ 真实接口 |
| 11 | `/products/create` | `pages/product/CreateProduct.jsx` | 产品 | AuthGuard | — | ~~⚪ 未提交请求~~ → ✅ 真实接口（自身不 import，委托已接线的 `ProductForm.jsx`） |
| 12 | `/warehouses` | `pages/warehouse/WarehouseList.jsx` | 仓储 | AuthGuard | ✅ 仓储管理 | ~~⚪ ⚪ mock~~ → ✅ 真实接口 |
| 13 | `/warehouses/:id` | `pages/warehouse/WarehouseDetail.jsx` | 仓储 | AuthGuard | — | ~~⚪ ⚪ mock~~ → ✅ 真实接口 |
| 14 | `/warehouses/create` | `pages/warehouse/CreateWarehouse.jsx` | 仓储 | AuthGuard | — | ~~⚪ ⚪ 未提交请求~~ → ✅ 真实接口 |
| 15 | `/customers` | `pages/customer/CustomerList.jsx` | 客户 | AuthGuard | ✅ 客户管理 | ~~⚪ ⚪ mock~~ → ✅ 真实接口 |
| 16 | `/customers/:id` | `pages/customer/CustomerDetail.jsx` | 客户 | AuthGuard | — | ~~⚪ ⚪ mock~~ → ✅ 真实接口 |
| 17 | `/customers/create` | `pages/customer/CreateCustomer.jsx` | 客户 | AuthGuard | — | ~~⚪ ⚪ 未提交请求~~ → ✅ 真实接口 |
| 18 | `/finance` | `pages/finance/FinanceList.jsx` | 财务 | AuthGuard | ✅ 财务管理 | ~~⚪ ⚪ mock~~ → ✅ 真实接口 |
| 19 | `/finance/:id` | `pages/finance/FinanceDetail.jsx` | 财务 | AuthGuard | — | ~~⚪ ⚪ mock~~ → ✅ 真实接口 |
| 20 | `/finance/create` | `pages/finance/CreateFinance.jsx` | 财务 | AuthGuard | — | ~~⚪ ⚪ 未提交请求~~ → ✅ 真实接口 |
| 21 | `/purchase` | `pages/purchase/PurchaseList.jsx` | 采购 | AuthGuard | ✅ 采购管理 | ~~⚪ ⚪ mock~~ → ✅ 真实接口 |
| 22 | `/purchase/:id` | `pages/purchase/PurchaseDetail.jsx` | 采购 | AuthGuard | — | ~~⚪ ⚪ mock~~ → ✅ 真实接口 |
| 23 | `/purchase/create` | `pages/purchase/CreatePurchase.jsx` | 采购 | AuthGuard | — | ~~⚪ ⚪ 未提交请求~~ → ✅ 真实接口 |
| 24 | `/platform-integration` | `pages/platform-integration/PlatformIntegration.jsx` | 平台集成 | AuthGuard | ✅ 平台集成 | ⚪ mock |
| 25 | `/platform-integration/:id` | `PlatformIntegrationDetail.jsx` | 平台集成 | AuthGuard | — | ~~⚪ ⚪ mock~~ → ✅ 真实接口 |
| 26 | `/platform-integration/create` | `CreatePlatformIntegration.jsx` | 平台集成 | AuthGuard | — | ~~⚪ ⚪ 未提交请求~~ → ✅ 真实接口 |
| 27 | `/tenants` | `pages/tenant/TenantList.jsx` | 租户 | AuthGuard | ❌ 无菜单 | ✅ 真实接口 |
| 28 | `/tenants/:id` | `pages/tenant/TenantDetail.jsx` | 租户 | AuthGuard | ❌ 无菜单 | ✅ 真实接口 |
| 29 | `/tenants/create` | `pages/tenant/CreateTenant.jsx` | 租户 | AuthGuard | ❌ 无菜单 | ✅ 真实接口 |
| 30 | `/users` | `pages/user/UserList.jsx` | 用户 | AuthGuard | ❌ 无菜单 | ✅ 真实接口 |
| 31 | `/users/:id` | `pages/user/UserDetail.jsx` | 用户 | AuthGuard | ❌ 无菜单 | ✅ 真实接口 |
| 32 | `/users/create` | `pages/user/CreateUser.jsx` | 用户 | AuthGuard | ❌ 无菜单 | ✅ 真实接口 |
| 33 | `/platform/dashboard` | `pages/platform/PlatformDashboard.jsx` | 平台运营 | PlatformAdminGuard | ❌ 无菜单 | ⚪ mock |
| 34 | `/platform/config` | `pages/platform/SystemConfig.jsx` | 平台运营 | PlatformAdminGuard | ❌ 无菜单 | ⚪ mock |

> 数据来源判定：✅ = 调用 `services/api.js` 真实请求 ｜ ⚪ = 页面内 mock / 未发起请求。
> 🔄 **2026-01-25 逐页复测后的订正**：本表「数据来源」列原先大量标 ⚪，现已按实测翻正——判定方法是「该 `.jsx` 是否 `import services/api` 且存在 `*Api.*` 调用点」。翻正 **22 页**；仍为 ⚪ 的只有 3 页：`home/Home.jsx`（无 import，写死 3 个统计数字）、`platform/PlatformDashboard.jsx`（无 import）、`platform/SystemConfig.jsx`（**有 import 但 0 个调用点**，`:53-54` 保存只 `console.log` + `message.success`，且它 import 的 `platformApi` 本身是空壳）。`product/CreateProduct.jsx`、`EditProduct.jsx` 自身不 import，委托给已接线的 `pages/product/ProductForm.jsx`，因此实际是 ✅。
> ⚠️ 两处本表未覆盖的漂移：① 表只列 34 个路径，`routes.jsx` 实测 **42 个 `path`**——`orders|logistics|products|customers|finance|warehouses|purchases|platform` 的 `/import` 页与 `/logistics/channels`、`/warehouses/shop` 是后加的，未进表；② 「侧边栏菜单」列对 `/tenants`、`/users`、`/roles` 标「❌ 无菜单」已过期，三项现均为菜单项（`Layout.jsx:182`、`:172`、`:177`），其中只有 `/tenants` 带角色门控。

---

## 4. 页面 ↔ 后端接口映射

| 页面 | 期望调用的接口 | 实际调用情况 |
|---|---|---|
| `Login.jsx` | `POST /api/auth/login` | ✅ 已接，登录后写入 `localStorage.token` |
| `Home.jsx` | 无对应统计接口 | ⚪ 写死常量（订单/客户/仓库 3 个数字） |
| `OrderList.jsx` | ~~`GET /api/orders`（**不存在**）~~ → ✅ 已存在（`OrderController.java:63-81`） | ~~⚪ 读 mock；`orderApi` 只提供 `getOrdersByCustomer`~~ → ✅ 2026-01-25 复测：`api.js:137` 有 `getOrders(params)`，页面按参数分页查询，mock 数组已删 |
| `OrderDetail.jsx` | `GET /api/orders/{id}` | ~~⚪ 读 mock~~ → ✅ 2026-01-25 复测：4 个 `orderApi.*` 调用点，已联网 |
| `CreateOrder.jsx` | `POST /api/orders` | ✅ 已接，但成功后无法在列表看到结果 |
| `LogisticsList.jsx` | `GET /api/logistics` | ⚪ `logisticsApi` 是**空对象** |
| `LogisticsDetail.jsx` | `GET /api/logistics/{id}` | ⚪ 同上 |
| `ProductList.jsx` | `GET /api/products`（列表，**不存在**） | ⚪ `productApi` 空对象 |
| `WarehouseList.jsx` | `GET /api/warehouses` | ⚪ `warehouseApi` 空对象 |
| `CustomerList.jsx` | `GET /api/customers`（列表，**不存在**） | ⚪ `customerApi` 空对象 |
| `FinanceList.jsx` | `GET /api/finance/transactions`（全量列表，**不存在**） | ⚪ `financeApi` 空对象 |
| `PurchaseList.jsx` | `GET /api/purchases` | ⚪ `purchaseApi` 空对象 |
| `PlatformIntegration.jsx` | `GET /api/platform-integrations` | ⚪ `platformIntegrationApi` 空对象 |
| `Tenant*`（3 页） | `/api/tenants/**` | ✅ `tenantApi` 完整（9 个方法） |
| `User*`（3 页） | `/api/users/**`、`/api/roles` | ✅ `userApi`（10）、`roleApi`（8） |
| `PlatformDashboard/SystemConfig` | 无 | ⚪ `platformApi` 空对象 |

> ~~**前端去 mock 的前置条件**：后端必须先补齐列表/分页/筛选接口——这是 v0.1 的 P0 任务，见 [`07-roadmap.md`](./07-roadmap.md)。~~
> 🔄 **2026-01-25 复测：本表整体已过期，请勿据其判断现状。** 表中所有「`xxxApi` 是**空对象**」与「接口**不存在**」的判定都不成立：`services/api.js` 现有 **21 个 api 模块 / 194 个方法**，唯一空壳是 `platformApi`（`:464-466`）。逐条订正：
> - `GET /api/orders`（分页条件列表）**已存在**（`OrderController:64`），`orderApi` 从 4 个方法扩到 **10 个**（`getOrders` / `getOrderByNumber` / `cancelOrder` / `deleteOrder` / `getStatuses` 等），`OrderList.jsx`、`OrderDetail.jsx` 已改真实取数 → 行 125/126 的「⚪ 读 mock」作废；
> - `logisticsApi`（4 方法）、`productApi`、`warehouseApi`、`customerApi`、`financeApi`、`purchaseApi`、`platformIntegrationApi` **全部已实现**（不是空对象），对应列表页已接真实接口 → 行 128-135 的「⚪ 空对象」全部作废。逐模块行数见 [tech/frontend-architecture.md §api.js 清单](../tech/frontend-architecture.md)；
> - 行 127「成功后无法在列表看到结果」已随列表接口落地解决；
> - 行 138 仍然成立且需精确化：`platformApi` 是空壳，`PlatformDashboard.jsx`/`SystemConfig.jsx` 因此无数据可取（后者 `:53-54` 的保存只 `console.log`）。
> ⚠️ 于是原先那句「**前置条件是后端先补列表/分页/筛选接口**」的 P0 判断已完成，**当前的真实阻塞点换成了另两件事**：读路径无租户过滤（ISS-050，所以列表接口能返回别的租户的数据）与 `platformApi`/统计接口缺失（首页与平台页仍无数据源）。

---

## 5. 全局交互框架（设计约定）

| 元素 | 约定 | 实现位置 |
|---|---|---|
| 主框架 | Header（Logo + 用户信息 + 退出） + Sider（折叠菜单） + Content | `components/Layout.jsx` |
| 菜单 | ~~9 项静态数组~~ → ~~2026-01-25 实测 19 个 key 平铺~~ → 🔄 **2026-04-29 已按业务域分组**：菜单定义抽离为单一数据源 `constants/menus.js` 的 `MENU_GROUPS`，**19 个平铺项收敛为 6 个一级分组**——**首页**（顶层，无子项）／**交易管理**（订单管理·订单导入·客户管理·客户导入）／**物流履约**（物流管理·物流导入·物流渠道）／**商品与仓储**（产品管理·商品导入·仓储管理·店铺仓库对接）／**采购与财务**（采购管理·财务管理·财务导入）／**平台与设置**（平台集成·用户管理·角色管理·租户管理）。一级分组默认收起，按 `location.pathname` 自动展开所属分组；二级菜单不再逐项配图标（原先 `ImportOutlined` 重复 5 次） | 配置与角色门控：`constants/menus.js`（`MENU_GROUPS` / `buildMenuItems` / `findSelectedKeys` / `findOpenGroupKeys`）<br>受控展开态：`components/Layout.jsx:62-70`（渲染期派生，不用 effect+setState）、`:89-90`<br>`Menu` 渲染：`Layout.jsx:139-144`<br>⚠️ 门控**仍不一致**：只有 `/tenants` 声明了 `roles: ['PLATFORM_ADMIN']`（`menus.js:70`），`/users` 与 `/roles` 无条件显示 → 非平台管理员看得见"用户管理/角色管理"菜单，点进去接口才 403。要么三项一起门控，要么都交给路由级守卫 |
| 响应式 | 视口 < 768 折叠并宽 80；< 1024 宽 180；桌面宽 200 | `Layout.jsx:28-54` |
| 路由守卫 | `AuthGuard` / `PlatformAdminGuard` / `TenantAdminGuard` | `components/Auth/AuthGuard.jsx` |
| 登录态 | `AuthContext`：localStorage 中存 `token`、`username` | `context/AuthContext.jsx` |
| 请求封装 | axios，`baseURL=http://localhost:8082/api`，超时 10s，拦截器注入 Bearer | `services/api.js` |
| 响应解包 | `code === "200"` 时返回 `data`，否则 `message.error` 并抛错 | `services/api.js:38-53`（`:45-46` 解包、`:49-51` 报错）。<br>🟡 **2026-01-25 补测新分支**：`:41-42` 对 `responseType === 'blob'` 或 `data instanceof Blob` **直接透传不解包**（导入模板/面单下载所需）——写文档时"只有一条解包路径"的说法要带上这个例外 |
| 401 处理 | `window.location.href = '/login'`（整页跳转，非 SPA 路由跳转） | `services/api.js:63-66`。<br>🔴 **实测该分支不可达**：`:61` 先判 `errorData.code`，而 Spring Security 过滤器链上的拒绝返回的是 **403 + 0 字节 body**（无 `code`、且状态不是 401，`SecurityConfig` 无 `exceptionHandling()`），于是 `errorData.code` 为 `undefined` → 落 `:63` 的 401 判断也永远不成立 → 用户看到的是「网络错误，请检查网络连接」。ISS-017/ISS-032 |
| 错误边界 | `services/errorHandler.jsx` 的 `ErrorBoundary`（另有重复实现在 `App.jsx`） | 两处 |
| 反馈组件 | Ant Design 6 `Table` / `Form` / `Modal` / `message` / `Descriptions` / `Steps` / `Tag` | 全域 |

**尚不存在的基础设施**（产品层面需知道的限制）：

| 能力 | 现状 | 影响 |
|---|---|---|
| 国际化 i18n | ❌ 无 i18next / 无 `ConfigProvider` locale | 文案全硬编码中文，**无法做英文站点** |
| 主题/暗色模式 | 🟡 **2026-04-29 出现首个 CSS 覆盖**：绝大多数颜色仍内联写死，未引入 antd `ConfigProvider` token 层；唯一的例外是 `index.css` 末尾用 `!important` 覆盖 antd `Sider` 折叠条的 `triggerBg`/`triggerColor`——AntD 默认是深蓝底白箭头（`triggerBg: '#002140'`，定义在 `node_modules/antd/es/layout/style/index.js:100`），与本项目白色顶栏/侧栏不统一，改为白底深箭头 + hover 主蓝。CSS 必须 `!important` 才能压过 antd CSS-in-JS 的 hash 前缀 | 夜间模式/品牌定制仍需引入 token 体系；届时应把这类覆盖收敛回 token，避免 CSS 与主题互相打架 |
| 状态管理库 | ❌ 仅 React Context | 跨页面共享态、缓存列表数据较难 |
| 图表库 | ❌ 无 ECharts / Recharts | 看板只能用 `Statistic` + `Progress` 拼凑 |
| 路由级角色控制 | ~~⚠️ 守卫存在但角色校验被注释 / 任何登录用户可访问所有路由~~ → 🟡 **2026-01-25 复测：角色校验已实现**（`AuthGuard.jsx:34` 判 `requiredRoles`、`:35-43` 渲染 403；`Layout.jsx:181` 按 `hasRole('PLATFORM_ADMIN')` 过滤菜单），但**只有 `/platform/dashboard`、`/platform/config` 在 `PlatformAdminGuard` 下**（`routes.jsx:127-132`），`/tenants`、`/users`、`/roles` 仍挂裸 `AuthGuard`（`:112-122`），`TenantAdminGuard` 组内零路由（`:135-139`）| 非平台管理员直接输 URL 仍能进租户/用户/角色页面，只在点接口时被后端 `@PreAuthorize` 挡成 403（且是空 body，ISS-058）；见 [spec/tenant-and-iam.md BR-11](../spec/tenant-and-iam.md) |
| 动态菜单/路由表驱动 | 🟡 **2026-04-29：菜单侧已收敛，路由侧仍两份维护** | ~~已产生漂移：`/tenants`、`/users`、`/platform/*` 有路由无菜单~~ → **2026-01-25 复测**：`/tenants`、`/users`、`/roles` 已有菜单项；**仍有路由无菜单**的是 `/platform/dashboard`、`/platform/config`（`routes.jsx:129-130`）与全部详情页/创建页（表驱动时属正常）。<br>🔄 **本次改动**：菜单从 `Layout.jsx` 组件内硬编码数组抽到 `constants/menus.js`，成为单一数据源并在文件头约定"二级菜单 key 必须与 `routes.jsx` 的 path 严格一致"，选中/展开均改为按 pathname 反查。但 `routes.jsx` 仍是手写的 42 个 `<Route>`，**两者的一致性目前只靠注释约定、无编译期校验**——写错 key 只会静默失去高亮与展开，不会报错。漂移方向仍值得注意：**有菜单项但路由组里无对应守卫**（`/users`、`/roles`） |

---

## 6. 角色可见范围（目标态）

> 当前系统**不校验角色**，下表为目标态，实现依赖 [`../spec/tenant-and-iam.md`](../spec/tenant-and-iam.md) 中的权限模型落地。

| 菜单 | 运营专员 | 仓库主管 | 采购 | 财务 | 客服 | 老板 | 平台 Admin |
|---|---|---|---|---|---|---|---|
| 首页 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | — |
| 订单管理 | ✅ | ✅（只读发货） | — | ✅ | ✅ | ✅（只读） | — |
| 物流管理 | ✅ | ✅ | — | ✅ | ✅（只读） | — | — |
| 仓储管理 | — | ✅ | ✅（只读） | — | — | — | — |
| 产品管理 | ✅ | ✅（只读） | ✅ | — | — | — | — |
| 采购管理 | — | — | ✅ | ✅ | — | ✅（审批） | — |
| 财务管理 | — | — | — | ✅ | — | ✅ | — |
| 客户管理 | — | — | — | — | ✅ | — | — |
| 平台集成 | ✅ | — | — | — | — | — | — |
| 租户/用户/角色 | — | — | — | — | — | — | ✅ |
| 系统配置 | — | — | — | — | — | — | ✅ |

---

## 7. 已知 IA / 交互问题清单

| # | 问题 | 用户可感知表现 | 建议 | 优先级 |
|---|---|---|---|---|
| IA-01 | 菜单与路由双份维护且不一致 | ~~租户/用户/平台配置页只能手动输 URL 才能进~~ → 仍有路由无菜单（`/platform/dashboard`、`/platform/config`）；写错菜单 key 会静默失去高亮 | 🟡 **部分完成（2026-04-29）**：菜单已抽为单一数据源 `constants/menus.js` 并与路由 path 建立 key 约定；剩余工作是把 `routes.jsx` 的 42 个手写 `<Route>` 也改为由该配置驱动，并加一条启动期断言校验 key 与 path 一致 | P1 |
| IA-02 | 所有"创建页"是独立 URL 而非弹窗 | 创建完成后需手动返回列表，且列表不刷新 | 统一为列表页 + Modal 表单，保存后刷新列表 | P2 |
| IA-03 | 详情页缺少"返回列表"与面包屑 | 用户容易迷失 | 补面包屑与返回按钮 | P2 |
| IA-04 | 无全局 loading / 空态 / 错误态规范 | 各页面自行处理，表现不一致 | 抽出统一 `PageContainer` + 空/错/加载三态组件 | P2 |
| IA-05 | 401 用整页跳转（丢失当前上下文） | 操作时登录过期会被强制踢回登录页，未保存数据丢失 | 改为路由跳转 + 登录后回跳原路径 | P2 |
| IA-06 | 列表无分页、无筛选、无列设置 | 数据上千条后不可用 | 统一 `Table` 分页 + 查询表单 + 重置 | P1 |
| ~~IA-07~~ → 🟡 部分完成 | ~~`admin-web` 无登录页~~ → **登录页已有**（`admin-web/src/pages/Login.jsx:18` 走 `authApi.login`），且有 `AdminGuard`（`routes.jsx` 要求 `PLATFORM_ADMIN`） | ~~直接暴露 4 个 mock 页面~~ → 2026-01-25 复测：5 页里 **3 真 2 假**（真：`Login`/`tenant/TenantList`/`user/UserList`；假：`platform/PlatformDashboard`/`platform/SystemConfig`） | ~~补登录 + 守卫~~ 已补；剩下的是把平台两页接真接口（ISS-026） | P1 |
| IA-08 | `index.html` 的 `<title>` 仍为脚手架默认值 | 浏览器标签显示 "frontend" | 改为"跨境大卖 ERP" | P3 |
| IA-09 | 移动端仅做侧栏宽度自适应 | 手机端表格横向滚动体验差 | 关键列表提供卡片视图 | P3 |

详细缺陷登记见 [`../qa/known-issues.md`](../qa/known-issues.md)。
