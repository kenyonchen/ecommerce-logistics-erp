# 0003 · 多租户隔离：共享库 + 应用层租户上下文

> **产品线决策文档**：本文回答"为什么这样选"。
> **实现差距见 [ADR-0007](./0007-jwt-auth-and-tenant-context.md) 末段与 [`../security-findings.md`](../security-findings.md) M-03/M-04**（~~隔离未生效、业务表缺 `tenant_id`~~ → **2026-01-25 二次实测：写路径隔离已生效**（`TenantIsolationAspect.java:55-86` 真抛 `TENANT_ACCESS_DENIED`，`PLATFORM_ADMIN` 放行），**读路径仍未隔离**（仓储层不过滤、没有一条查询从 `TenantContext` 取租户，ISS-050）；业务表 **23/42 有 `tenant_id`、19 张没有**（ISS-010 残余））。

| 项 | 内容 |
|---|---|
| 状态 | **部分落地**（模型与接口在，~~隔离尚未生效~~ → **2026-01-25：写路径隔离已生效、读路径未生效**，见 [`../spec/tenant-and-iam.md`](../spec/tenant-and-iam.md) §5 与 ISS-009/010/050/051） |
| 日期 | 2026-01-25 |
| 决策者 | 项目维护者 |
| 相关文档 | [`../spec/tenant-and-iam.md`](../spec/tenant-and-iam.md)、[`../qa/known-issues.md`](../qa/known-issues.md) ISS-009 / ISS-010 / ISS-024 |

---

## 背景

产品的商业模式是 **SaaS 多租户**：一个部署实例服务多个卖家（含代运营场景，同一服务商切换多个品牌方账户）。因此需要：

1. 租户之间数据**互不可见**；
2. 租户可以自助管理自己的成员与角色；
3. 平台方（我方）可以开户、停用、查看运营指标。

在租户规模尚未验证（预期几十到几百个租户，而非数万）的背景下，需要选择成本低、实现简单的隔离方案。

## 候选方案

| 方案 | 优点 | 缺点 |
|---|---|---|
| **A. 独立库（database-per-tenant）** | 隔离最彻底；单租户导出/恢复简单；可做物理级资源配额 | 运维成本高；连接管理复杂；升级需逐个库执行 |
| **B. 共享库 + `tenant_id` 逻辑隔离 + 应用层拦截（选定）** | 运维简单；单库即可横向扩展；成本低 | 必须靠代码保证过滤，一旦漏写即越权；需要严格的代码规范与切面保护 |
| C. 共享库 + Schema-per-tenant | 介于两者之间 | MySQL 的 schema 即 database，实际等价于 A 的轻量版，仍有连接与迁移复杂度 |

## 决策

采用**选项 B**：共享库 + 每张业务表携带 `tenant_id`，由应用层负责写入租户上下文并在查询时强制过滤。

设计约定：

| 约定 | 内容 |
|---|---|
| 租户上下文载体 | ThreadLocal（`infrastructure/security/TenantContext`） |
| 上下文来源 | JWT 中的 `tenantId` claim → `JwtAuthenticationFilter` 写入 `TenantContext` |
| 统一拦截 | `infrastructure/aop/TenantIsolationAspect` 在服务方法执行前校验上下文的存在性与一致性 |
| 数据列 | 业务表必须带 `tenant_id`，并加索引 |
| 平台级操作 | `/api/tenants/**`、`/api/platform/**` 需 `ROLE_PLATFORM_ADMIN` |

## 理由

- 当前阶段租户数量与数据量都处于"小而分散"的早期，共享库的性价比最高；
- 逻辑隔离便于做跨租户统计（平台运营看板需要）；
- 与已选定的 MySQL 8 + JPA 技术栈组合成本最低，Hibernate 的 `@Filter` / `@TenantId` 可后续启用。

### ⚠️ 当前落地状态（务必阅读）

