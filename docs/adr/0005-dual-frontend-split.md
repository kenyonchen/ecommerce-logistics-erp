# 0005 · 前后端分离与双前端拆分（user-web / admin-web）

> **产品线决策文档**：本文回答"为什么要拆"。
> **实现差距见 [ADR-0008](./0008-frontend-user-admin-split.md)**（端口/CORS/认证缺失/重复页面等工程后果）。

| 项 | 内容 |
|---|---|
| 状态 | 已采纳（**admin-web 处于早期原型，待重构**） |
| 日期 | 2026-01-25 |
| 决策者 | 项目维护者（对应提交 `bfbce09 分离用户端和管理端`） |
| 相关文档 | [`../product/04-information-architecture.md`](../product/04-information-architecture.md)、[`../qa/known-issues.md`](../qa/known-issues.md) ISS-016 / ISS-026 / ISS-029 |

---

## 背景

系统需要同时服务两类截然不同的使用者：

| 使用者 | 关注内容 | 使用频率 |
|---|---|---|
| **卖家团队成员**（运营/仓储/采购/财务/客服/负责人） | 日常业务操作，页面多、交互重 | 全天 |
| **平台运营方**（我方内部） | 开户、停用租户、系统参数、平台监控 | 低频 |

如果把两类功能塞进同一个前端，会带来：菜单臃肿、权限模型复杂、发布互相牵连、技术债共享。

## 候选方案

| 方案 | 优点 | 缺点 |
|---|---|---|
| A. 单前端 + 角色动态菜单 | 一套代码、一份样式；复用度高 | 打包体积大；平台级功能与业务功能混杂；发布耦合 |
| **B. 双前端（user-web + admin-web）** | 关注点分离；可独立发布；权限模型简单 | 存在重复代码（当前 4 个页面近乎逐字复制）；需要共享的设计/工具资产机制 |
| C. 单前端 + 微前端（qiankun 等） | 兼顾复用与隔离 | 引入复杂度过高，与团队规模不匹配 |

## 决策

采用**选项 B**：拆分为两个独立的 Vite + React 应用，共用同一套 REST API。

| 前端 | 端口 | 服务对象 | 路由方式 | 现状 |
|---|---|---|---|---|
| `frontend/user-web` | ~~5173~~ → **5120**（🔄 2026-10-10：`vite.config.js:9` 已写死） | 卖家团队 | `<Routes>` 组件式（`src/routes.jsx`） | ~~34 个页面文件（33 条路由），其中 9 个页面有真实 API 调用~~ → **2026-01-25 实测：`src/pages/` 下 45 个 `.jsx`、`routes.jsx` 有 42 个 `path`（49 个 `<Route>` 元素，含守卫/布局容器），其中 42 个页面走真实接口**（`services/api.js` = 21 个 api 模块 / 194 个方法），仍写死的只有 `home/Home.jsx`、`platform/PlatformDashboard.jsx`、`platform/SystemConfig.jsx` |
| `frontend/admin-web` | ~~5174~~ → **5110**（🔄 2026-10-10：`vite.config.js:9` 已写死） | 平台运营 | `createBrowserRouter` 数据式 | ~~4 个页面，全 mock，无登录页~~ → **2026-01-25 实测：5 个页面，且已有登录页 + `AdminGuard` + 真实 `authApi`**（M-09/ISS-026 已修）。数据是 **3 真 2 假**：`Login`/`TenantList`/`UserList` 发真实请求，`PlatformDashboard`（无 `services/api` import）与 `SystemConfig`（有 import、**0 个调用点**，保存只 `console.log`）仍是硬编码；其 `platformApi`（`admin-web/src/services/api.js:116-143`）是**内存桩** |

技术栈统一：React 19 + Vite 7 + Ant Design 6 + React Router 7 + Axios，纯 JavaScript（无 TypeScript）。

## 理由

- 两类使用者的**变化频率与发布节奏完全不同**，拆分后可以互不阻塞；
- 权限模型简化：admin-web 只需区分平台管理员，user-web 按业务角色；
- 缩小单包体积，降低首屏成本。

## 后果

### 正面

- 发布解耦：调整租户管理不影响卖家日常使用；
- 各自可以按需演进 UI 体系（管理端偏简洁表格流，用户端偏操作流）。

### 负面 / 需要承担的成本

