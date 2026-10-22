# 文档中心

> 跨境大卖 ERP 系统（Ecommerce Logistics ERP）的**产品与功能文档体系**。
> 面向角色：产品 / 设计 / 前端 / 后端 / 测试 / 交付实施。
> 最后整理：2026-10-21（对当前工作树**逐条实测复测**后的同步；`main` 基线仍是 `c2a22ff 文档回写项目现状实测数据`，工作树含未提交的文档与代码改动）。

## 0. 阅读前须知（三条铁律）

1. **代码是唯一事实来源。** 本套文档按 2026-01-25 的代码现状逐文件核对写成，并在 **2026-01-25 用工作树 + 运行时 MySQL 复测过一轮**（端点数、表数、注解数、异常映射、前后端接线率均已重测；每条更正都保留原句并标注日期，不重排版）；当文档与代码不一致时，**以代码为准**并回来修正文档。
2. **本项目阶段定位已从「骨架完成」推进到「主链路可用、粒度未收口」。** ~~文档中大量功能点标记为「部分实现」，意味着 UI 或接口存在但数据是桩 / 流程未闭环~~ → 2026-01-25 实测：user-web **46 个页面文件 / 43 条路由**（已联网）；admin-web **6 个页面、已接入后端真实接口**；后端 **24 个控制器 / 214 个端点**、**145 处** `@Transactional`（🔄 2026-10-21 两项均已重跑复现）、列表接口普遍已加分页。**但「页面存在 ≠ 功能可用」这条判断依然成立**，未收口的地方换成了：~~部分读路径仍缺租户过滤（ISS-050，部分已修）、部分控制器无方法级鉴权~~、平台凭据明文（ISS-011，按边界不修）、无 DDL 迁移工具（ISS-020，按边界不引入 Flyway）。
   > 🔄 **2026-10-21 复测**（口径与 [architecture.md](./architecture.md) §5、[api/api.md](./api/api.md) 对齐）：**24 个控制器 / 214 个映射** 与 **145 处 `@Transactional`** 本轮重跑仍是这两个数（`readOnly = true` **20 处**、`rollbackFor` **0 处**）；页面口径 46 / 43 / 6 三项全部复现。
   > ~~部分读路径仍缺租户过滤（ISS-050，部分已修）~~ → ✅ **已收口**：`TenantAccessGuard` 覆盖「按 ID 直读/直写」与「缺省 tenantId 的列表」两条切面管不到的路径（ISS-092/050），冒烟 §8.2–8.6 + §8.7–8.10 逐聚合打点。**残留不是「没过滤」而是「过滤口径分散」**：13 个类各自复制 `"default-tenant"` 字面量（见 [adr/0010 后果 6](./adr/0010-mapstruct-domain-jpa-bridge.md)）。
   > ~~部分控制器无方法级鉴权~~ → 精确口径：~~**`@PreAuthorize` 实测 136 处 / 26 个文件**，**24 个控制器里 22 个有**~~ → 🔄 **2026-10-21 二次复测：136/26 是 grep 行数，不是注解数**。逐行分开后：**`grep -rn '@PreAuthorize' backend/src/main` = 136 行 / 26 个文件**，其中 **6 行是 Javadoc/注释里提到这个注解**（`CustomUserDetailsService:23`、`JwtAuthenticationFilter:30`、`RoleNames:11`、`GlobalExceptionHandler:237`、`PlatformIntegrationController:59` 与 `:404`），且这 4 个非控制器文件的命中**全部**是注释 ⇒ **真实注解 130 处、落在 22 个控制器文件**（26 − 4）。130 处再分：**类级 9 处**（`AuditLogController`、`SystemConfigController`、`UserController`、`RoleController` + 5 个 `*ImportController`：customer/finance/logistics/order/product）+ **方法级 121 处**。按端点算：214 个映射里 **198 个有注解**，未有的 16 个＝`AuthController` 4 + `TenantController` 9 + `PlatformIntegrationController` 的 3 个读端点（`GET /auth-specs :90`、`GET /{id} :116`、`GET :142`）。剩 2 个控制器是有意为之——`AuthController`（`/api/auth/login|refresh|logout` 本就 `permitAll`，`/me` 走 `authenticated()`）与 `TenantController`（由 ~~`SecurityConfig.java:84`~~ 现行 `:83` 的 URL 级 `.requestMatchers("/api/tenants/**").hasRole(ROLE_PLATFORM_ADMIN)` 把住，比方法级注解更严）。⚠️ 那 3 个平台读端点靠**控制器内**代码兜住而不是声明式守卫：`resolveTenantScope:447-452`（非平台管理员一律回落到自己的租户，无租户上下文时回 `""`）与 `assertTenantVisible:457-465`（跨租户抛 `TENANT_ACCESS_DENIED`），`/auth-specs` 是纯静态目录不读库。所以「**无方法级鉴权 ≠ 无鉴权**」依然成立，引用这条时必须说明是「注解缺口」而不是「越权缺口」；另外 `.permitAll()` 其实有**两条**（`:78` 认证三件套 + `:80-81` swagger 与 webjars，生产靠 `springdoc.api-docs.enabled=false` 关），做暴露面盘点时别只数认证那一条。
   > 🔴 **本轮新登记的文档漂移位**：`SecurityConfig.java` ~~`:86-87`~~（现行 `:87-88`）的行内注释仍写「默认出口是 403 空 body，前端 `api.js` 的 401→/login 分支因此不可达（ISS-032/058）」，而同一个方法体 ~~`:89-94`~~（现行 `:89-96` 整段 `exceptionHandling`：`authenticationEntryPoint` `:90-92` 返 **401 + `Result` 信封**、`accessDeniedHandler` `:93-95` 返 403 信封）已经装了统一出口——ISS-098 修了行为、没改注释。读代码注释判断「401 通道不可达」会得出与真机相反的结论。
