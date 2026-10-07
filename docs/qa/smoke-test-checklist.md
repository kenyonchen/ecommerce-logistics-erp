# 冒烟测试清单

> 用途：每次版本构建后，用最短路径确认"系统还活着，且新改动没有打坏核心链路"。
> 判定：🟢 通过 ｜ 🟡 已知限制下通过（见备注）｜ 🔴 失败（阻断发布）。
> 已知缺陷编号参见 [`known-issues.md`](./known-issues.md)。

---

## 0. 环境准备

| # | 步骤 | 命令 / 方式 | 预期 |
|---|---|---|---|
| 0.1 | 启动依赖 | MySQL 8（库名 `ecommerce_logistics_erp`，root/123456，见 `application.yml`）；RocketMQ 可选（`scripts/docker/docker-compose.yml`） | 数据库可连接 |
| 0.2 | 启动后端 | `cd backend && mvn spring-boot:run` | 端口 8082 起来，控制台可见 Hibernate 建表 SQL |
| 0.3 | 检查 Swagger | 浏览器打开 `http://localhost:8082/swagger-ui.html` | 页面正常，能看到 ~~12 个 Tag~~ **20 个 Tag**（2026-01-25 实测：`interfaces/controller` 下 20 个 controller 各带 1 个 `@Tag`） |
| 0.4 | 初始化数据 | 首次启动自动执行 `DataInitializationConfig` | 存在默认租户与 `admin` 账号；**并应在启动日志看到「已为用户 admin 分配角色 PLATFORM_ADMIN」**。⚠️ 2026-01-25 实测此前**从未**出现这一行——种子初始化的两个方法在无会话线程上触碰 `@ElementCollection` 懒集合，抛 `LazyInitializationException` 后被 `catch` 吞掉，启动照样"成功"（ISS-061，已修）。登录后解 token 应为 `roles=[ADMIN, PLATFORM_ADMIN]` + **22 个权限点**（2026-01-25 二次实测复证：解出的 JWT `permissions` 恰好 22 项、含 `finance:import`）<br>⚠️ **但"22 个权限点"不等于"22 个权限点都被检查"**。全仓 `hasAuthority(...)` 实测只出现 **10 个不同的点**：`finance:read`（5 处）、`finance:write`（4 处）、`finance:import`、`order:import`、`product:import`、`customer:import`、`logistics:import`、`user:manage`、`role:manage`、`platform:manage`（`@PreAuthorize` 分布：`FinanceController` 9、其余各 1，见 `OrderImportController`/`ProductImportController`/`CustomerImportController`/`LogisticsImportController`/`FinanceImportController`/`UserController`/`RoleController`/`PlatformIntegrationController`）。**其余 12 个点（`order:read`/`order:write`/`product:read`/`product:write`/`customer:read`/`customer:write`/`warehouse:read`/`warehouse:write`/`purchase:read`/`purchase:write`/`logistics:read`/`logistics:write`）只是被种进了数据，没有任何端点检查它们**——给角色勾掉这些点，接口照样能调。特别地，`WarehouseImportController` 有 **0 个** `@PreAuthorize`（连 `warehouse:import` 这个点都不存在，库里 22 个点里也没有它）<br>⚠️ **角色界面录不进第 22 个点**：`user-web/src/pages/role/RoleList.jsx:12-33` 的 `PERMISSION_PRESETS` 只有 **21 项、缺 `finance:import`**，且用的是 `Select mode="multiple"`（`:266`）而非 `tags` ⇒ 既无法从预设里授予财务导入权限，也无法手输新点。测财务导入的授权矩阵时必须直接改库或走 `POST /api/roles/{id}/permissions`（`roleApi.setPermissions` 在 `:126`）<br>⚠️ **本节的事件/联动类用例当前不可用**：跨聚合的领域事件对订单、客户、物流、仓储、平台五个聚合根本没发布出去（**ISS-062**），所以"下单后自动出现物流单""缺货后自动出现采购单""订单状态变更触发通知"这三类检查点**必然失败**，且失败原因不在消费者；当前唯一有 `发布领域事件` 日志的是采购、财务、商品三个域，而 `purchase-events` / `finance-events` 两个 topic **没有任何消费者** |
| 0.5 | 启动 user-web | `cd frontend/user-web && npm install && npm run dev` | http://localhost:5173 可访问 |
| 0.6 | 启动 admin-web（可选） | `cd frontend/admin-web && npm install && npm run dev` | http://localhost:5174 可访问（~~数据为 mock~~ → 2026-01-25 实测：**5 个页面里 3 个**（Login/TenantList/UserList）走真实接口，另 2 个是 mock，见 ISS-026）。⚠️ `AdminGuard`（`routes.jsx:13-32`）要求 `isAuthenticated` **且** `hasRole('PLATFORM_ADMIN')`，非平台管理员会被挡在所有路由外——该角色由 0.4 分配（ISS-061 修复前 `admin` 进不去） |