| 设计 | 实现现状 |
|---|---|
| 业务表带 `tenant_id` | ❌ ~~全库 24 张表仅 5 张有该列~~ → **2026-01-25 按 `information_schema` 复测：42 张表里 23 张有 `tenant_id`、19 张没有**（本轮给 `purchase_orders` 补列，ISS-046）。仍无列的是子表与集合表：`order_items`/`skus`/`inventory_records`/`locations`/`logistics_channels`/`logistics_fees`/`logistics_labels`/`payment_infos`/`shipping_addresses`/`purchase_order_items` + 6 张 `*_import_job_errors` + 2 张集合表（ISS-010 剩余部分、数据字典 R-07） |
| 切面强制校验 | ~~❌ 校验逻辑被注释，只打印日志（ISS-009）~~ → 🟡 **已生效但覆盖面很窄**（2026-01-25 实测）：`TenantIsolationAspect.java:55-86` 只比对**形参名为 `tenantId` 的 String** 与登录租户，不一致抛 `BusinessException(..., "TENANT_ACCESS_DENIED")`。三条边界：`:57-60` **无租户上下文时直接放行**、`:63-65` `ROLE_PLATFORM_ADMIN` 全量放行、`:89-97` 仓储侧切点**只打 trace 不过滤**。所以它是"入参校验"，不是"按列过滤"——**读路径基本没有隔离**（ISS-050） |
| 从 Token 解析租户 | ~~❌ `getTenantIdFromToken()` 恒返回 `"default-tenant"`~~ → ✅ 已修（ISS-008/S-02）：token 携带 `tenantId` claim，解析失败返回 `null`（`JwtTokenProvider.java:141-144`），过滤器构造注入（`JwtAuthenticationFilter.java:31,33`）。⚠️ 但"控制器仍接收客户端传的 `tenantId`"这条没消除（`OrderController.java:66`，见 ISS-002 残余） |
| 应用层透传租户 | ~~❌ `OrderController#createOrder` 直接硬编码 `"default-tenant"`（ISS-002）~~ → 🟡 建单已按登录租户归属（实测单号 `ORD20261002CABB3DB2` 落对租户）；`interfaces/controller` 下 `default-tenant` 字面量只剩 **1 处**——`PurchaseController.java:47` 的 `@RequestParam(defaultValue="default-tenant")`，它与切面的比对规则冲突（ISS-051） |
| 用户↔租户绑定 | 🟡 有列但 `username`/`email` 全局唯一，语义错误（ISS-024） |

**也就是说：~~当前系统并不具备多租户隔离能力。~~** 本 ADR 描述的是目标态，实现工作排在 v0.1。
> 🔄 2026-01-25 就地更正：**「完全不具备」已不成立，但"具备"也不能拿来演示**。准确口径是——**写路径按登录租户约束**（新建数据归属正确租户实测通过；形参名恰为 `tenantId` 的接口会被切面跨租户拒绝），**读路径基本没有隔离**（ISS-050：仓储查询不带租户谓词，A 租户可按 ID 直读 B 租户数据），**库层面 19 张表还没有 `tenant_id` 列**（子表与集合表）。因此"切换租户后看不到对方数据"这类演示**不能做**；本节待办 1（补列）与待办 4（禁止接收客户端 tenantId）都还未完成，隔离仍依赖"调用方守规矩"。

## 后果

### 正面

- 一套部署服务所有客户，运维与升级成本最低；
- 跨租户运营分析容易实现；
- 与现有 JPA 技术栈完全兼容。

### 负面 / 需要承担的成本

- **安全性完全依赖代码正确性**：一个忘记带租户条件的查询就是一次越权；
- 需要额外的自动化防护（切面、DAO 层统一封装、甚至 Hibernate Filter），否则容易漏；
- 单表数据量会随租户数增长，未来可能需要按租户做分区；
- 某租户误删/误操作影响面难以隔离。

### 必须遵守的约束与待办

| # | 待办 | 优先级 | 关联 |
|---|---|---|---|
| 1 | 为所有业务表补 `tenant_id` 列与索引，并回填历史数据 | **P0** | ISS-010 |
| 2 | ~~启用 `TenantIsolationAspect` 校验：上下文缺失或不一致直接拒绝~~ **🟡 2026-01-25 已启用但只覆盖写路径**：切面已生效（`TenantIsolationAspect.java:55-86`），但它只看入参里出现的 `tenantId`；仓储层被显式排除（`:91-97` 仅 trace）。剩余动作 = 把校验下沉到读路径（ISS-050） | **P0** | ISS-009 → **ISS-050** |
| 3 | ~~修复 JWT 解析，从 Token 取真实 tenantId~~ **✅ 2026-01-25 已修**：`JwtTokenProvider.java:141-144` 从 claim 取真实 tenantId。剩余动作 = 移除硬编码默认值，只剩 `PurchaseController.java:47` 的 `defaultValue = "default-tenant"`（ISS-051） | **P0** | ISS-002 |
| 4 | 所有写操作由认证上下文注入租户，禁止接收客户端传入的 tenantId | P1 | ISS-002 |
| 5 | 用户名唯一约束改为 `(tenant_id, username)` | P1 | ISS-024 |
| 6 | 考虑接入 Hibernate `@TenantId` 自动过滤，降低手写遗漏 | P2 | — |
| 7 | 租户停用后拒绝其成员的全部写操作 | P1 | US-905 |