3. **文档不做重复定义。** 数据模型相关的内容统一放在 [`docs/db/`](./db/) 目录，本文档中心只做引用与串联。

## 1. 文档地图

### 产品层 `product/` —— 回答"做什么、给谁做、为什么做"

| 文档 | 读者 | 回答的问题 |
|---|---|---|
| [`01-product-overview.md`](./product/01-product-overview.md) | 全员 | 产品定位、解决什么痛点、产品目标与北极星指标、范围边界 |
| [`02-personas-and-scenarios.md`](./product/02-personas-and-scenarios.md) | 产品 / 设计 | 6 类关键角色画像、职责、痛点与典型工作场景 |
| [`03-feature-map.md`](./product/03-feature-map.md) | 全员 | 功能全景图、9 大模块逐条功能的**真实实现状态矩阵** |
| [`04-information-architecture.md`](./product/04-information-architecture.md) | 产品 / 前端 | 双前端定位、站点地图、路由表、页面↔接口映射、角色可见范围 |
| [`05-core-business-flows.md`](./product/05-core-business-flows.md) | 产品 / 前后端 / 测试 | 8 条端到端业务流程，含每一步的系统支撑现状与断点 |
| [`06-glossary.md`](./product/06-glossary.md) | 全员 | 跨境业务术语 + 系统/DDD 术语统一口径 |
| [`07-roadmap.md`](./product/07-roadmap.md) | 产品 / 管理层 | 版本策略、里程碑、需求 Backlog、优先级与排期依据 |
| [`08-user-stories.md`](./product/08-user-stories.md) | 产品 / 开发 / 测试 | 9 个史诗的用户故事、验收标准（Given/When/Then）与覆盖现状 |

### 功能规格层 `spec/` —— 回答"具体做成什么样"

每个业务模块一份，统一结构：**定位 → 角色权限 → 概念模型 → 状态机 → 业务规则 BR-xx → 功能清单 → 接口对照 → 领域事件 → 缺口 → 验收要点**。

| 文档 | 模块 | 子域 |
|---|---|---|
| [`order.md`](./spec/order.md) | 订单管理 | 核心域 |
| [`logistics.md`](./spec/logistics.md) | 物流管理 | 核心域 |
| [`warehouse.md`](./spec/warehouse.md) | 仓储管理 | 支撑域 |
| [`product.md`](./spec/product.md) | 产品管理 | 支撑域 |
| [`purchase.md`](./spec/purchase.md) | 采购管理 | 支撑域 |
| [`finance.md`](./spec/finance.md) | 财务管理 | 支撑域 |
| [`customer.md`](./spec/customer.md) | 客户管理 | 支撑域 |
| [`platform-integration.md`](./spec/platform-integration.md) | 平台集成 | 通用域 |
| [`tenant-and-iam.md`](./spec/tenant-and-iam.md) | 多租户与权限 | 通用域 |
| [`platform-api-reference.md`](./spec/platform-api-reference.md) | 外部平台协议参考（Shopify 等各家官方接口形状） | 通用域·**🔄 2026-10-21 补入本表**：此前 §1 只列了 9 份规格，`ls spec/` 实际 **10 份**，这份一直在目录里但没进地图 |