> 注意：后端依赖 `ddl-auto: update` 自动建表，如果 MySQL 版本或方言不一致（例如容器里跑的是别的数据源），可能出现建表失败。**冒烟前先确认数据源与 `application.yml` 一致。**

---

## 1. 认证与主链路（必须全绿）

| # | 场景 | 步骤 | 预期结果 | 备注 |
|---|---|---|---|---|
| 1.1 | 登录成功 | 打开 `/login`，输入 `admin` / `123456` | 跳转到首页，localStorage 中存在 token | CI 环境若改了初始密码需同步 |
| 1.2 | 登录失败提示 | 输入错误密码 | 弹出错误提示，停留在登录页 | — |
| 1.3 | 未登录访问 | 清空 localStorage 后直接访问 `/orders` | 重定向到 `/login` | — |
| 1.4 | 首页渲染 | 登录后进入 `/` | 页面无报错，统计卡片渲染（~~数据为写死常量，属 ISS-015~~ → 2026-01-25 实测确认常量位置：`pages/home/Home.jsx:34-59` 硬编码 1250 单 / ¥128500.50 / 500 商品 等 KPI，**不是接口返回**） | 🟡 |
| 1.5 | 退出登录 | 点击右上角退出 | 回到登录页 | ~~admin-web 的退出是桩（ISS-026）~~ → 2026-01-25 复测：admin-web 退出已走真实接口（`context/AuthContext.jsx:72-81` 调 `authApi.logout()`，`services/api.js:71` → `POST /auth/logout`）；admin-web 剩下的 mock 是 `PlatformDashboard.jsx` 与 `SystemConfig.jsx` |

---

## 2. 端到端真实链路（当前唯一可跑通的业务+管理链路）

> ⚠️ 前提（2026-01-25 实测补记）：整节都要求登录用户持 `PLATFORM_ADMIN`——`SecurityConfig.java:64-67` 把 `/api/tenants/**` 锁在 `hasRole(PLATFORM_ADMIN)`，`/api/users`、`/api/roles` 由类级 `@PreAuthorize` 保护，admin-web 的 `AdminGuard` 也按同一角色拦路由。此前 `admin` **并不持有任何平台管理员角色**，所以本节在 ISS-061 修复前一条都跑不通（实测全部 403）。修复后 2026-01-25 复测：`GET /api/tenants` **200**、`GET /api/users?tenantId={登录租户}` **200**、`GET /api/roles?tenantId=…`、`/api/finance/transactions`、`/api/platform-integrations` 均 200。
> ⚠️ `GET /api/users`、`GET /api/roles` **不带 `tenantId` 会 400**（`VALIDATION_ERROR`，见 ISS-057），不是 500 也不是空列表。

| # | 场景 | 步骤 | 预期结果 |
|---|---|---|---|
| 2.1 | 创建租户 | `/tenants/create` 填写名称、联系邮箱并提交 | 成功后跳转列表，**新租户出现在列表中**（真实数据） |
| 2.2 | 查看租户详情 | 在列表点击某租户进入 `/tenants/{id}` | 展示真实租户信息 |
| 2.3 | 停用/启用租户 | 详情或列表执行停用后刷新 | 状态变更持久化 |
| 2.4 | 创建用户 | `/users/create` 提交 | 出现在用户列表 |
| 2.5 | 修改用户密码 | `/users/{id}` 改密 | 提示成功，可用新密码登录 |
| 2.6 | 锁定/解锁用户 | 执行锁定后刷新 | 状态变更持久化 |
| 2.7 | 分配角色 | 给用户分配角色并刷新 | 角色出现在用户详情中 |

