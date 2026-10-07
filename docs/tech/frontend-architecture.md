# 前端工程架构与技术规范

> 定位：前端两个应用的**工程技术视角**——构建、目录、路由、API 层契约、认证与状态、组件与样式规范、去 mock 路线。
> 与产品文档分工：站点地图/路由的业务含义在 [product/04-information-architecture.md](../product/04-information-architecture.md)；本文只讲**怎么实现、要实现成什么样**。选型决策见 [ADR-0005](../adr/0005-dual-frontend-split.md)（产品线）与 [ADR-0008](../adr/0008-frontend-user-admin-split.md)（工程线实现差距）。
> 事实基线：2026-01-25 工作树，逐文件核对。凡「未验证」是指需要运行时确认（~~本机 node_modules 未安装，ESLint/构建未实跑~~ → **2026-01-25 更新**：两 app 的 `node_modules/` 均已安装（`frontend/user-web/node_modules`、`frontend/admin-web/node_modules` 实测存在），dev server 已起并做过浏览器联调；仍**未实跑**的是 `npm run lint` 与 `npm run build`（无 `test` script，见 §2）。§8 的运行时结论以浏览器实测为准）。

## 1. 工程形态

```
frontend/
├── user-web/     # 卖家工作台：~~45 个 src 文件、34 个页面、真实联后端~~ **61 个 src 文件（58 个 js/jsx）、45 个页面、42 个已联后端**
└── admin-web/    # 平台运营后台：~~11 个 src 文件、4 个页面、纯 mock~~ **13 个 src 文件（10 个 js/jsx）、5 个页面（3 真 / 2 桩），已接真实 axios**
```

- **两个独立 npm 项目，无 monorepo**：仓库根与 `frontend/` 均无 `package.json`/`pnpm-workspace.yaml`/`turbo.json`/`lerna.json`/`nx.json`（实测）。
- 逐字节相同（`diff` 实测）：`vite.config.js`、`eslint.config.js`、`.gitignore`、`src/assets/react.svg`。`package.json` 唯一差异是 user-web 多一行 `"license"`，**依赖与 scripts 完全一致**。
- 后果：任何基础能力（axios 实例、错误码映射、AuthGuard、Layout）都要写两遍。§9 给出去重复的两条路线。

## 2. 技术栈与构建

| 项 | user-web | admin-web | 备注 |
|---|---|---|---|
| React / React DOM | 19.2.x（`^19.2.0`，lock 锁 19.2.3） | 同 | 两 app 版本一致 |
| Ant Design | `^6.2.0`（lock 6.2.0） | 同 | 全量具名 import，无按需配置；Vite ESM tree-shaking（产物未实测） |
| @ant-design/icons | `^6.1.0` | 同 | |
| axios | `^1.13.2` | `^1.13.2` | ~~⚠️ admin-web 声明了 axios 但 `src` 内 grep **零命中**~~ → **2026-01-25 已用**：`admin-web/src/services/api.js:1` `import axios from 'axios'`、`:5-11` 建实例。⚠️ 但**两 app 各持一份实例与一份拦截器**（user-web `api.js:5-11` / admin-web `api.js:5-11`，同为 `baseURL: http://localhost:8082/api` + `timeout: 10000`），且**行为已分叉**：admin-web 多一条 **403 分支**（`:53-54`「无权限执行该操作（需要平台管理员角色）」）、少一条 **blob 透传分支**（user-web `:41-43`）→ 管理端下载类响应会被当信封解包而损坏。错误码映射表也是两份（`ERROR_MESSAGES`：user-web `:29-35` / admin-web `:26-32`，内容相同） |
| react-router-dom | `^7.12.0` | 同 | 但两 app 用了**不同 API 风格**（§5） |
| Vite / @vitejs/plugin-react | `^7.2.4` / `^5.1.1` | 同 | |
| ESLint | 9.39 flat config + `eslint-plugin-react-hooks` 7 + `react-refresh` | 同 | 规则：`js.configs.recommended` + hooks recommended + vite refresh；自定义只放宽 `no-unused-vars`（`varsIgnorePattern: '^[A-Z_]'`）；`globalIgnores(['dist'])` |
| TypeScript | **无**（全 frontend 无 `tsconfig.json`/`jsconfig.json`） | 无 | 保持 JS + JSDoc；是否引入 TS 属技术选择，需 ADR |
| engines 约束 | **无** | 无 | 建议补 `"engines": { "node": ">=18" }`（Vite 7 要求） |
| scripts | `dev` / `build` / `lint` / `preview` | 同 | ⚠️ **无 `test`**，无任何测试依赖 |
| vite.config.js | 仅 `plugins:[react()]`（7 行） | 逐字节相同 | 无 `server.port`、无 `server.proxy`、无 `build.*`；`test` 配置也不存在 |
| index.html | `<div id="root">` + `/src/main.jsx` | 同 | user-web title 仍是脚手架默认 `frontend` |

**构建侧待办（低成本高收益）**：① 固定 `server.port` 并加 `/api` proxy（同时解决 CORS 与 baseURL 硬编码，见 §4）；② 加 `test` script；③ user-web 的 HTML title 改为产品名。