### 接口层 `api/`

| 文档 | 内容 |
|---|---|
| [`README.md`](./api/README.md) | **导读**（产品线）：怎么读、按业务导航、横切约定摘要、需求侧诉求 |
| [`api.md`](./api/api.md) | **端点契约唯一权威**（工程线）：214 个端点（24 个控制器，2026-10-10 实测；逐项清单见 §4B）、响应信封、鉴权现状、错误处理、待改进项 |
| [`error-codes.md`](./api/error-codes.md) | **错误码字典**（工程线）：现有 `code` 穷举、目标码表 `000000/A/B/C`、信封扩展 `traceId`、HTTP 状态码一致性、落地 7 步 |

### 工程视角文档（工程线维护，产品线只读引用）

| 文档 | 内容 |
|---|---|
| [`architecture.md`](./architecture.md) | 分层与依赖方向、上下文落地、领域事件、事务边界、命名约定、技术债 |
| [`security-findings.md`](./security-findings.md) | 逐条带行号的安全与风险事实清单及修复顺序 |
| [`testing.md`](./testing.md) | 真实测试水位、失败原因与修复路径 |
| [`deployment.md`](./deployment.md) | 依赖矩阵、启动顺序、配置项、缺失能力 |

### 技术方案层 `tech/`（工程线）—— 回答「该怎么实现、按什么顺序修」

| 文档 | 内容 |
|---|---|
| [`README.md`](./tech/README.md) | **技术文档索引**：清单、按角色阅读顺序、单一权威边界、`P/C/O/I` 命名空间、本目录自身的已知局限 |
| [`tech/dev-guide.md`](./tech/dev-guide.md) | 新人上手：工具链、启动顺序、自检命令、十条实测坑 |
| [`tech/backend-standards.md`](./tech/backend-standards.md) | 分层依赖与 ArchUnit 门禁、命名、事务、领域事件、异常、持久化、日志规范 |
| [`tech/frontend-architecture.md`](./tech/frontend-architecture.md) | 双 app 工程形态、API 层契约、认证态、路由与 mock 分布、去 mock 路线 |
| [`tech/security-design.md`](./tech/security-design.md) | 威胁模型 + 认证/授权/租户隔离/凭据保护的目标设计与修复顺序（事实证据在 security-findings） |
| [`tech/observability.md`](./tech/observability.md) | MDC/traceId、logback profile、Actuator 暴露面、指标与告警清单、慢 SQL 与 N+1 |
| [`tech/performance-capacity.md`](./tech/performance-capacity.md) | 容量假设、瓶颈 `P-1~P-8`、分页与索引矩阵、缓存/连接池取舍、接口性能预算、压测 |
| [`tech/integration-design.md`](./tech/integration-design.md) | 外部平台适配 SPI、凭据加密、限流退避熔断、同步任务表与灰度 |
| [`tech/ci-cd.md`](./tech/ci-cd.md) | 流水线阶段与门禁、阻塞项 `C-1~C-6`、分支与版本策略、文档漂移检查 |
| [`tech/deployment-architecture.md`](./tech/deployment-architecture.md) | To-Be 三档拓扑、Dockerfile/Nginx、发布流程、Runbook、备份/RPO/RTO |
| [`tech/traceability.md`](./tech/traceability.md) | **追溯矩阵**：需求/BR ↔ 接口/领域/表/前端/缺陷 ↔ 技术文档，含反向清单与实测口径命令 |
| [`tech/project-statistics-report.md`](./tech/project-statistics-report.md) | **统计报告 / 技术底账**：代码规模、注解计数、端点与表清单、技术债评估 |
| [`tech/inventory/`](./tech/inventory/) | 上面那份报告的**可下钻数据源**：15 张 CSV（按控制器/按目录/按语言/按测试类/依赖/错误码/前后端文件…） |

> ⚠️ **2026-10-21 补记**：上表此前只列 11 行，`ls docs/tech/` 实际有 **12 份 `.md` + 1 个 `inventory/` 目录**——漏登记的正是 `project-statistics-report.md` 与 `inventory/`，而它俩是 [qa/known-issues.md](./qa/known-issues.md) 多轮"计量口径"的对账依据。**引用统计数字时以本报告 + CSV 为准，但要先看它的复测日期**（头部写 `复测日期：2026-03-22`，晚于它的 architecture.md / known-issues.md 已经把它甩在后面，见 §5 的偏差登记）。

