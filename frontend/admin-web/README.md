# admin-web — 平台管理端

跨境电商 ERP 的平台运营侧前端，用于租户开通与停复、平台级看板与系统配置。
与同仓库的 `../user-web`（卖家业务端）拆分为两个独立 SPA，决策背景见 [ADR-0008](../../docs/adr/0008-frontend-user-admin-split.md)。

> ⚠️ **当前状态：本应用尚未接入后端。** `src/services/api.js` 整个文件是 mock 层（不 import axios、不发任何 HTTP 请求），且**没有任何认证**。README 早期版本笼统描述前端「已完成所有模块页面」，对 admin-web 而言并不准确。

## 技术栈

与 user-web 几乎逐字节相同（`package.json` 唯一差异是 user-web 多一行 `license` 字段）：

React 19.2 · Vite 7.2（`@vitejs/plugin-react` 5.1）· Ant Design 6.2 + `@ant-design/icons` 6.1 · React Router DOM 7.12 · axios 1.13（**已安装但未使用**）· JavaScript/JSX，无 TypeScript · ESLint 9 flat config · 无测试基建。

## 运行

```bash
npm install
npm run dev      # http://localhost:5174
npm run build    # 输出 dist/
npm run lint
```

⚠️ 与 user-web 同样的坑：`vite.config.js` 只有 `plugins: [react()]`，**没有声明 `server.port`**。5174 是 Vite 发现 5173 被占用后自动递增的结果。若希望端口稳定，需显式配置。

## 目录结构与页面

```
src/
├── main.jsx                 # createBrowserRouter + RouterProvider
├── App.jsx                  # AntD Layout（Header + Sider + Outlet）
├── routes.jsx               # 5 条路由
├── pages/
│   ├── platform/PlatformDashboard.jsx   # 平台看板（内联模拟数据）
│   ├── platform/SystemConfig.jsx        # 系统配置（mockConfig 常量）
│   ├── tenant/TenantList.jsx            # 租户列表（调用 mock api）
│   └── user/UserList.jsx                # 用户列表（调用 mock api）
└── services/api.js          # 纯 mock：mockTenants / mockUsers / delay() / 假 login
```

| 路径 | 页面 | 说明 |
|---|---|---|
| `/` | PlatformDashboard | 同时作为 index |
| `/platform/dashboard` | PlatformDashboard | 与 `/` 同一组件 |
| `/platform/config` | SystemConfig | 保存只 `console.log` |
| `/tenants` | TenantList | |
| `/users` | UserList | |

## 与后端的差距

| 缺口 | 事实 | 修复前置条件 |
|---|---|---|
| **无认证** | 无 `context/`、无 `components/Auth/`、无 Login 页、`routes.jsx` 无 guard；`App.jsx:12-15` 的退出按钮只 `console.log('Logout')` | 需要新建登录流程；`api.js` 里那个写死 `admin/admin123` 返回 `'mock-jwt-token'` 的 `authApi.login` 是**无人 import 的死代码** |
| **CORS 不允许** | 后端 `SecurityConfig.java:31` 只放行 `http://localhost:5173`，**5174 不在白名单** | 现在不暴露是因为本 app 不联后端；接入时会立刻失败 |
| **租户接口恒 403** | 后端 `/api/tenants/**` 要求 `PLATFORM_ADMIN`，而 JWT 过滤器输出的 authorities 恒为空 → 该角色永不满足 | 必须先修后端鉴权链路（[security-findings.md S-02](../../docs/security-findings.md)），否则接入也拿不到数据 |
| 页面与 user-web 重复 | `TenantList.jsx`、`UserList.jsx`、`PlatformDashboard.jsx`、`SystemConfig.jsx` 在两个 app 中同名且实现相近 | 建议抽共享包或迁到 monorepo workspace |

`user-web` 已经实现的那套真实 API 调用（`tenantApi` 9 个、`userApi` 11 个、`roleApi` 10 个）可以直接复用为 admin-web 的 api 层起点——对应后端端点均已存在，见 [docs/api/api.md §4.2~§4.4](../../docs/api/api.md)。

## 下一步

1. 补 `AuthContext` + `AuthGuard` + 登录页，与 user-web 的实现对齐（注意复用时要避开 user-web 现存的 token 双重解包 bug，见 [user-web README](../user-web/README.md)）。
2. 把 `services/api.js` 换成真实 axios 实例（复用 `Result` 信封解包逻辑），删除 `mockTenants/mockUsers/delay`。
3. 与后端一起解决 CORS（推荐改为 Vite proxy 走同源，或把 5174 加入白名单）与 `PLATFORM_ADMIN` 角色授予。
4. 固定 `server.port`，加 `npm test`（vitest）基建。

## 相关文档

[架构](../../docs/architecture.md) · [接口清单](../../docs/api/api.md) · [部署](../../docs/deployment.md) · [安全发现](../../docs/security-findings.md) · [ADR-0008](../../docs/adr/0008-frontend-user-admin-split.md)