## 3. 目录结构（实测文件数）

`user-web/src`（~~45 文件~~ **61 文件**，2026-01-25 复测）：

| 目录 | 数量 | 内容 |
|---|---|---|
| `pages/` | ~~34~~ **45** | **14 个模块目录**：`auth`(1)、`home`(1)、`platform`(2)、`role`(1)、`order`(4：List/Detail/Create/Import)、`logistics`(6：List/Detail/Create/Import/Channels/BatchShipModal)、`product`(6：List/Detail/Create/Edit/Form/Import)、`warehouse`(4：List/Detail/Create/ShopWarehouseSync)、`customer`(4)、`finance`(4)、`purchase`(3：List/Detail/Create)、`platform-integration`(3)、`tenant`(3)、`user`(3) |
| `components/` | 2 | `Layout.jsx`、`Auth/AuthGuard.jsx`（⚠️ 45 个页面共享**只有 2 个自建组件**，表格/表单/弹窗全部各页自写，见 §7） |
| `constants/` | **5**（新增，此前未记录） | `customer.js`、`finance.js`、`logistics.js`、`order.js`、`product.js` —— 各模块的状态字典/枚举文案 |
| `context/` | 1 | `AuthContext.jsx` |
| `services/` | 2 | `api.js`（21 模块 / 194 方法）、`errorHandler.jsx` |
| 根级 | 5 | `main.jsx`、`App.jsx`、`routes.jsx`、`index.css`、`App.css` |
| `assets/` | 1 | `react.svg` |

`admin-web/src`（~~11 文件~~ **13 文件**）：`pages/`(**5**：`Login`、`platform/PlatformDashboard`、`platform/SystemConfig`、`tenant/TenantList`、`user/UserList`)、`services/api.js`(143 行，真实 axios)、**`context/AuthContext.jsx`（新增）**、`routes.jsx`（内含 `AdminGuard` `:13-32`）、根级 4、`assets/` 1。~~**无 `components/`、无 `context/`。**~~ → **2026-01-25：`context/` 已建（1 个文件）；`components/` 仍无**，`App.jsx` 里直接写侧边栏，与 user-web 的 `Layout.jsx` 是两份实现。

页面命名约定（现状已一致，保持）：`pages/<module>/{List,Detail,Create}*` 三件套。

## 4. API 层（唯一网络出口）

`user-web/src/services/api.js` 实况：

| 位置 | 事实 |
|---|---|
| `:5-11` | axios 实例，`baseURL: 'http://localhost:8082/api'`（**硬编码**，实测在 `:6`；admin-web 的 `:6` 同值，两端各一份）、`timeout: 10000`、默认 JSON header。全 src 无 `import.meta.env`、无 `.env*` 文件（实测） |
| `:14-26` | 请求拦截器：`localStorage.getItem('token')` → `Authorization: Bearer <token>`（实测 `:17`） |
| ~~`:38-76`~~ | 响应拦截器：成功分支 `:38-53`、失败分支 `:54-79`。`response.data.code === "200"`（**字符串**，实测 `:45`）时 `return response.data.data`；否则查 `ERROR_MESSAGES`（`:29-35`）弹 `message.error` 并 `throw`。<br>✅ **2026-01-25 新增二进制透传**：`:41-43` 判 `response.config?.responseType === 'blob' ｜｜ response.data instanceof Blob` 时直接 `return response.data`，导入模板/面单下载不再被当成信封解包（admin-web 那份拦截器**没有这一分支**，见 §2） |
| ~~`:58-62`~~ **`:63-66`** | HTTP 401 → `window.location.href = '/login'`（硬跳转，丢失 SPA 状态与回跳目标）。<br>🔴 **2026-01-25 实测：这个分支当前不可达**——后端没配 `httpBasic`/`formLogin`/`exceptionHandling`，未认证与坏令牌一律 **403 + 空 body**（不返回 401），所以令牌过期时用户看到的不是"跳登录"而是页面报错。执行顺序上：`errorData.code`（`:61`）先判，而 403 的 body 是 **0 字节**（`errorData` 为空串/`undefined`）⇒ 落到 `:63` 的 401 判断也不成立，再落到 `:67`/`:69` 的 404/500 也都不成立 ⇒ 最终 `errorMessage` 保持默认「网络错误，请检查网络连接」（`:56`），用户只看到一句误导文案。修法见 [security-findings M-06/ISS-017](../security-findings.md)；⚠️ user-web 连 **403 分支都没有**（admin-web 有，`api.js:53-54`）。<br>🟡 **续期链路：半成品**。此前「`AuthContext` 的 `refreshAccessToken` 拿不到 `refreshToken`（登录响应只有 `token`）」的口径**作废且当时就不准确**——`AuthContext` 里根本没有 `refreshAccessToken` 这个函数（实测 grep 零命中）。实况是：后端 `TokenResponse` 确实回传 `refreshToken`（`AuthController.java:128`），前端也确实存了下来（`AuthContext.jsx:77-79`，键名 `:10`），`authApi.refreshToken` 也存在（`api.js:86`）——但**没有任何调用方**（全 src 对 `authApi.refresh` 零命中），所以既不会静默续期、也不会在过期时引导重登。⚠️ 且后端 `:128` 回传的是**同一个旧 refreshToken（不轮换）**，即使接上调用也做不到撤销。 |
| ~~`:79-125` 已实现模块：`authApi`(3)、`tenantApi`(9)、`userApi`(11)、`roleApi`(10)~~<br>~~`:128-133` `orderApi`(4)：仅 byId / byCustomer / create / updateStatus——**无列表与分页方法**~~<br>~~`:136-173` ⚠️ `logisticsApi`、`productApi`、`warehouseApi`、`customerApi`、`financeApi`、`purchaseApi`、`platformIntegrationApi`、`platformApi` 全部是空壳 `{}`~~ → **2026-01-25 逐模块重测：该文件已扩到 21 个 `*Api` 模块 / 194 个方法**（`authApi:83`、`tenantApi:91`、`userApi:104`、`roleApi:119`、`orderApi:133`、`orderImportApi:148`、`logisticsApi:174`、`logisticsChannelApi:198`、`logisticsImportApi:212`、`productApi:240`、`productImportApi:258`、`warehouseApi:283`、`shopWarehouseApi:301`、`warehouseImportApi:311`、`customerApi:336`、`customerImportApi:351`、`financeApi:378`、`financeImportApi:395`、`purchaseApi:426`、`platformIntegrationApi:443`、`platformApi:464`）。上表三条旧口径全部作废：<br>① `orderApi` 已有列表与分页方法（对应 `GET /api/orders`），不再是 4 个；<br>② 8 个"空壳"里 **7 个已填充真实调用**；<br>③ **唯一残留空壳是 `platformApi`（`:464-466`，体内只剩注释「根据实际后端API补充」）**——它没有方法，所以任何页面 import 它都不可能拿到数据（`platform/SystemConfig.jsx:3` 正是这种情况：import 了却不调用，`:16-41` 自拼 `mockConfig`）。<br>📌 对照：**admin-web 的 `platformApi` 与 user-web 不同**，它有 `getSystemConfig`/`saveSystemConfig` 两个**硬编码桩方法**（`admin-web/src/services/api.js:116-143`，`saveSystemConfig` 只 `console.log`），比空壳更危险——看着能用，数据是假的。逐页清单见 §7 |