> 分工：architecture / deployment / testing / security-findings 记 **As-Is 与事实证据**；`tech/` 记 **规范与 To-Be 方案**。同一事实只在一处定义（见 §4.6）。

### 数据层 `db/`（工程线）

| 文档 | 内容 |
|---|---|
| [`schema.sql`](./db/schema.sql) | 表结构**快照**（非执行入口，真实来源是 JPA 实体）；⚠️ 当前落后 3 张 ~~product 导入表~~ → 🔄 **2026-10-21 复测：缺的 3 张是 `audit_logs`、`shops`、`system_configs`**（本机 `information_schema` 实测 45 张 vs `schema.sql` 42 条 `CREATE TABLE`，两边做集合差集只有这三张；6 条导入线的 18 张 `*_field_mappings`/`*_import_jobs`/`*_import_job_errors`——含 product 那三张——**全部在**快照里，所以"落后 product 导入表"是误记）。缺的都是 2026-10 轮新增的表，见 [db/migration-plan.md §7](./db/migration-plan.md) |
| [`data-dictionary.md`](./db/data-dictionary.md) | 表/列语义、枚举存储口径、已识别数据风险 R-01~R-12 |
| [`er-diagram.md`](./db/er-diagram.md) | 分上下文的 Mermaid ER 图 |
| [`migration-plan.md`](./db/migration-plan.md) | **迁移路线**：M0 基线冻结 → M1 Flyway → M2 唯一键/索引 → M3 `tenant_id` → M4 金额/时间/乐观锁；不可逆脚本与影子列策略 |

### 计划层 `plans/` —— 已执行完的实施计划留档（🔄 2026-10-21 补入：本目录此前不在文档地图里）

| 文档 | 内容 |
|---|---|
| [`2026-10-07-mainflow-usable.md`](./plans/2026-10-07-mainflow-usable.md) | 「主流程可用性收敛」8 个 Task 的实施计划（ISS-062 事件断链、BR-04 发货扣库存、ISS-050 仓库分页与读过滤、缺货事件租户、前端 PageResult 适配） |

> ⚠️ 这是**历史执行计划，不是活文档**：Task 1~8 的落地结果以 [architecture.md](./architecture.md) §3/§7 与 [qa/known-issues.md](./qa/known-issues.md) §8~§20 为准。本轮复核它对自己根因的描述仍然准确（"`save()` 返回重建副本 + mapper 置空 `domainEvents`"），但它 Task 1 设想的修法（"8 个 `*RepositoryImpl` 的 `save()` 都改成搬运"）与最终实现不完全一致——实测只有 **5 个** Impl 调 `carryOverEvents`，采购与财务走 `void save()`，`ShopRepositoryImpl:55-60` 则是**第三种写法**：`jpaRepository.save(entity); return shop;` 直接返回入参实例（事件因此也不会丢）。三种保存契约并存，正是 [ADR-0010 后果 7](./adr/0010-mapstruct-domain-jpa-bridge.md) 复测段登记的"隐式约定加厚一层"的具体形状。

### 质量层 `qa/`
| 文档 | 内容 |
|---|---|
| [`known-issues.md`](./qa/known-issues.md) | 已知缺陷与技术债清单（含用户可感知影响、临时规避、修复建议版本） |
| [`smoke-test-checklist.md`](./qa/smoke-test-checklist.md) | 版本冒烟测试清单：按角色走查路径与预期结果 |
| [`user-web-verification-2026-04-29.md`](./qa/user-web-verification-2026-04-29.md) | **一次性实测报告**（文件名即时点）：user-web 页面级功能与业务流程验证，含 V-01~V-28 缺陷编号 |

> ⚠️ **2026-10-21 补记**：`qa/` 实际有 **3 份**文档，本表此前只列 2 份。`user-web-verification-2026-04-29.md` 是**带时点的快照**不是活文档——它的 `V-xx` 编号自成一套命名空间（与 `ISS-xxx` 不同），引用时必须注明"2026-04-29 时点"，且其中多条已被后续轮次关闭（该报告自身只到 04-29，未随 10 月的 §13~§20 复测更新）。