## 复审触发条件

- 出现对隔离级别有法律/合同要求的大客户（→ 考虑独立库方案）；
- 单租户数据量超过单表承载（→ 考虑分区或分库）；
- 租户数量需要跨地域部署（→ 考虑按地域集群 + 租户路由）。


## 更新（2026-01-25 按工作树复测）

决策未变（仍是「应用层 AOP + 共享库共享 schema」），但实现状态比正文描述前进了一步、也暴露了它真正的短板：

- ✅ `TenantIsolationAspect` 已从「全部注释掉、只 `System.out.println`」变为真实生效：`@Before` 切 `application.service..*`，比对入参 `tenantId` 与 `TenantContext`，不一致即抛 `BusinessException("无权访问其他租户的数据", "TENANT_ACCESS_DENIED")`（`TenantIsolationAspect.java:55-86`）；`ROLE_PLATFORM_ADMIN` 放行（`:99-105`）。`TenantContext` 已在 `JwtAuthenticationFilter` 的 `finally` 中 `clearTenantId()`，ThreadLocal 串租户风险解除。
- ✅ 租户列扩散：`tenant_id` 从「24 张里 5 张」到 **42 张里 23 张有 / 19 张没有**（本轮给 `purchase_orders` 补列，ISS-046）。
- ❌ **本 ADR 未说透的关键缺陷**：切面只校验"入参里出现的 `tenantId`"，而**仓储层被显式设计为不过滤**（`:91-97` 仅 trace 日志，注释写明"数据隔离由服务层参数校验保障"）。于是凡是入参不带 `tenantId` 的方法就没有隔离——采购域的 `findById`/`findAll`/`findByStatus`/`findBySupplierId`/`findByWarehouseId` 全部如此（`PurchaseOrderRepositoryImpl.java:58,70,83,97,111`）。**结论：写路径已隔离，读路径基本没有**（新立 **ISS-050**）。"有 `tenant_id` 列" ≠ "隔离生效"。
- ❌ 另一处自相矛盾（**ISS-051**）：`PurchaseController.java:47` 仍留 `@RequestParam(defaultValue = "default-tenant")`，非种子租户不显式传 `tenantId` 就会被上面的切面拒绝；前端目前靠显式传登录租户来绕过，属绕过而非修复。
  - 🔄 **2026-01-25 自查订正（本条原文只数了 controller，低估了一个量级）**："`default-tenant` 只剩 1 处"**只对「`@RequestParam` 默认值」这一形态成立**。实测主源码里字面量 `"default-tenant"` 共 **17 处**：**10 处是 `DEFAULT_TENANT_ID` 常量声明**（9 个应用服务 + `domain/finance/service/FinancialTransactionServiceImpl:26`），**15 处引用**它做 `hasText(tenantId) ? tenantId : DEFAULT_TENANT_ID` 兜底（含 `OrderController:170`、`CustomerController:68`），异步/映射侧另有 `WarehouseMapper:59-60`、`EventHandler:100`、`OrderCreatedEventConsumer:38`、`InventoryShortageEventConsumer:37`、`BusinessProcessOrchestrationService:48,58`。
  - **对本 ADR 判断的影响**：待办第 4 条（"所有写操作由认证上下文注入租户，禁止接收客户端传入的 tenantId"）**远未完成**，且失败模式是**双向**的——非种子租户「不传就被拒」，种子租户「不传就静默落到 `default-tenant`」。后者比 403 更危险，因为它写出的数据是"看起来正常的错租户数据"。`LogisticsOrderMapper` 则已**完全不处理 `tenantId`**（0 命中），属第三种形态：读出来就是 null。