**契约规范（新代码必须遵守）**：

1. **解包口径只有一层**：拦截器已把 `Result<T>` 解成 `T`，页面拿到的就是数据本身 → 页面写 `const list = await tenantApi.getTenants()`，**禁止再 `.data`**。详见 §8 的双重解包缺陷。
2. 后端信封有三种封装写法并存（[api.md §2](../api/api.md)），前端**不做兼容猜测**：由后端统一为 `Result<T>`，前端只保留「`code === "200"` → `data`」一条路径；异常分支一律走 `errorHandler`。
3. 环境地址：改为 `import.meta.env.VITE_API_BASE_URL ?? '/api'`，配合 §2 的 Vite proxy 实现同源（顺带解决 admin-web 的 CORS 403）。
4. 每个模块一个 `services/<module>.api.js`，方法名 `get/create/update/remove` + 资源名，参数对象化（`{ page, size, ...filters }`），不在页面里散写 URL 字符串。
5. 401 处理改为 `authContext.logout()` + `navigate('/login', { state: { from: location } })`，不用 `window.location`。

## 5. 路由

| 项 | user-web | admin-web |
|---|---|---|
| API 风格 | JSX `<Routes>`（`routes.jsx`）+ `App.jsx` 的 `BrowserRouter` | `createBrowserRouter` + `main.jsx` 的 `RouterProvider` |
| 叶子路由 | ~~34 条：`/login` 1 + `AuthGuard` 下 33（index + 11 模块×3）+ `PlatformAdminGuard` 下 2~~ **43 条**（`routes.jsx` 共 49 个 `<Route>` 元素 = 42 个具名 `path` + 1 个 `index` + 6 个无 path 的守卫/布局包裹）：`/login` `:72`；裸 `AuthGuard`（`:75`）→ `LayoutWrapper`（`:76`）下 **40 条**（index `:77` + 39 具名，含 `tenants`×3 `:112-114`、`users`×3 `:117-119`、`roles` `:122`）；`PlatformAdminGuard`（`:127`）下 **2 条**（`platform/dashboard:129`、`platform/config:130`）；`TenantAdminGuard`（`:135`）下 **0 条**——块内只剩一行注释（`:137`），是真正的**空壳** | 6 个路由对象：`/login` `:35` + `/` 包裹 `:40` + 子级 5 条（`/` `:48` 与 `/platform/dashboard` `:52` **指向同一组件** `PlatformDashboard`、`/platform/config:56`、`/tenants:60`、`/users:64`）。旧锚点 `:14-21` 现在落在 `AdminGuard` 定义里，已失效 |
| 嵌套/布局 | 三层：`AuthGuard` → `LayoutWrapper`（`AppLayout` + `Outlet`）→ 页面；`LayoutWrapper` 出现 **3 次**（`:76`、`:128`、`:136`）= 三套菜单高亮上下文 | 两层 |
| 守卫 | ~~⚠️ `PlatformAdminGuard`/`TenantAdminGuard`（`:44-53`）**只是转发 AuthGuard，角色检查整段被注释**（`:33-36`）~~ ✅ **2026-01-25 复测：角色检查已实现**。`components/Auth/AuthGuard.jsx`：`:9` 收 `requiredRoles`，`:29-31` 未登录 `Navigate /login` 带 `state.from`，`:34` 判 `hasRole(requiredRoles)`，`:35-43` 渲染 403 `Result`；`PlatformAdminGuard`（`:51-53`）与 `TenantAdminGuard`（`:58-60`）分别转发 `['PLATFORM_ADMIN']` / `['TENANT_ADMIN','PLATFORM_ADMIN']`，`AuthContext.hasRole`（`context/AuthContext.jsx:115-119`）已能正确处理数组入参（`Array.isArray` 归一 + `some`）。⚠️ **残留两处**：① `/tenants`、`/users`、`/roles` 三组**只挂在裸 AuthGuard 下**（`routes.jsx:112-122` 未传 `requiredRoles`）⇒ 任意已登录卖家可打开这些管理页（页面能渲染，但 `GET /api/tenants` 会 403——`SecurityConfig.java:66` 与 `UserController/RoleController:31` 仍在后端兜底，所以是"能看壳不能拿数"）；② `TenantAdminGuard` 定义了却**一条路由都没挂** ⇒ 该守卫形同不存在 | ⚠️ ~~**无任何守卫**，全部路由裸开放（M-09）~~ → **已修**：`admin-web/src/routes.jsx:13-32` 定义 `AdminGuard`（`:18` 未登录跳 `/login`，`:21` `hasRole('PLATFORM_ADMIN')` 不符则渲染 403 `Result`），并在 `:42` 包住 `/` 布局路由；`context/AuthContext.jsx:74` 的登出已改为真实清理而非 `console.log`。⚠️ 但这是**第二份守卫实现**，与 user-web 的 `AuthGuard` 无任何共享（见 §9 抽公共包） |
| 懒加载 | 无，全部静态 `import`（`:6-48`） | 无 |