### 架构决策层 `adr/`

| 文档 | 内容 |
|---|---|
| [`README.md`](./adr/README.md) | ADR 索引与编写模板（两侧共维：上半 0001~0005 是产品线决策，下半 0002、~~0006~0012~~ → 🔄 0006~**0013** 是工程线实现差距，共 **9 条**） |
| [`0001-domain-driven-design.md`](./adr/0001-domain-driven-design.md) | 产品线：采用 DDD 四层架构与 ~~9 个~~ 限界上下文（🔄 2026-10-21 实测 **13 个 + `common`**，见该 ADR 的状态补充；标题按 ADR 惯例不改） |
| [`0003-multi-tenancy-isolation.md`](./adr/0003-multi-tenancy-isolation.md) | 产品线：多租户隔离：共享库 + 应用层租户上下文 |
| [`0004-jwt-authentication.md`](./adr/0004-jwt-authentication.md) | 产品线：JWT + Spring Security 无状态鉴权 |
| [`0005-dual-frontend-split.md`](./adr/0005-dual-frontend-split.md) | 产品线：用户端 / 管理端双前端拆分 |
| [`0002`](./adr/0002-hibernate-ddl-auto.md) · [`0006`](./adr/0006-rocketmq-domain-events.md) · [`0007`](./adr/0007-jwt-auth-and-tenant-context.md) · [`0008`](./adr/0008-frontend-user-admin-split.md) · [`0009`](./adr/0009-config-externalization.md) · [`0010`](./adr/0010-mapstruct-domain-jpa-bridge.md) · [`0011`](./adr/0011-result-envelope-and-errors.md) · [`0012`](./adr/0012-ddd-single-module-layering.md) | 工程线决策：自动建表与迁移、领域事件、鉴权与租户上下文实现差距、双前端实现差距、配置外部化、MapStruct 桥接、Result 信封、DDD 单模块分包 |

## 2. 建议阅读顺序

| 你是 | 从这开始 |
|---|---|
| **新入职的产品经理** | `product/01` → `02` → `03` → `05` → `08` → `07` |
| **前端开发** | `tech/dev-guide.md` §7（实测坑）→ `tech/frontend-architecture.md` → `product/04` → `api/README.md` → 对应 `spec/*.md` |
| **后端开发** | `tech/dev-guide.md` → `tech/backend-standards.md` → `adr/0001` → `spec/*.md` → `db/data-dictionary.md` → `api/README.md` |
| **测试 / QA** | `testing.md`（真实水位）→ `product/03` → `product/05` → `product/08` → `qa/smoke-test-checklist.md` → `qa/known-issues.md` |
| **实施交付** | `product/01` → `product/04` → `qa/known-issues.md`（重点看"能否演示"） |
| **架构师** | `tech/README.md` → `adr/README.md` 全量 → `architecture.md` → `tech/traceability.md` → `qa/known-issues.md` |

## 3. 状态标记图例（全文档统一）

| 标记 | 含义 | 判定标准 |
|---|---|---|
| ✅ **已实现** | 端到端可用 | 前端页面 → 真实 API → 数据库，链路跑通，有明确的业务语义输出 |
| 🟡 **部分实现** | 骨架在，闭环缺 | 满足其一：UI 用 mock 数据 / 接口存在但逻辑是桩 / 只有增查没有改删 / 流程缺下游联动 |
| ⚪ **未实现** | 仅有规划 | 代码中不存在对应页面、接口或领域能力 |
| 🔴 **高危/已知阻断** | 该项会误导「能不能用」的判断 | 用于标注 P0/P1 级缺陷直接体现在页面、接口或数据上的位置；与 🟡 的区别是：🟡 只是不完整，🔴 是**看起来能用但实际不成立** |

> 页面数量口径统一为：**页面文件数**（`find frontend/user-web/src/pages -name '*.jsx'` = 46）与**路由条目数**（`routes.jsx` 的 `path=` = 43）两个指标，引用时必须写明用哪一个；admin-web 页面文件数 = 6（已接入后端真实接口）。

> 更细的可运行性判断见 [`product/03-feature-map.md`](./product/03-feature-map.md) 的「端到端可用性分级」一节。

## 4. 文档维护约定

