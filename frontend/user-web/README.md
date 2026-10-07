# user-web — 卖家业务端

跨境电商 ERP 的用户侧前端，覆盖订单、物流、仓储、产品、采购、财务、客户、平台集成以及租户/用户/角色管理。
后端为同仓库的 `../../backend`（Spring Boot，`http://localhost:8082`）。

## 技术栈

| 项 | 版本 / 说明 |
|---|---|
| React / React DOM | 19.2（函数组件 + Hooks） |
| 构建 | Vite 7.2（`@vitejs/plugin-react` 5.1） |
| UI | Ant Design 6.2 + `@ant-design/icons` 6.1（**未使用 ConfigProvider 主题定制**，取 antd 默认主题 + 行内样式） |
| 路由 | React Router DOM 7.12 |
| HTTP | axios 1.13 |
| 语言 | JavaScript / JSX，**无 TypeScript**（无 `tsconfig.json`） |
| 状态管理 | 仅 React Context（`AuthContext`），无 Redux/Zustand/Mobx |
| 校验 | 仅 antd `Form rules`（required/type），无 yup/zod/react-hook-form |
| i18n / 埋点 / 环境变量 | 均**无**（文案硬编码中文；无 `.env`、未使用 `import.meta.env`） |
| 测试 | **无**（无 vitest/jest/@testing-library，`package.json` 无 `test` 脚本） |

## 运行

```bash
npm install
npm run dev      # http://localhost:5173
npm run build    # 输出 dist/
npm run lint     # eslint . （flat config，覆盖 **/*.{js,jsx}）
```

⚠️ `vite.config.js` 只声明了 `plugins: [react()]`——**没有 `server.port`，也没有 `proxy`**。5173 是默认端口，与 admin-web 的 5174 分工来自端口占用后的自动递增，不是配置结果。
接口地址直接硬编码在 `src/services/api.js:6`（`http://localhost:8082/api`），跨域直连，依赖后端 CORS 白名单（`SecurityConfig.java:31` 仅放行 `http://localhost:5173`）。

## 目录结构

```
src/
├── main.jsx                 # 挂载：AuthProvider + ErrorBoundary
├── App.jsx                  # BrowserRouter > AuthProvider > AppRoutes（+ 第二层 ErrorBoundary）
├── routes.jsx               # 完整路由表（AuthGuard 包裹）
├── context/AuthContext.jsx  # 登录态：localStorage 的 token / username
├── components/
│   ├── Layout.jsx           # AntD Layout + 侧边菜单（9 项）
│   └── Auth/AuthGuard.jsx   # 未登录跳 /login
├── pages/<module>/{List,Detail,Create}.jsx   # 13 个模块目录 / 34 个页面
└── services/
    ├── api.js               # axios 实例 + 拦截器 + 各模块 *Api 对象
    └── errorHandler.jsx     # ErrorHandler + ErrorBoundary + withErrorHandling
```

## 路由与鉴权

| 路径 | 页面 | 保护 |
|---|---|---|
| `/login` | `auth/Login.jsx` | 公开 |
| `/` | `home/Home.jsx` | AuthGuard |
| `/orders`、`/orders/:id`、`/orders/create` | order 三件套 | AuthGuard |
| `/logistics[...]`、`/products`、`/warehouses`、`/customers`、`/finance`、`/purchase`、`/platform-integration` | 各模块 | AuthGuard |
| `/tenants[...]`、`/users[...]` | tenant / user | AuthGuard |
| `/platform/dashboard`、`/platform/config` | platform | `PlatformAdminGuard` |

⚠️ 三点需要知道：
1. `AuthGuard.jsx:33-36` 的**角色检查整段被注释**，`PlatformAdminGuard`/`TenantAdminGuard` 目前只验「已登录」，不验角色。
2. `/tenants`、`/users`、`/platform/*` **不在侧边菜单**（`Layout.jsx:67-113` 只有首页 + 8 个业务模块），只能手敲 URL 进入。
3. `main.jsx` 与 `App.jsx` **各包了一层 `AuthProvider` + `ErrorBoundary`**，Provider 冗余嵌套。

