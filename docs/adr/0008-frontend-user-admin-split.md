# ADR-0008：前端拆分为 user-web 与 admin-web 两个独立 SPA

- 状态：Accepted（admin-web 尚未接入后端）
- 日期记录：2026-01-25（对应提交 `bfbce09 分离用户端和管理端`）
- 相关：[frontend/user-web/README.md](../../frontend/user-web/README.md)、[frontend/admin-web/README.md](../../frontend/admin-web/README.md)、[deployment.md §4](../deployment.md)

## 背景

系统有两类差异极大的使用者：
- **卖家业务人员**：日常操作订单、物流、仓储、产品、采购、财务、客户，模块多、页面深。
- **平台运营/管理员**：租户开通与停复、系统配置、平台级看板，权限模型完全不同（`PLATFORM_ADMIN`）。

早期两者共用一个前端工程，路由与权限混在一处难以区分。

## 决策

拆为两个独立 Vite 工程：

| | user-web | admin-web |
|---|---|---|
| 模块 | ~~34 个页面文件（33 条路由） / 13 个模块目录~~ → **2026-01-25 实测：`src/pages/` 下 45 个 `.jsx`、`routes.jsx` 有 42 个 `path`** / 13 个模块目录 | ~~4 个页面~~ → **5 个页面**（多一个 `Login.jsx`） |
| 路由 | `/`、orders、logistics、products、warehouses、customers、finance、purchase、platform-integration、tenants、users、platform/* | `/`、`/platform/dashboard`、`/platform/config`、`/tenants`、`/users` |
| 认证 | `AuthContext` + `AuthGuard` + Login 页 | ~~**无任何认证**（无 context/、无 components/、无 Login；退出按钮只 `console.log`）~~ → ✅ **2026-01-25 已补**：`context/AuthContext.jsx`（`:74` 退出真调 `authApi.logout()`）+ `routes.jsx:13-24` 的 `AdminGuard`（`isAuthenticated` 且 `hasRole('PLATFORM_ADMIN')`）+ `pages/Login.jsx`。仍是**独立实现**，未与 user-web 共用（M-09 已修，ISS-026 数据可信度另说） |
| 后端集成 | ~~37 个 API 调用已打通（auth/tenant/user/order-create），其余 8 个 api 模块为空壳 + 25 个页面仍用模拟数据（34 个页面文件中仅 9 个发起真实调用）~~ → **2026-01-25 实测：`services/api.js` = 21 个 api 模块 / 194 个方法**，唯一空壳 `platformApi`（`:464-466`）；**45 个页面里 42 个走真实接口**，仍写死的只有 `home/Home.jsx`、`platform/PlatformDashboard.jsx`、`platform/SystemConfig.jsx` | ~~**0%**（`src/services/api.js` 整个文件是 mock 层，不 import axios）~~ → **60%（3/5 页）**：`api.js`（143 行）已是真实 axios 实例，`authApi:69`/`tenantApi:77`/`userApi:90`/`roleApi:102` 发真实请求；残留 `platformApi:116-143` 是内存桩（`saveSystemConfig:137` 只 `console.log(:138)`），因此 `PlatformDashboard`（无 import）与 `SystemConfig`（0 调用点）两页仍是假数据 |
| 技术栈 | React 19.2 + Vite 7.2 + AntD 6.2 + axios，JS/JSX（无 TS） | 同上，package.json 几乎逐字节相同 |

## 后果

**正面**
- 权限边界清晰：管理端不会被业务端路由表意外暴露；两边可独立发布。
- 后端 `SecurityConfig` 的 CORS 白名单可逐源管控。

**代价 / 待处理**
1. **重复依赖与样板**：两份 package.json、两份 eslint.config.js、两份 vite.config.js，且 `user-web` 与 `admin-web` 存在**同名页面文件**（~~`TenantList.jsx`、`UserList.jsx`、`PlatformDashboard.jsx`、`SystemConfig.jsx`~~ → **2026-01-25 实测共 5 个**，多出 `Login.jsx`），实现相近但各自维护 —— 后续应考虑抽公共包或用 monorepo 工具（pnpm workspace / Nx）。<br>🔎 **重复程度分两档（`diff -q` + 字节数实测）**：`PlatformDashboard.jsx`（两端均 7021 字节）与 `SystemConfig.jsx`（均 5291 字节）是**字节级完全相同**的复制；`TenantList`（10384 vs 7465）、`UserList`（12397 vs 8422）、`Login`（3845 vs 2922）**已分叉**（admin 版是精简表格流）→ 前者可直接抽公共包，后者抽包前要先做行为归一。`AuthContext` 也是两份独立实现。
2. **端口未固定**：两个 `vite.config.js` 都没有 `server.port`，5173/5174 只是默认端口占用后自动递增的结果。<br>✅ **2026-01-25 复核仍在**：两份 `vite.config.js` 内容一致，都只有 `plugins: [react()]`——**既没有 `server.port`，也没有 `/api` proxy**，所以 [tech/frontend-architecture.md](../tech/frontend-architecture.md) 里"改同源 + 反代"的方案至今未落地，前端仍靠硬编码 `baseURL: http://localhost:8082/api`（各自 `services/api.js:6`）+ 后端 CORS 白名单跑通（L-06）。
3. ~~**CORS 未覆盖 admin-web**：后端只放行 `http://localhost:5173`，**5174 不在白名单**。目前因 admin-web 不联后端而未暴露，一旦接入会立刻失败。~~ → ✅ **2026-01-25 已修**：`SecurityConfig:50` 为 `List.of("http://localhost:5173","http://localhost:5174")`，admin-web 也已实际接入（M-09）。❌ 残留是**允许的 origin 仍硬编码在 Java 里、未外部化**（属 S-04 边界，本轮不改）。
4. ~~**admin-web 缺认证是设计空洞而非进度问题**：后端 `/api/tenants/**` 需要 `PLATFORM_ADMIN`（而该角色判断当前恒不通过，见 [ADR-0007](./0007-jwt-auth-and-tenant-context.md)），因此 admin-web 即便接上后端也无法工作——两端必须一起修。~~ → 🟡 **2026-01-25：这条判断当时是对的，但"如何成立"和预期不同。** 权限点进 JWT 后（ISS-041）`hasRole` 已能真正判定，可 `admin` 依然进不了管理端——因为**它从来没被分配 `PLATFORM_ADMIN`**：`DataInitializationConfig` 的分配步骤在非 Web 线程读 `@ElementCollection` 抛 `LazyInitializationException`，又被 `catch` 吞成一行日志（**ISS-061**）。现改为仓储读方法带 `@Transactional(readOnly = true)` + `RoleMapper` 防御性拷贝，启动日志出现「已为用户 admin 分配角色 PLATFORM_ADMIN」，`GET /api/tenants` 实测 200。<br>⚠️ 结论要修正为：**"两端必须一起修"的真正含义是"前端守卫 + 后端授权 + 数据初始化三者必须一起验"**，只测前端或只测接口都会漏。附带副作用：`user_roles` 多出 `admin → PLATFORM_ADMIN`，`admin` 成了跨租户平台管理员，**不要用它演示租户隔离**。
5. 双份登录态：同一浏览器里两个 SPA 各存各的 `localStorage.token`（同源 5173/5174 端口不同 → 实际隔离），没有统一登出。

## 建议

- 固定端口并在 `vite.config.js` 加 `/api` proxy 到 `:8082`，从而**消除对 CORS 的依赖**（后端可收紧为仅允许同源）。
- admin-web 接入后端前，先补 `AuthContext`/`AuthGuard` 与登录页，并同步修复后端角色授予链路。
- 把两 app 的共享部分（axios 实例 + 信封解包、antd 主题、通用列表页组件）抽到 workspace 内的共享包。