规范：① 两 app 统一为 `createBrowserRouter` 数据路由（可 `React.lazy` + `Suspense`，且未来接 SSR/预取不用重写）；② 守卫必须是**真实角色判定**，`requiredRoles` 生效，未授权跳 `/unauthorized`（当前注释掉的分支正好补上）；③ 路由表与页面一一对应，不再出现空壳 `Route` 块；④ 34 个页面的静态引入应改为按模块 lazy，首屏只加载 `home/order`。

## 6. 认证与状态管理

**token 存储**：`localStorage`，~~键 `token` / `username`~~ → **2026-01-25 复测：6 个键**（`AuthContext.jsx:8-15` = `token`、`refreshToken`、`username`、`tenantId`、`roles`、`permissions`）。读写点（user-web）：

| 操作 | 位置 |
|---|---|
| 读 | `services/api.js:17`（注入头，只读 `token`）、`AuthContext.jsx:59-61`（初始化：有 token 就拉 `/auth/me`）、`:72`（login）、`:110-112`（`checkAuth`） |
| 写 | `AuthContext.jsx:76-84`（login：token/refreshToken/username/tenantId/roles）、`:48-51`（`applyProfile`：username/tenantId/roles/permissions） |
| 删 | `AuthContext.jsx:24-26`（`clearSession` 遍历 **全部 6 个键** `Object.values(KEYS).forEach(removeItem)`），由 `:36`（`/auth/me` 失败）与 `:104`（logout）调用 |

登录链路：`pages/auth/Login.jsx`（Form `onFinish`）→ `authApi.login`（`api.js:84`）→ `login(response)` → `AuthContext.login`（`AuthContext.jsx:71-95`，存 5 个键后 `:93` 触发 `refreshProfile()`）→ `navigate('/')`。页面刷新恢复：`useEffect`（`:56-67`）→ `refreshProfile`（`:29-41`）调 `authApi.getMe()` ⇒ `GET /api/auth/me`（`AuthController.java:146`）⇒ `applyProfile`（`:44-54`）用库里的实时角色/权限覆盖本地值；失败即 `clearSession()` 复位为未登录。~~登出只清 `localStorage` + 复位状态（`:44-49`），**未调用 `authApi.logout`**。~~ → 现已调用，见下表 #4。

状态管理：**无 Redux/Zustand/Mobx**（两 app `package.json` 实测）。全局状态仅 `AuthContext`（`user`、`isAuthenticated`、`loading`，`AuthContext.jsx:19-21`，value 见 `:128-138`）；~~34 个页面~~ **45 个页面**各自 `useState` 管理本地数据。

⚠️ 现存结构性问题：