> ~~由于 ISS-009/ISS-010，以上数据并不按租户隔离，冒烟时只需验证"能增删改查"。~~
> 2026-01-25 口径订正：**写路径已按登录租户落库/校验**（ISS-009 已修——`TenantIsolationAspect.java:75-83` 比对形参名为 `tenantId` 的 String，不一致抛 `TENANT_ACCESS_DENIED`；实测非本租户会被拒），**读路径基本没有隔离**（ISS-050：`PurchaseOrderRepositoryImpl.java:58,70,83,97,111` 一类查询不带租户条件；切面在仓储侧只打 trace，`:89-97`）。所以本节除了"能增删改查"，还**不能**顺带演示"A 租户看不到 B 租户的数据"。

---

## 3. 业务模块（~~当前大部分为 mock，只做"不报错"验证~~ → 2026-01-25 复测：user-web 45 个页面里 **42 个已接真实接口**，本节多数行已过时；只剩 `home/Home.jsx`、`platform/PlatformDashboard.jsx`、`platform/SystemConfig.jsx` **3 个**是 mock，见 ISS-015/ISS-026）

| # | 场景 | 路径 | 预期结果 | 备注 |
|---|---|---|---|---|
| 3.1 | 订单列表可渲染 | `/orders` | ~~列表出现 20 条 mock 数据~~ → **真实接口**：`GET /api/orders` 返回分页对象 `{content,total,page,size,totalPages}`（2026-01-25 实测），列表按后端分页渲染 | ~~ISS-015~~ 已去 mock |
| 3.2 | 订单详情可渲染 | `/orders/{任意id}` | 渲染 Detail 表单，无白屏 | ~~mock~~ 真实接口；不存在的 ID 会得到 404 `ENTITY_NOT_FOUND`（见 4.2） |
| 3.3 | 创建订单 | `/orders/create` 填写并提交 | ~~**取决于 ISS-001**：若未修复，这一步很可能失败（后端 500）~~ → ✅ 2026-01-25 实测 **200**：单号 `ORD20261002CABB3DB2`（`ORD`+`yyyyMMdd`+UUID 前 8 位大写，`Order.java:195-198`）。⚠️ 本轮另查出两个 500 才真正跑通：订单行项主键未赋值（**ISS-056**）、`PaymentInfo.transactionId`/`ShippingAddress` 必填字段缺失会 400（ISS-057） | 🔴 → 🟢 |
| 3.4 | 物流列表/详情 | `/logistics`、`/logistics/{id}` | 渲染 ~~mock~~ **真实**数据 | 已接 `/api/logistics*` |
| 3.5 | 产品列表/详情 | `/products`、`/products/{id}` | 渲染 ~~mock~~ **真实**数据 | 已接 `/api/products`（PageResult）；`CreateProduct.jsx`/`EditProduct.jsx` 只是 `ProductForm.jsx` 的薄封装，真实调用在 `ProductForm.jsx:41,91,94` |
| 3.6 | 仓库列表/详情 | `/warehouses`、`/warehouses/{id}` | 渲染 ~~mock~~ **真实**数据 | 已接 `/api/warehouses`；库存见 4.5 |
| 3.7 | 客户列表/详情/创建/编辑/状态变更/软删除 | `/customers`、`/customers/import`、`/customers/{id}` | **已接真实接口**：列表筛选分页、详情页历史订单合并展示、编辑弹窗、激活/暂停/删除按钮均走 `/api/customers` | 2026-01-25 去 mock |
| 3.8 | 财务列表/详情 | `/finance`、`/finance/{id}` | 渲染 ~~mock~~ **真实**数据 | 已接 `/api/finance*`；⚠️ 该控制器带 `@PreAuthorize`，需 `finance:read`（`admin` 现已持有，见 0.4） |
| 3.9 | 采购列表/详情 | `/purchase`、`/purchase/{id}` | 渲染 ~~mock~~ **真实**数据 | 2026-01-25 去 mock（ISS-015/016 收尾，任务 #12）：列表主行改为采购单、金额筛选改前端本地过滤、创建/审批链路走 `/api/purchases`；⚠️ 直调必须显式传 `tenantId`（ISS-051） |
| 3.10 | 平台集成列表/详情 | `/platform-integration`、`/{id}` | 渲染 ~~mock~~ **真实**数据 | 已接 `/api/platform-integrations`（2026-01-25 实测 200；未建任何集成时返回 `data: []`——**4.9 的凭据泄露抽查要先建一条才能验**） |
| 3.11 | 平台运营页 | `/platform/dashboard`、`/platform/config` | 渲染 mock 数据（无菜单入口，需手输 URL） | ISS-029；⚠️ 2026-01-25 实测补充：user-web 的 `platform/PlatformDashboard.jsx:20,64` 与 `platform/SystemConfig.jsx:3`（import 了 `platformApi` 却不调用，`:16-41` 自拼 `mockConfig`）都是假数据，admin-web 同名的 `SystemConfig` 依赖的 `services/api.js:116-143` `platformApi` 本身也是硬编码桩 → **这两个页面不能当数据源演示** |
| 3.12 | 平台客户导入全链路 | `/customers/import` → 选平台 → 勾「使用示例数据」→ 预览 → 直接导入 | 预览表显示映射后行与告警；导入弹窗给出 total/成功/失败/跳过与失败行明细；「导入任务」Tab 可回溯该任务；同数据二次导入应命中幂等（合并不重复建户） | 单测已覆盖（CustomerImportPipelineTest 5 例） |

