# 文档中心

> 跨境大卖 ERP 系统（Ecommerce Logistics ERP）的**产品与功能文档体系**。
> 面向角色：产品 / 设计 / 前端 / 后端 / 测试 / 交付实施。
> 最后整理：2026-10-10（对 `main` 分支工作树**逐条实测复测**后的同步；基线提交仍是 `bfbce09 分离用户端和管理端`，但工作树已含后续修复与文档回写）。

## 0. 阅读前须知（三条铁律）

1. **代码是唯一事实来源。** 本套文档按 2026-01-25 的代码现状逐文件核对写成，并在 **2026-01-25 用工作树 + 运行时 MySQL 复测过一轮**（端点数、表数、注解数、异常映射、前后端接线率均已重测；每条更正都保留原句并标注日期，不重排版）；当文档与代码不一致时，**以代码为准**并回来修正文档。
2. **本项目阶段定位已从「骨架完成」推进到「主链路可用、粒度未收口」。** ~~文档中大量功能点标记为「部分实现」，意味着 UI 或接口存在但数据是桩 / 流程未闭环~~ → 2026-01-25 实测：user-web **46 个页面文件 / 43 条路由**（已联网）；admin-web **6 个页面、已接入后端真实接口**；后端 **24 个控制器 / 214 个端点**、**145 处** `@Transactional`、列表接口普遍已加分页。**但「页面存在 ≠ 功能可用」这条判断依然成立**，未收口的地方换成了：部分读路径仍缺租户过滤（ISS-050，部分已修）、部分控制器无方法级鉴权、平台凭据明文（ISS-011，按边界不修）、无 DDL 迁移工具（ISS-020，按边界不引入 Flyway）。
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

> 分工：architecture / deployment / testing / security-findings 记 **As-Is 与事实证据**；`tech/` 记 **规范与 To-Be 方案**。同一事实只在一处定义（见 §4.6）。

### 数据层 `db/`（工程线）

| 文档 | 内容 |
|---|---|
| [`schema.sql`](./db/schema.sql) | 表结构**快照**（非执行入口，真实来源是 JPA 实体）；⚠️ 当前落后 3 张 product 导入表，见 [db/migration-plan.md §7](./db/migration-plan.md) |
| [`data-dictionary.md`](./db/data-dictionary.md) | 表/列语义、枚举存储口径、已识别数据风险 R-01~R-12 |
| [`er-diagram.md`](./db/er-diagram.md) | 分上下文的 Mermaid ER 图 |
| [`migration-plan.md`](./db/migration-plan.md) | **迁移路线**：M0 基线冻结 → M1 Flyway → M2 唯一键/索引 → M3 `tenant_id` → M4 金额/时间/乐观锁；不可逆脚本与影子列策略 |

### 质量层 `qa/`

| 文档 | 内容 |
|---|---|
| [`known-issues.md`](./qa/known-issues.md) | 已知缺陷与技术债清单（含用户可感知影响、临时规避、修复建议版本） |
| [`smoke-test-checklist.md`](./qa/smoke-test-checklist.md) | 版本冒烟测试清单：按角色走查路径与预期结果 |

### 架构决策层 `adr/`

| 文档 | 内容 |
|---|---|
| [`README.md`](./adr/README.md) | ADR 索引与编写模板（两侧共维：上半 0001~0005 是产品线决策，下半 0002、0006~0012 是工程线实现差距） |
| [`0001-domain-driven-design.md`](./adr/0001-domain-driven-design.md) | 产品线：采用 DDD 四层架构与 9 个限界上下文 |
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
   | 选型理由 vs 实现差距 | `adr/0001~0005`（产品线写"为什么这样选"）/ `adr/0002`、`0006~0012`（工程线写"现状与差距"） | 两侧文件开头互指，不互相复述 |
7. **编号命名空间**：`R-01~R-12`（两位）＝数据字典的**数据风险**；`R-101~R-3xx`（三位）＝路线图的**需求条目**。两者靠位数区分，新增时不要串用位数。其余前缀：`ISS-`=缺陷、`US-`=用户故事、`BR-`=业务规则、`IA-`=信息架构问题。技术文档另用 `P-n`=性能瓶颈、`C-n`=CI 阻塞项、`O-n`=可观测性阶段、`I-n`=平台集成阶段（见 [`tech/README.md §5`](./tech/README.md)）。
   ⚠️ **`M-` 被两处占用**：[`security-findings.md`](./security-findings.md) 的风险等级 `M-01~M-10`（带横线）与 [`db/migration-plan.md`](./db/migration-plan.md) 的迁移里程碑 `M0~M4`（不带横线）。靠**是否带横线**区分，引用时必须带文档名（写「security M-04」/「migration M3」），不要再叠加第三种 `M-` 含义。