| # | 问题 | 证据 | 目标 |
|---|---|---|---|
| 1 | **双重 Provider / 双重 ErrorBoundary 嵌套** | `main.jsx:8-15` 已包 `AuthProvider` + `ErrorBoundary`（来自 `services/errorHandler.jsx`），`App.jsx:47-55` 内部**又包一层**自建 class ErrorBoundary（`:9-44`）+ `AuthProvider`（`:50`）+ `BrowserRouter`。按 React Context 语义，页面消费的是内层实例，外层闲置（运行时行为未验证） | Provider 只在 `main.jsx` 装配一次；`App.jsx` 退化为路由出口 |
| 2 | token 存 `localStorage`，XSS 可窃取 | §6 读写点 | 评估改内存 + `sessionStorage` 刷新恢复，或 httpOnly cookie（需后端配合，属 ADR 级决策） |
| 3 | 无 token 过期/续期处理 | 前端不解析 JWT `exp`，也不调 `authApi.refresh`。**2026-01-25 复核：仍未修**，但成因要说准——`authApi.refreshToken`（`api.js:86`）**已存在**、refreshToken **也已存进 localStorage**（`AuthContext.jsx:77-79`），只是**全 src 零调用方**；过期后的实际行为是刷新页面时 `/auth/me` 403 → `refreshProfile` 的 `catch`（`:34-40`）静默清会话退回登录页 | 拦截器按 401 触发一次静默 refresh，失败才登出（前提是后端先返回 401，见 §4 与 ISS-017） |
| 4 | ~~登出未通知后端 | `AuthContext:44-49`~~ | ✅ **2026-01-25 已修**：`AuthContext.logout`（`:98-107`）先 `await authApi.logout()`（`:100`，失败不阻塞，`:101-103`），再 `clearSession()`（`:104`）+ 复位状态。⚠️ 但要同时知悉后端 `/api/auth/logout` 只是 `SecurityContextHolder.clearContext()`（`AuthController.java:137-139`）、**无 jti 黑名单** ⇒ 这次"通知"在服务端**不产生任何撤销效果**，已签发的令牌在其 24h 有效期内仍然可用。前端不能假设令牌已失效 |
| 5 | ~~admin-web 无认证 | 无 `context/`、无守卫、无 Login 页；`App.jsx:12-15` 退出按钮只 `console.log('Logout')`；mock `authApi` 写死 `admin/admin123` 返回 `'mock-jwt-token'`（`api.js:165-185`）且无页面引用（死代码）~~ | ✅ **2026-01-25 已闭合**：新增 `admin-web/src/context/AuthContext.jsx`（`logout` 在 `:72-78`，真实调 `authApi.logout()` 并按键清理 localStorage）、`pages/Login.jsx`、`routes.jsx:13-32` 的 `AdminGuard`（`:18` 未登录、`:21` 非 PLATFORM_ADMIN → 403 Result，`:42` 应用到 `/` 布局路由）；`App.jsx:14-15` 的退出按钮改为 `await logout()`（已无 `console.log`）；写死 `mock-jwt-token` 的 mock `authApi` 已删，取而代之的是真实 `authApi`（`api.js:69`）+ 一个**硬编码桩 `platformApi`**（`:116-143`，`saveSystemConfig` 只 `console.log`）。⚠️ 代价是**守卫与 AuthContext 现在是两份实现**（user-web `AuthGuard.jsx` vs admin-web `AdminGuard`），行为需人工同步；另注意 `user_roles` 里 `admin` 现持 `PLATFORM_ADMIN`（ISS-061 副作用），**演示隔离时不要拿 admin 当普通租户用户** |

**服务端状态**（列表/详情数据）目前全部手写 `useState` + `useEffect`。规范：新增数据页统一走自研 `useResource(fetcher)` hook（封装 `loading/error/data/refetch` 四态），避免每页重复手写三态；是否引入 TanStack Query 属技术选择，需 ADR。

## 7. UI、样式与组件规范

| 维度 | 现状（实测） | 规范 |
|---|---|---|
| AntD 主题 | 全 src **零** `ConfigProvider`、零 locale 引入 | 在 `main.jsx` 统一 `<ConfigProvider theme locale={zhCN}>`，色板/圆角/字号集中一处（对齐 [design system]） |
| 国际化 | 无，文案硬编码中文 | 单语言可暂不引入 i18n，但跨境场景建议预留 `t()` 抽层 |
| 自建组件 | user-web 仅 2 个（`Layout.jsx`、`Auth/AuthGuard.jsx`）；admin-web 0 | 表格/表单必须抽公共封装：`DataTable`（列定义 + 分页 + loading/empty/error）、`FormModal`、`PageHeader`。现状是每个页面各写 `columns`（`TenantList.jsx`、`OrderList.jsx` 等） |
| 样式 | 全局 `index.css` + `App.css` + 大量行内 `style={}`；无 CSS Module / less / sass / tailwind（find 实测零命中） | 保留全局 + AntD token；页面级样式收敛为 `<module>.css`，禁止新增行内样式（除动态值） |
| 脚手架残留 | ⚠️ admin-web `index.css`（68 行）仍是 Vite 默认深色样式（`color-scheme: light dark`、`#242424`、`body{display:flex}` `:6-8,27`）；`App.css:1-43` 保留 logo 动画；两 app 都有 `assets/react.svg` | 一次清理 PR 解决，避免新人以为是设计意图 |
| 错误处理 | `services/errorHandler.jsx`（仅 user-web）含 `ErrorBoundary` + 第二份 `ERROR_MESSAGES`（`:53-59`），与 `api.js:29-35` 重复 | 错误码→文案映射**单一来源**，见 [api/error-codes.md](../api/error-codes.md) |