1. **触发更新的时机**：新增/变更 REST 接口、新增页面或路由、变更领域状态机、变更业务规则、新增数据表或字段。
2. **改业务规则时**：先改 `spec/*.md` 中对应 `BR-xx` 编号条目 → 同步 `product/08-user-stories.md` 的验收标准 → 若影响体验再改 `product/03-feature-map.md` 状态矩阵。
3. **改表结构时**：改 JPA 实体 → 重启验证 Hibernate DDL → 手工同步 `db/schema.sql`（详见 [`adr/0002`](./adr/0002-hibernate-ddl-auto.md)）。
4. **发现缺陷时**：登记到 `qa/known-issues.md`，编号 `ISS-xxx`，并回指到对应 `spec` 条目，避免"缺陷散落在聊天记录里"。
5. **禁止**：在未跑通链路的情况下把状态从 🟡 改为 ✅。
6. **单一权威（防止同一事实出现两份而漂移）**：
   | 主题 | 唯一权威文件 | 其它文件的做法 |
   |---|---|---|
   | 端点清单 | [`api/api.md`](./api/api.md) | 只做导读与索引，不重述端点表 |
   | 缺陷立项 | [`qa/known-issues.md`](./qa/known-issues.md)（`ISS-xxx`） | [`security-findings.md`](./security-findings.md)（`S/M/L-xx`）只放带行号的安全证据并回指 `ISS`，不自建待办 |
   | 数据模型与数据风险 | [`db/data-dictionary.md`](./db/data-dictionary.md)（`R-01~R-12`） | 其余文档引用编号即可 |
   | 错误码 | [`api/error-codes.md`](./api/error-codes.md) | `api.md` 与 `tech/*` 只引用码名，不重述码表 |
   | schema 迁移路线 | [`db/migration-plan.md`](./db/migration-plan.md)（里程碑 `M0~M4`） | `adr/0002` 只记选型理由，不重述脚本 |
   | 编码规范与门禁 | [`tech/backend-standards.md`](./tech/backend-standards.md)、[`tech/frontend-architecture.md`](./tech/frontend-architecture.md) | `architecture.md` 记现状与偏离，不复述规范条文 |
   | To-Be 技术方案 | [`tech/`](./tech/) 各设计文档 | 四份工程文档保持 **As-Is + 证据** 定位，不写目标态 |
   | 选型理由 vs 实现差距 | `adr/0001~0005`（产品线写"为什么这样选"）/ `adr/0002`、~~`0006~0012`~~ → 🔄 `0006~0013`（工程线写"现状与差距"） | 两侧文件开头互指，不互相复述 |
   | **权限点目录**（🔄 2026-10-21 新增） | 代码：`domain/tenant/PermissionPoints.java`（`TENANT_SCOPED_PERMISSIONS` **25 项** + `PLATFORM_SCOPED_PERMISSIONS` **1 项** = 26） | 文档只引用点名字符串，不重列清单；⚠️ **两处副本已知风险**：前端 user-web 角色管理页的 `PERMISSION_PRESETS`（该类 javadoc 自己点了这一处）与 `docs/spec/tenant-and-iam.md` 的权限表。历史上就出过"后端有权限、界面配不出来"（ISS-090） |
   | **统计口径**（🔄 2026-10-21 新增） | [`qa/known-issues.md`](./qa/known-issues.md) 头部「计量口径」各行（带复测日期，最新一条生效） | `tech/project-statistics-report.md` 与 `tech/inventory/*.csv` 是 **2026-03-22 快照**，用于下钻而非现值；`architecture.md`/`api/api.md` 引数字时必须写自己的复测日期 |
7. **编号命名空间**：`R-01~R-12`（两位）＝数据字典的**数据风险**；`R-101~R-3xx`（三位）＝路线图的**需求条目**。两者靠位数区分，新增时不要串用位数。其余前缀：`ISS-`=缺陷、`US-`=用户故事、`BR-`=业务规则、`IA-`=信息架构问题。技术文档另用 `P-n`=性能瓶颈、`C-n`=CI 阻塞项、`O-n`=可观测性阶段、`I-n`=平台集成阶段（见 [`tech/README.md §5`](./tech/README.md)）。
   ⚠️ **`M-` 被两处占用**：[`security-findings.md`](./security-findings.md) 的风险等级 `M-01~M-10`（带横线）与 [`db/migration-plan.md`](./db/migration-plan.md) 的迁移里程碑 `M0~M4`（不带横线）。靠**是否带横线**区分，引用时必须带文档名（写「security M-04」/「migration M3」），不要再叠加第三种 `M-` 含义。