登录态只存 `token` 与 `username` 两个 localStorage 键，**不存 tenantId 与角色**。

## 与后端的对接状态（重点）

`src/services/api.js` 的响应拦截器会解包后端信封：`code === "200"`（**字符串**，与后端 `Result.code` 的 String 类型一致）时返回 `response.data.data`；HTTP 401 会 `window.location.href = '/login'` 硬跳转。

| 状态 | 模块 |
|---|---|
| ✅ 已打通（37 个调用，路径与后端端点逐一对上） | `authApi`(3)、`tenantApi`(9)、`userApi`(11)、`roleApi`(10)、`orderApi`(4) |
| 🟡 页面已打通、但仍有部分逻辑用 mock | `OrderList.jsx`（列表接口后端不存在）、`Home.jsx`（统计卡与最近订单为 `Math.random()`） |
| ⚪ **空壳 api 模块**（`api.js:136-173`，全是 `{}`，注释「根据实际后端API补充」） | `logisticsApi`、`productApi`、`warehouseApi`、`customerApi`、`financeApi`、`purchaseApi`、`platformIntegrationApi`、`platformApi` |
| ⚪ 无有效数据来源 | 34 个页面文件中仅 **9 个**发起真实调用（`Login` + 租户 3 + 用户 3 + `CreateOrder`/`OrderDetail`），其余 **25 个**：List 用 `Array.from(...)+Math.random()`，Detail 用整段 `mockXxx` 常量，Create 走「模拟API调用成功」，`OrderList`/`Home` 虽有 import 但无有效调用 |

`OrderDetail.jsx:130/:155` 处真实调用被注释掉，改走「模拟更新成功 / 模拟取消成功 / 模拟同步成功」。

### 已知缺陷

| # | 问题 | 位置 |
|---|---|---|
| 1 | **token 双重解包**：拦截器已 `return response.data.data`，`AuthContext.login` 又读 `responseData.data?.token` → token 取到 `undefined`，`if (token)` 静默跳过，登录疑似无法写入 localStorage | `services/api.js` 响应拦截器 vs `context/AuthContext.jsx:32` |
| 2 | 订单列表无数据源：后端既无 list-all 也无分页 | 见 [docs/api/api.md §4.8](../../docs/api/api.md) |
| 3 | `errorHandler.jsx` 的 code→文案映射与 `api.js` 的 `ERROR_MESSAGES` **重复定义**，且后端实际只产出 `"200"/"400"/"500"`，语义码分支基本不命中 | `services/errorHandler.jsx` |
| 4 | `GET /api/tenants/**` 恒 403（后端角色判断被架空），租户页面对接后拿不到数据 | [docs/security-findings.md S-02](../../docs/security-findings.md) |

## 下一步

1. 修 #1 的 token 双重解包（阻塞所有需要登录态的联调）。
2. 按 `logistics → warehouse → product → customer → finance → purchase → platformIntegration` 顺序填充空壳 api 模块，逐模块把页面从 mock 切到真实接口。
3. 给 `vite.config.js` 加 `server: { port, proxy: { '/api': 'http://localhost:8082' } }`，把 baseURL 改为 `import.meta.env` 或直接走同源 `/api`，从而摆脱 CORS。
4. 接入 vitest + @testing-library，首批覆盖拦截器解包与 `AuthContext`（方案见 [docs/testing.md §6](../../docs/testing.md)）。
5. 恢复 `AuthGuard` 的角色校验，与后端方法级鉴权同步上线。

## 相关文档

[架构](../../docs/architecture.md) · [接口清单](../../docs/api/api.md) · [测试](../../docs/testing.md) · [部署](../../docs/deployment.md) · [前端拆分决策 ADR-0008](../../docs/adr/0008-frontend-user-admin-split.md)