## 8. 数据源现状与去 mock 路线

~~**真实联网页面：9 / 34**（`grep -l '*Api.'` 实测：`Login`、`order/CreateOrder`、`order/OrderDetail`(部分)、`tenant/×3`、`user/×3`）。其余 25 个页面为前端假数据：~~

🔴 **2026-01-25 复测：该口径整体作废，现为「真实联网页面 42 / 45」**（逐文件 `grep` 实测）：45 个页面里 **41 个 import `services/`**（39 个单引号 + 2 个双引号写法：`purchase/PurchaseDetail.jsx:5`、`purchase/CreatePurchase.jsx:5`），不 import 的只有 4 个（`home/Home.jsx`、`platform/PlatformDashboard.jsx`、`product/CreateProduct.jsx`、`product/EditProduct.jsx`），其中后两个是 `ProductForm.jsx` 的 3 行薄壳（`CreateProduct.jsx:7`、`EditProduct.jsx:10`，而 `ProductForm.jsx:41/:91/:94` 是真实调用）⇒ **实质 mock 只剩 3 个页面**：`Home`、`PlatformDashboard`、`SystemConfig`。

| 形态 | ~~页面与证据~~ **2026-01-25 复测状态** |
|---|---|
| ~~随机数列表~~ | ✅ **已清零**：全 `pages/` 目录 `Math.random()` **0 命中**（旧列的 `OrderList.jsx:43-51`、`ProductList.jsx:56`、`CustomerList.jsx:44`、`PurchaseList.jsx:47`、`FinanceList.jsx:53` 均已改为真实调用） |
| ~~整段 mock 常量~~ | 🟡 **只剩 2 处**：① `platform/SystemConfig.jsx:20-41`（`mockConfig` 常量 + `setConfig(mockConfig)`，且 `:3` 导入的 `platformApi` 是**零方法空壳**，`:49-54` 的 `onFinish` 只 `console.log('Saving config:')` + `message.success('…保存成功')` —— 假成功）；② `home/Home.jsx:34-47` 与 `platform/PlatformDashboard.jsx:9/:20/:64`（`useState`/`setStats` 内联字面量，如 `totalOrders: 1250`、`totalRevenue: 128500.50`，注释自陈「暂时使用模拟数据」`:37`）。`OrderDetail`/`CustomerDetail`/`FinanceDetail`/`PurchaseDetail` 的 mock 常量已全部替换（现分别为 6/4/2/7 处 `*Api.` 调用） |
| ~~真实调用被注释~~ | ✅ **已解开**：`order/OrderDetail.jsx` 现有 6 处真实调用（`:48` getById、`:70` updateOrderStatus、`:84` cancelOrder、`:95` deleteOrder、`:111/:119` 触发平台同步），旧注释行 `:131`/`:155` 与「模拟…」说明均不存在 |
| 假查询参数 | ✅ **已改**：`current-tenant-id` 字面量全仓 **0 命中**；`user/UserList.jsx:23` 现为 `currentUser?.tenantId \|\| localStorage.getItem('tenantId') \|\| ''`。⚠️ 但这只是把「假值」换成「**客户端自选值**」——`tenantId` 仍由前端传参（M-05/ISS-051），真正的修复在后端 |
| 分页是装饰 | 🟡 **部分**：`api.js:93` `getTenants: () => api.get('/tenants')` 仍无 `page/size`（后端 `TenantController` 也只有 `List`）；已分页的模块（orders/customers/products/logistics/finance/各 import）改为读后端 `PageResult.total`。仍是无界列表的后端接口：Purchase、Warehouse、ShopWarehouse、LogisticsChannel、PlatformIntegration、Tenant（6 个，见 [api.md §10](../api/api.md)） |

**去 mock 顺序（与后端能力对齐，不超前）**：