---

## 4. 接口层抽查（用 Swagger 或 curl）

| # | 场景 | 请求 | 预期 |
|---|---|---|---|
| 4.1 | 获取 Token | `POST /api/auth/login` | 返回 token（code=200） |
| 4.2 | 订单查询不存在的 ID | `GET /api/orders/not-exist` | 返回 `ENTITY_NOT_FOUND`，HTTP 404 |
| 4.3 | 创建订单金额不一致 | `POST /api/orders`，使明细合计 ≠ paymentInfo.amount | ~~**当前实测 HTTP 500 + `SYSTEM_ERROR`**~~ → 2026-01-25 复测为 **HTTP 400 + `INVALID_OPERATION`**：`OrderProcessingService:168` 抛的 `IllegalArgumentException` 已有 handler（`GlobalExceptionHandler.java:104-112` 把 `IllegalArgument`/`IllegalState` 统一映射为 400 INVALID_OPERATION）。期望态的专用错误码仍未给 → ISS-037 部分收敛 |
| 4.4 | 变更订单状态 | `PUT /api/orders/{id}/status?status=COMPLETED` | ⚠️ ~~当前会成功（ISS-003）~~ → 2026-01-25 实测：非法流转被聚合拒绝，**HTTP 400 `INVALID_OPERATION`**，实测文案「订单状态不允许从 处理中 变更为 待处理，允许的下一状态: 已确认、已发货、已取消」；合法流转 200。⚠️ 两点注意：① `OrderStatus` **没有 `DELIVERED`**（只有 PENDING/PROCESSING/CON_FIRMED/SHIPPED/COMPLETED/CANCELLED，`OrderStatus.java:14-19`），传错名会先撞上参数绑定 400（ISS-057）；② `canTransitionTo` 对"目标 = 当前状态"返回 true（同状态自转允许） |
| 4.5 | 库存调拨 | `POST /api/warehouses/{id}/inventory/move` | ~~🔴 已知返回 500（ISS-012）~~ → 2026-01-25 实测 **200**：入库 50 → 入库 10 → 调拨 20 → 出库 5 → 盘点置 45 → 调整置 10 全绿，库中源仓 A=45、目标仓 B=10 与响应一致（ISS-059/ISS-060 已修）。⚠️ 三个演示口径：① body **必须带 `warehouseId`**（`InventoryMovement` builder 校验 "Warehouse ID cannot be null"），但实际按**路径参数**的仓库执行；② `TRANSFER` 的响应只回目标仓入库那条记录，源仓结果要看 `GET /api/warehouses/{id}/inventory`；③ 调拨数量必须 >0、目标仓库必须存在，否则 `ValidationException` → 400 |
| 4.6 | 采购审批 | 创建采购单 → `PUT /purchases/{id}/approve` | 状态变为 `approved`；2026-01-25 实测创建返回 `PO-54a6405d`，`tenantId` 正确、`createdAt` 非空（ISS-046/048） |
| 4.7 | 重复审批 | 再次调用 approve | ~~期望被拒绝（当前未强校验，ISS-006）~~ → 2026-01-25 实测**确实被拒绝**，但响应形状是 **HTTP 200 + `code:"500"`**（`PurchaseController` 的 try/catch 把异常包成 `Result.error(String)`，M-08）——按 HTTP 码判成功的自动化用例会误绿 |
| 4.8 | 财务流水 | `POST /api/finance/transactions?type=income` → `GET /by-type?type=income` | 能查到刚创建的流水。⚠️ 2026-01-25 实测坑：`/by-type` 的 `type` 是**枚举名**（`INCOME`/`EXPENSE`/`REFUND`/`ADJUSTMENT`），传小写 `income` 会 **400 `VALIDATION_ERROR`「缺少必填参数 type 或参数取值不合法」**（ISS-057 的新 handler），大写才 200 |
| 4.9 | 平台密钥泄露检查 | `GET /api/platform-integrations/{id}` | 🔴 当前返回明文 secret（ISS-011，按本轮边界保持未修）；修复后应为掩码。⚠️ 2026-01-25 实测：库里没有任何集成记录时 `GET /api/platform-integrations` 返 `data: []`，**要先建一条**才能验出泄露 |
| 4.10 | 越权检查 | 不带 token 访问 `/api/orders/{id}` | ~~🔴 当前可能通过（ISS-007）~~ → 已被拦，但 2026-01-25 实测形态是 **HTTP 403 + 空 body（0 字节）**，**永远不会 401**：`SecurityConfig.java:62-67` 没配 `httpBasic`/`formLogin`/`exceptionHandling`，未认证直接 403 出口。因此 ①前端 `api.js:63-66` 的 401 → `/login` 分支不可达（ISS-032）；②空 body 也就没有统一信封（ISS-017）。只有进 `GlobalExceptionHandler` 的 `AccessDeniedException`（`@PreAuthorize` 拒绝）才有 403 + 信封（ISS-058，本轮新增） |