| # | 问题 | 关联 |
|---|---|---|
| 1 | **代码复制**：~~`PlatformDashboard.jsx`、`SystemConfig.jsx`、`TenantList.jsx`、`UserList.jsx` 在两个仓库中内容高度重复~~ → **2026-01-25 逐文件实测：两端同名页有 5 个**（`Login`、`PlatformDashboard`、`SystemConfig`、`TenantList`、`UserList`），其中 **`PlatformDashboard.jsx` 与 `SystemConfig.jsx` 是字节级完全相同**（7021 / 5291 字节，`diff -q` 无差异）；`TenantList`（10384 vs 7465）、`UserList`（12397 vs 8422）、`Login`（3845 vs 2922）**已分叉**（admin 版是精简表格流） | ISS-026 |
| 2 | 无共享包机制（monorepo/私有 npm 包/UI 组件库），导致布局、请求层各写各的 | 待办 #1。<br>🔎 2026-01-25 实测补充：`AuthContext` 也是**两份独立实现**（`user-web/src/context/AuthContext.jsx` 与 `admin-web/src/context/AuthContext.jsx:72`），未复用、未抽公共包 |
| 3 | ~~两端 `services/api.js` 行为不一致：user-web 走真实 HTTP，admin-web 是内存 mock~~ → 🟡 **2026-01-25 已收敛大半**：admin-web 的 `api.js`（共 143 行）现在是**真实 axios 实例**，`authApi:69`/`tenantApi:77`/`userApi:90`/`roleApi:102` 都发真实请求；残留的是 `platformApi:116-143` 仍是内存桩（`saveSystemConfig:137` 只 `console.log(:138)`）。两端共同残留：`baseURL` **都硬编码** `http://localhost:8082/api`（各自 `:6`），无 `.env*`、无 `import.meta.env`、两个 `vite.config.js` 都**没有 proxy**（L-06） | ISS-016 大部分已修 |
| 4 | ~~后端 CORS 未把 admin-web 的 `:5174` 加入白名单~~ → ✅ **已加入**：`SecurityConfig:50` `setAllowedOrigins(List.of("http://localhost:5173","http://localhost:5174"))`。<br>❌ 仍是硬编码、未外部化（属 S-04 边界，本轮不改） | ISS-018 已修 |
| 5 | ~~admin-web 缺少登录页、路由守卫、ErrorBoundary、退出实现为空方法~~ → ✅ **2026-01-25 已补齐**：`pages/Login.jsx` 存在且调 `authApi.login`；`routes.jsx:13-24` 定义 `AdminGuard`（`:18` 要 `isAuthenticated`、`:21` 要 `hasRole('PLATFORM_ADMIN')`，挂载点在 `:42`）；退出改为调 `authApi.logout()`（`context/AuthContext.jsx:74`，M-09）。<br>⚠️ 副作用：`PLATFORM_ADMIN` 此前**从未真正分配给 `admin`**（ISS-061），所以该守卫在修复前把唯一可用账号挡在管理端外 | ISS-026 已修 |
| 6 | `user-web` 侧存在设施重复：`App.jsx` 与 `main.jsx` 双层 Provider/ErrorBoundary | 待办 #3 |

### 必须遵守的约束与待办

| # | 待办 | 优先级 |
|---|---|---|
| 1 | 抽取共享资产：建议改为 npm workspace / monorepo，沉淀 `@erp/ui`（布局、表单控件、空/错/加载三态）与 `@erp/api`（axios 实例 + 各业务 API），两端依赖同一份 | P1 |
| 2 | admin-web 补齐登录页、路由守卫、ErrorBoundary、真实 services | P1 |
| 3 | 移除 user-web 中重复的 Provider 包裹，统一在 `main.jsx` 组装 | P2 |
| 4 | 统一 i18n 方案（若未来要做英文站点，两端必须共用同一套语言包与 `ConfigProvider` locale） | P2 |
| 5 | 两端统一基础 URL 管理（改为 `.env` + Vite proxy，消除硬编码与跨域依赖） | P2 |
| 6 | 建立统一的错误处理与反馈规范（当前各页面自行 try/catch + message.error） | P2 |

## 复审触发条件

- 第二个前端出现第三个端（例如移动端/开放平台控制台）→ 必须建立共享包机制，否则重复成本失控；
- 两端 UI 表现出现明显不一致并被用户投诉 → 收敛为共享设计系统；
- 需要提供品牌定制能力 → 引入统一主题 token 与租户级主题配置。