1. ~~修 §9 的 P0 解包 bug~~ ✅ 已完成（`AuthContext.jsx:72-76` 直接读 `responseData.token`，并加了 `:73-75` 缺令牌即 `throw`，不再静默跳过）。
2. ~~补 `productApi`、`logisticsApi`、`warehouseApi`、`customerApi`、`financeApi`、`purchaseApi`、`platformIntegrationApi` 七个空壳模块~~ ✅ **已完成且超出**：这 7 个 + `orderImportApi`/`logisticsChannelApi`/`logisticsImportApi`/`productImportApi`/`shopWarehouseApi`/`warehouseImportApi`/`customerImportApi`/`financeImportApi` 共 15 个模块已填充，`api.js` 现为 21 模块 / 194 方法。**唯一残留空壳是 `platformApi`（`api.js:464-466`）**——它对应的是「系统配置」这类**后端根本没有端点**的能力，所以补不动（先要有 `GET/PUT /api/platform/config`）。
3. 列表页逐个切真实接口；**后端未分页的接口先不切**（无界返回有性能与内存风险，M-07），等后端补分页（[performance-capacity.md §4](performance-capacity.md)）再切，中间态在页头标注「演示数据」。→ 现仅剩上表「分页是装饰」列出的 **6 个无界接口**未补分页，其余已切完。
4. ~~`UserList.jsx` 的 `'current-tenant-id'` 改为从 `AuthContext` 取当前租户~~ ✅ 字面量已清除，⚠️ 但「真正的修复在后端」这条**未动**（`tenantId` 仍由客户端传参，见 M-05/ISS-051 与 [security-design.md §4](security-design.md)）。
5. ~~admin-web 整体接后端：先解决 5174 的 CORS（~~`SecurityConfig:31`~~）或改走 Vite proxy 同源。~~ → 🟡 **CORS 已白名单**（`infrastructure/config/SecurityConfig.java:50` 同时放行 5173/5174，注意类的真实路径在 `infrastructure/config/` 而非 `interfaces/config/`），admin-web 也已接后端；**未做的是 Vite proxy**——两个 `vite.config.js` 仍只有 `plugins:[react()]`，`baseURL` 仍是硬编码绝对地址，所以同源方案与「换环境改代码」的问题依旧存在。

## 9. 缺陷清单（前端）

| 级别 | 缺陷 | 证据 | 修复方向 |
|---|---|---|---|
| ~~**P0**~~ | **响应双重解包导致登录态写不进去**：拦截器已 `return response.data.data`（~~`api.js:42`~~ 现为 `:46`），`AuthContext.jsx:32` 又读 `responseData.data?.token` → `undefined`，`:35` `if (token)` 静默跳过 | ~~同类问题：`tenant/TenantDetail.jsx:19`、`user/UserDetail.jsx:19`（`response.data`）；反证正确用法：`TenantList.jsx:23-24`（`response`）~~ ✅ **2026-01-25 已修（P0 关闭）**：`AuthContext.login` 现直接读 `responseData.token`（`:72`）、缺令牌时 `throw`（`:73-75`）不再静默跳过；`refreshProfile` 走 `/auth/me`（`:31`）并覆盖 roles/permissions（`:46-52`）。同类点 `tenant/TenantDetail.jsx`、`user/UserDetail.jsx` 也已清（各自 `:19` 只剩「拦截器已解包」的注释，代码里无 `.data` 二次取值） | ~~统一「拦截器解包一次」口径：改上述 3 处；并在 `api.js` 顶部注释写明契约 + 补一条拦截器单测锁住~~ 口径已收敛；⚠️ **仍缺后两件**：`api.js` 顶部没有「解包只发生一次」的契约注释，也没有拦截器单测锁住（见 §11 用例 ①） |
| P1 | `ERROR_MESSAGES` 两处重复（`api.js:29-35` / `errorHandler.jsx:53-59`），且后端信封是否返回这些语义码未验证。**2026-01-25 复核：仍是重复，且实为三份**——① `api.js:29-35` 模块级常量 `ERROR_MESSAGES`；② `services/errorHandler.jsx` 的 `getErrorMessageByCode`（方法在 `:52`）内的**函数级局部量** `errorMessages`（小写，映射体 `:53-59`，5 个键与 ① 逐字相同）；③ `admin-web/src/services/api.js:26-32` 又一整份。另 `api.js` 里还有第二处使用点 `:61`（error 分支） | 同上 | 单一来源 + 与 [error-codes.md](../api/error-codes.md) 对齐 |
| P1 | 无 `@Valid` 对应的前端校验：AntD `Form` rules 覆盖情况逐页不一 | 抽查 `CreateOrder`/`TenantList` | 表单必填/长度/金额精度统一 rules 常量 |
| P1 | 列表无分页参数、无 loading 骨架、无错误态兜底（部分页面） | ~~`api.js:88`；`OrderList.jsx:55-57`~~ → **2026-01-25 复测：前端分页参数已补齐**（`OrderList.jsx:34-37` 维护 `pagination` 状态、`:58-59` 发 `page`(0 起)/`size`、`:74` 读 `result.total`、`:90-95` `handleTableChange` 驱动翻页，`:316` 传给 Table）。⚠️ **残留改到后端侧**：Purchase / Warehouse / ShopWarehouse / LogisticsChannel / PlatformIntegration / Tenant 六个列表接口仍无界（`api.js:93` `getTenants` 也没有 `page/size`），这些页面只能拿到全量数组后本地切片 | `useResource` + `DataTable`（§6/§7）+ 后端为上述 6 个接口补分页 |
| P2 | ~~角色守卫空壳、`TenantAdminGuard` 路由空块 | `AuthGuard.jsx:33-36`、`routes.jsx:113-117` | 接真实角色（依赖后端 authorities 修复，S-02）~~ → 🟡 **一半已修**：角色判定已实现（`components/Auth/AuthGuard.jsx:34` + `:35-43` 的 403 Result，依赖的后端 authorities 修复即 ISS-041），⚠️ **两个残留**：① `TenantAdminGuard` 路由空块照旧（`routes.jsx:135-139`，块内只有 `:137` 一行注释）——要么往里挂路由要么删掉守卫；② `/tenants`、`/users`、`/roles`（`:112-122`）仍挂在**裸 AuthGuard** 下、没传 `requiredRoles`，等于业务侧用户可打开管理页外壳（数据被后端 403 挡住） |
| P2 | 无懒加载、`main.jsx`/`App.jsx` 双重 Provider | §5、§6-1 | 路由重构一次解决 |
| P2 | ~~admin-web 与 user-web 4 个页面重复（`PlatformDashboard.jsx`、`SystemConfig.jsx` **byte-identical**；`TenantList.jsx`、`UserList.jsx` 结构平行、`pagination={{` 对称出现在 `:170`/`:194`） | `diff -q` 实测零差异 |~~ → **2026-01-25 复测：重名页面增至 5 个，且只有 2 个还逐字节相同**（逐个 `diff -q` + 字节数）：`PlatformDashboard.jsx` **IDENTICAL**（7021 / 7021）、`SystemConfig.jsx` **IDENTICAL**（5291 / 5291）、`TenantList.jsx` **已分叉**（10384 vs 7465）、`UserList.jsx` **已分叉**（12397 vs 8422）、`Login.jsx` **新增重名且已分叉**（3845 vs 2922）。分叉是**本轮补 admin-web 认证与真实调用**造成的必然结果 ⇒ 风险已从「重复」升级为「**同名不同实现**」：改一处不会同步另一处，而两份 `SystemConfig` 现在都还是假的（user-web 侧 `pages/platform/SystemConfig.jsx:20-41` mock + `:53` `console.log`；admin-web 那份逐字节相同，所以行为一致——`platformApi` 在 user-web 是空壳、在 admin-web 是桩）。⚠️ 另注意 byte-identical 的两份里 `import { platformApi } from '../../services/api'` 在两个 app 解析到**不同实现**，这是"看起来一样、跑起来不一样"的典型 | §10 共享包 |