---

## 5. 前端体验抽查

| # | 场景 | 预期 |
|---|---|---|
| 5.1 | 视口缩到 768 以下 | 侧边栏自动折叠 |
| 5.2 | 列表页无数据时点击搜索 | 展示空态而非白屏 |
| 5.3 | 接口返回 500 | 弹出错误提示，不应白屏 |
| 5.4 | 页面 JS 异常 | 触发 ErrorBoundary 展示兜底 UI |
| 5.5 | 浏览器标题 | 🟡 显示为脚手架默认值（ISS-031） |

---

## 6. 发布判定标准

| 级别 | 条件 |
|---|---|
| **可通过（内部迭代）** | 第 0~5 节中无新增 🔴；所有 🔴 均可追溯到本文档已登记的已知缺陷。⚠️ 2026-01-25 复测后本节的实测基线：~~后端 164 个单测全绿~~ → **2026-03-08 复测 = 211 个单测全绿**（`mvn -o test`，`Tests run: 211, Failures: 0, Errors: 0, Skipped: 0`），4.1~4.10 抽查除 4.9（按边界保持未修）外全部返回预期状态码，无 500 |
| **可演示（给内部干系人）** | 满足上述条件 + 1.1~2.7 全绿 + 已提前告知演示范围。~~（避开 ISS-001、ISS-012）~~ → 这两项 2026-01-25 已修并实测通过，**演示雷区换成**：① `home/Home.jsx`、`platform/PlatformDashboard.jsx`、`platform/SystemConfig.jsx` 三个页面是假数据（ISS-015/026）；② 没有按钮级权限（`hasPermission` 0 调用方，ISS-035 残余）；③ 读路径不按租户隔离（ISS-050），**不要**演示"切换租户后看不到对方数据"；④ 会话到期不会自动登出，且未授权出口是 403 空 body（ISS-032/058）；⑤ 采购创建直调必须显式带 `tenantId`（ISS-051） |
| **可交付（外部客户）** | P0 清单（ISS-001/002/007/008/009/010/011）全部修复，且 ISS-014/ISS-015 已解决后重新评估 |