上述条目已（或应）在 [qa/known-issues.md](../qa/known-issues.md) 以 `ISS-xxx` 立项，本表用于工程侧排序。

## 10. 两 app 复用策略

当前无 workspace，重复项见 §1/§9。两条路线（择一，需 ADR）：

| 路线 | 做法 | 成本 | 适用 |
|---|---|---|---|
| **A. 引入 pnpm workspace + `frontend/packages/shared`** | 把 `apiClient`、`ERROR_MESSAGES`、`AuthContext`、`AuthGuard`、`DataTable`、`FormModal`、AntD 主题 token 抽进 `@elabel/shared`；两 app 只做业务页面 | 中（需加 `pnpm-workspace.yaml`、改 lock） | 长期两个前端并存、且 admin-web 要真正接后端 → **推荐** |
| B. 保持独立，靠 copy-paste + 约定 | 只做 §4/§5/§7 的规范统一，不共享代码 | 低 | 若 admin-web 决定废弃或并入 user-web（产品决策，见 [ADR-0008](../adr/0008-frontend-user-admin-split.md)） |

**不要**在未抽公共包的前提下改 `apiClient` 的解包口径——那会让两份实现彻底分叉。

## 11. 前端测试接入（最小可行）

现状：**零测试基建**（两 app 无 test script、无 vitest/jest/@testing-library/playwright/cypress）。🔎 **2026-01-25 复核**：`package.json:7-12` 的 scripts 仍是 `dev/build/lint/preview` 四项；`find frontend -name "*.test.*" -o -name "*.spec.*"`（排除 node_modules）**零命中**；两 app 的 `node_modules/` 已安装 ⇒ 「装不了、跑不动」这个借口已不成立，接入只差下面的 `npm i -D` 与三行配置。

```bash
cd frontend/user-web
npm i -D vitest @testing-library/react @testing-library/jest-dom jsdom
```

```js
// vite.config.js 追加
test: { environment: 'jsdom', globals: true, setupFiles: './src/test/setup.js' }
```

```json
// package.json
{ "scripts": { "test": "vitest run", "test:watch": "vitest" } }
```

首批三个用例（价值最高、最防回归）：① `api.js` 响应拦截器在 `code:"200"` / `code:"500"` / HTTP 401 三种情形下的返回值与副作用；② `AuthContext.login` 能拿到 token（正好锁住 §9 的 P0）；③ `AuthGuard` 未登录重定向带 `state.from`。E2E（Playwright）在无 CI 前提下降为 P2，先建单测与拦截器契约测试。详见 [testing.md §6](../testing.md)、门禁接法见 [ci-cd.md §4](ci-cd.md)。
