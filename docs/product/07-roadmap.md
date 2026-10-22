# 07 · 产品路线图与版本规划

> 用途：明确"接下来做什么、按什么顺序做、做完什么算完成"。
> 版本策略采用 **v0.x 迭代、v1.0 对外可交付** 的节奏。
> 制定日期：2026-01-25（代码基线 `main@bfbce09`，当前版本 v0.0.1）。

---

## 1. 版本策略

| 版本 | 主题 | 定义 | 目标时间 |
|---|---|---|---|
| **v0.0.1**（当前） | 骨架雏形 | DDD 骨架 + 前后端页面 + ~~mock 数据 + 少量真实链路~~ → 🔄 **2026-01-25 复测：本行已明显低估现状**。实测后端 ~~20 个控制器 / 196 个端点~~ → **24 个控制器 / 214 个端点**、~~110~~ → **145** 处 `@Transactional`；user-web **46 个页面文件全部走真实接口**、`api.js` ~~21 个模块 / 194 个方法（模块/方法数 2026-10-10 待重测）~~ → 🔄 **2026-10-21 实测 23 个模块 / 209 个方法**；认证链路真实可用（JWT 验签 + roles/permissions 进 authorities + `@EnableMethodSecurity` + ~~17 处 `@PreAuthorize`~~ → 🔄 **真注解 130 处 / 22 个控制器，覆盖 198/214 端点**）。**主题名"骨架雏形"已不适用，但 v0.1 的"租户隔离落地"仍未完成（ISS-050）——因此版本标签继续沿用 v0.0.1 是保守选择，不是能力描述** | 已完成 |
| **v0.1** | **能连起来** | 前端去 mock、核心列表/分页、鉴权与租户隔离落地、修复 P0 缺陷。<br>🔄 **2026-01-25 完成度**：前端去 mock ✅（除 3 页）、核心列表/分页 🟡（11/20 控制器）、鉴权 ✅（含 ISS-041/061 两个使鉴权真正生效的修复）、**租户隔离只完成写路径 ❌**（读路径 ISS-050）、P0 缺陷多数已修但 ISS-011/004 按边界保留 | 下一个迭代 |
| **v0.2** | **能闭环** | 跨上下文联动（订单→库存→物流→财务）、出入库、平台对接样板、报表导出 | 再下一迭代。<br>⚠️ 2026-01-25 二次实测把"跨上下文联动"的工作量重新定位：**先修 ISS-062（P0，5/8 聚合的事件恒空、发布器见空列表静默返回）**，再谈消费者与通道；装配层的 ISS-034 已修、`@Scheduled` 仍为 0（平台同步只能手工触发）|
| **v0.3** | **能用好** | 缺货预警与采购建议、客户运营、分类/属性主数据、看板与决策支持 | 后续 |
| **v1.0** | **能交付** | 生产可用：安全、性能、观测、迁移脚本、备份与运维手册齐备 | — |

**判定原则**：任何版本都不允许带着 P0 缺陷交付（见 [`../qa/known-issues.md`](../qa/known-issues.md)）。

---

## 2. 优先级框架

采用 **MoSCoW + 风险优先**组合：

1. 先排 **安全与正确性**（P0 缺陷），因为它们会让其他功能失去意义；
2. 再排 **"看得见"的能力**（列表/分页/去 mock）——决定干系人能否相信这套系统；
3. 再排 **"连得通"的能力**（跨上下文联动）——决定能否真的用；
4. 最后排 **"算得清"的能力**（财务/报表/预警）——决定能否产生决策价值。

---

## 3. v0.1 ·"能连起来"

### 目标

> 让 demo 变成真数据：打开任意列表页看到的都是数据库里的真实数据，且不同租户的数据不会互相串。

### 验收定义（Definition of Done）

- [ ] ~~ISS-001、ISS-002、ISS-007、ISS-008、ISS-009、ISS-010、ISS-011 全部修复；~~ → 🟡 **2026-01-25 复测：7 项里 4 项已修、1 项大部分、2 项未动**。已修：ISS-001（订单编号在聚合内生成）、ISS-007（`validateToken` 真验签）、ISS-008（roles+permissions 进 JWT 与 authorities）、ISS-009（切面恢复校验 + ThreadLocal 清理）。部分：ISS-002（控制器字面量清 1 处，但全仓仍有 10 个 `DEFAULT_TENANT_ID` 常量 + 15 处兜底引用，见 [security-findings.md M-05](../security-findings.md)）、ISS-010（~~23/42 表有 `tenant_id`~~ → 🔄 2026-10-21：**25/45 表有 `tenant_id`**，写路径已校、**读路径未过滤** → 拆出新 ISS-050/051）。❌ 未做：**ISS-011**（平台 `api_key/api_secret` 明文）与 **ISS-004**（枚举命名）——前一项按本轮明确边界**不做、只记录为已知接受风险**，故本条 DoD 不能整体勾选；
- [x] ~~订单、产品、财务、客户四个模块的列表接口上线（支持分页 + 至少 1 个筛选条件）~~ → ✅ **2026-01-25 已达成**：5 个上下文（含物流）分页落地，~~20 个控制器里 **11 个返回 `PageResult`**~~ → 🔄 2026-10-21：**24 个控制器里 14 个返回 `PageResult`（24 个方法声明）**；`GET /api/orders`（`OrderController:64`）支持 `orderNumber/customerId/customerName/status/platform/keyword/startDate/endDate` 条件。<br>❌ 未收口：`WarehouseController` 等仍无界（M-07）
- [x] ~~`services/api.js` 中 8 个空 API 对象补齐，前端 25 个 mock 页面全部改为真实数据~~ → ✅ **基本达成（2026-01-25 逐页实测）**：`api.js` ~~现有 **21 个 api 模块 / 194 个方法**（模块/方法数 2026-10-10 待重测）~~ → 🔄 **2026-10-21 实测 23 个模块 / 209 个方法**；user-web 46 个页面文件（2026-10-10）全部接真实接口，无写死页面。<br>~~❌ 残留：admin-web 5 页里 3 真 2 假（ISS-026），其 `platformApi`（`admin-web/src/services/api.js:116-143`）是**硬编码桩**、`saveSystemConfig` 只 `console.log`~~ → ✅ **2026-10-10：admin-web 6 个页面已接入后端真实接口，`platformApi` 桩已去除**；
- [x] ~~首页统计卡片改为真实统计接口（`Home.jsx` 无 `services/api` import，3 个数字写死）~~ → ✅ **2026-10-21：已达成**——`Home.jsx:31` 已 `import { overviewApi }`，`:69` 调 `getSystemOverview({ recentOrderLimit: 5 })`；`pages/` 下 `mock` 字样实测 0 处；
- [x] ~~库存调拨不再抛异常（ISS-012 修复或下线该入口）~~ → ✅ **已修**（ISS-012：`WarehouseApplicationService.moveInventory:201-232` 改为按仓库 ID 定位，不再跨仓库误定位；顺带发现并记录了 🔴 `Warehouse.findInventoryRecord` 双实例问题，见 [tech/traceability.md §1.3](../tech/traceability.md)）；
- [ ] 🟡 **单元测试覆盖扩展到 Service 层，冒烟清单全绿且无新增 🔴** → 部分：`mvn test` **BUILD SUCCESS**、~~26 个测试类 / **164 个 `@Test`**~~ → 🔄 **2026-10-21：102 个测试类 / 801 个 `@Test`，0 failures / 0 errors / 0 skipped**、`assertTrue(true)` 0 处；但**全部不起 Spring 上下文、不连库**（`@SpringBootTest` 0），本轮 3 个真实缺陷（ISS-047/048/049）与 ISS-061 都是纯单测抓不到的（见 [testing.md](../testing.md)）；冒烟清单已复测但仍有 🔴 未清（ISS-050 读路径隔离）。

### 范围与排期

| # | 需求 | 模块 | 优先级 | 依赖 |
|---|---|---|---|---|
| R-101 | 修复 JWT 校验与 authorities 生成 | IAM | **P0** | — |
| R-102 | 业务表补 `tenant_id` 并启用隔离切面 | 全域 | **P0** | R-101 |
| R-103 | 订单编号生成器 + 修复非空落库失败 | 订单 | **P0** | — |
| R-104 | 订单列表/分页/筛选接口 + 前端去 mock | 订单 | **P0** | R-103 |
| R-105 | 产品/财务/客户列表接口 + 前端去 mock | 多模块 | **P0** | R-102 |
| R-106 | 平台密钥加密存储与响应脱敏 | 平台集成 | **P0** | — |
| R-107 | 订单状态迁移走领域约束（禁止非法跳转） | 订单 | P1 | — |
| R-108 | 统一 `Result` 响应风格与错误码文档 | 工程 | P1 | — |
| R-109 | 采购/物流/仓储/平台集成前端去 mock | 多模块 | P1 | R-105 |
| R-110 | JWT 密钥与 DB 口令改为环境变量 | 工程 | P1 | — |
| R-111 | Service 层单元测试补充 | 质量 | P2 | — |

### 风险

| 风险 | 影响 | 缓解 |
|---|---|---|
| 补 `tenant_id` 需要历史数据回填，改动面覆盖 19 张无租户列的表 | 改动面大、容易遗漏 | 先做核心业务表（orders/logistics/inventory），其余按上下文逐步推进；同时引入切面做兜底校验 |
| 前端去 mock 依赖后端进度 | 排期互相阻塞 | 后端先定接口契约（Swagger），前端用契约并行开发 |
| 安全修复涉及权限模型设计 | 可能返工 | 先落地权限点字典（[`../spec/tenant-and-iam.md`](../spec/tenant-and-iam.md) 第 8 节）再做接口注解 |

---

## 4. v0.2 ·"能闭环"

### 目标

> 让流程跑起来：下单会占库存、发货会自动扣减、完成会自动生成流水；对接至少一家真实平台。

### 验收定义

- [ ] 消费 `OrderCreatedEvent` → 占用库存；`OrderShippedEvent` → 扣减库存 + 生成运费应付；`OrderCompletedEvent` → 生成收入流水；
- [ ] ~~入库 / 出库 / 调拨三个能力全部可用且有变动流水留痕；~~ → 🔄 **2026-03-22 拆成两半判定**：「三个能力全部可用」= **已达成**（`POST /inventory/in`、`/out`、`/count`、`/move` 四端点齐备，`WarehouseApplicationService.moveInventory:201-232` 按四类 `MovementType` 分派，`TRANSFER` 走 `transferInventory:237-252`；HTTP 层不再被反序列化卡住（`InventoryMovement.java:39` `@JsonCreator`），实测返回 200；前端 `WarehouseDetail.jsx:134-179` 已有入库/出库/盘点入口）。「有变动流水留痕」= **未达成**（库里只有 `inventory_records` 一张库存当前态表，没有 `inventory_movements` 流水表；`InventoryMovementEvent` 只在 `Warehouse.updateInventory:224-231` 注册、从不发布，`publishDomainEvents` 仅在 `:45/:55/:102` 三处被调）。因此本条保持未勾选——缺的是流水台账与调拨的前端入口，出入库能力本身已通。详见 [spec/warehouse.md](../spec/warehouse.md) §8；
- [ ] 🟡 **至少一家平台（建议 Shopify）完成"拉取订单 → 回写发货 → 推送库存"端到端对接** → 2026-03-08 状态：前两段代码就位（增量拉单 `ShopifyOrderFetcher` + 字段映射 + 落库 + `ShopifyFulfillmentPusher` 回写），**推送库存这一段明确未实现**（非 Shopify 协议一律返 `PLATFORM_INVENTORY_PUSH_UNSUPPORTED`；Shopify 侧需要先补 `inventory_item_id`/`location_id` 主数据映射，属跨模块 schema 变更）。⚠️ **本条验收标准一条都还没真正满足**：无任何平台沙箱凭据，全部证据是本地 fixture 契约测试（`ISS-064`），"端到端"未经一次真实调用。详见 [spec/platform-integration.md](../spec/platform-integration.md) §10、[tech/integration-design.md](../tech/integration-design.md) §9/§11。
- [ ] 物流渠道可通过界面维护，发货时可做多渠道费用试算；
- [ ] 报表导出（订单、流水、采购三类）支持 Excel/CSV；
- [ ] RocketMQ 领域事件真正启停（或用本地事件总线替代，保证同进程可靠消费）。

### 范围

| # | 需求 | 模块 | 优先级 |
|---|---|---|---|
| R-201 | 领域事件消费链路（订单→库存→财务） | 全域 | **P1** · 🔄 2026-03-22 复测口径：消费端 8 个都在（进程内 4 个 `@EventListener` + MQ 4 个 `@RocketMQMessageListener`），断点在上游「事件根本没被发布」（ISS-062，P0）⇒ 本项的前置是修发布，不是加消费者。另：**采购到货→入库→应付这条已经交付**，用的是应用服务同事务编排而非事件（`PurchaseApplicationService.java:256-262`） |
| R-202 | 入库 / 出库 / 调拨实现 + 变动流水表 | 仓储 | **P1** · 🔄 2026-03-22 复测（订正本条上一版）：**前半已交付**（`/inventory/in`:151、`/out`:161、`/count`:171、`/move`:180 四端点齐，服务层按四种移动类型分派，ISS-012/059/060 已修，前端另有出入库/盘点弹窗 `WarehouseDetail.jsx:134-179`）；~~且 `/move` 因 `InventoryMovement` 值对象无 `@JsonCreator` 在 HTTP 层仍打不通~~ → **这半句错了，已作废**：`InventoryMovement.java:39` 确实带 `@JsonCreator` ＋ 9 个 `@JsonProperty`（全仓唯一一处，`grep -rn JsonCreator backend/src/main/java` = 1），`WarehouseControllerTest:182-213` 用真实 JSON body 断到 200，`docs/qa/smoke-test-checklist.md` 4.5 也已实测调拨返 200 ⇒ `/move` 可达。<br>❌ **真正未交付的是「变动流水表」**：运行时库 `information_schema.tables` 里 `inventory%` 只有 `inventory_records`，没有 `inventory_movements` ⇒ US-304「生成调拨流水」与 `docs/spec/warehouse.md` 验收要点 3 的后半永远过不了。<br>⚠️ 另三个残留缺口：调拨**没有前端入口**（`api.js:297` 有 `moveInventory` 方法、逐页 grep 0 调用）、双写**没有服务层测试**（唯一覆盖把应用服务 mock 掉了）、库位**不参与落位**（`WarehouseApplicationService:118` 恒 `locationId = null`）|
| R-203 | Shopify（首家）平台对接样板 | 平台集成 | **P1** · 🟡 代码 4/5 + 测试 27 例（fixture），**未经真实平台验证**（ISS-064） |
| R-204 | 定时同步任务 + 同步日志记录 | 平台集成 | **P1** |
| R-205 | 物流渠道与价格规则 CRUD + 费用试算 | 物流 | **P1** |
| R-206 | 采购到货入库联动 | 采购、仓储 | P1 · ✅ **已完成（2026-03-22 复测）**：`PUT /api/purchases/{id}/delivered` 同事务逐明细行加库存 + 记采购应付（`PurchaseApplicationService.java:256-262`，测试 `PurchaseApplicationServiceTest:74-113`）。剩余边界另立两行：无「待入库任务/收货单」中间层（不能分批收货、无质检）、无供应商主数据表 |
| R-207 | 报表与导出 | 财务、订单 | P1 |
| R-208 | 引入 Flyway 建立迁移基线 | 工程 | **P1**（准生产前置） |
| R-209 | 物流轨迹明细表与接口 | 物流 | P1 |
| R-210 | 面单 PDF 生成 | 物流 | P2 |

---

## 5. v0.3 ·"能用好"

| # | 需求 | 优先级 | 说明 |
|---|---|---|---|
| R-301 | 差异化缺货阈值配置 | **P1** | 当前所有 SKU 共用硬编码 `threshold=10`（`InventoryRecord:47-57`、`Warehouse:246`），不合理——高价值慢动销商品安全库存可能只需 2，低价值快动销的可能要 50。需要在 `products` 或 `skus` 表加 `reorder_threshold` 列（默认回退到全局配置），让不同商品有不同的触发点。定时扫描支已不存在（`BusinessProcessOrchestrationService` 已删除），预警待办也未建模（只有事件，无待办表）。 |
| R-302 | 客户沟通记录与分层标签 | **P1** | 支撑客服场景（当前完全缺失） |
| R-303 | 产品分类树与属性变体 | P1 | 主数据完整性 |
| R-304 | 供应商主数据 | P2 | 比价与交期评估 |
| R-305 | 多币种与汇率 | P2 | 跨境必备 |
| R-306 | 管理端重构（共享资产 + 登录 + 真实数据） | P1 | 见 [ADR-0005](../adr/0005-dual-frontend-split.md) |
| R-307 | 角色-菜单-路由权限模型落地 | P1 | 依赖权限点字典 |
| R-308 | i18n（中/英） | P2 | 海外同事与外部客户需要 |
| R-309 | 首页决策看板（真实统计 + 图表） | P2 | 需引入图表库 |
| R-310 | 供应商对账与利润报表 | P2 | 走到 CFO 场景 |

---

## 6. v1.0 ·"能交付"

| 类别 | 必要条件 |
|---|---|
| 安全 | P0 缺陷清零；渗透自评通过；密钥托管；审计日志可用 |
| 数据 | Flyway 迁移可回滚；唯一约束与索引完整（数据字典 R-05）；金额类型统一为 DECIMAL |
| 性能 | 1 万单量级下列表响应 < 1s；关键接口有缓存/索引评估 |
| 质量 | Service/Domain 层单测覆盖率 ≥ 60%；端到端冒烟自动化 |
| 观测 | 健康检查、指标采集、结构化日志、告警规则 |
| 运维 | 部署手册、备份恢复方案、故障处置 Runbook、数据库容器编排 |

---

## 7. 长期不做（W）

| 需求 | 原因 |
|---|---|
| 生产制造 / MES | 与跨境履约链路无关 |
| WMS 硬件集成（PDA、AGV、波次算法） | 属于专业 WMS 领域 |
| 广告投放优化 | 独立产品线 |
| 自建商城 / 独立站 | 买家触达发生在第三方平台 |
| 税务申报与发票开具 | 强地区合规属性 |

> 完整范围边界见 [`01-product-overview.md`](./01-product-overview.md) 第 5 节。

---

## 8. Backlog 索引

按本文编号检索：`R-1xx`（v0.1）、`R-2xx`（v0.2）、`R-3xx`（v0.3）、`ISS-xxx`（[缺陷清单](../qa/known-issues.md)）、`US-xxx`（[用户故事](./08-user-stories.md)）、`IA-xx`（[信息架构问题](./04-information-architecture.md)）。

> **使用方式**：每个迭代开始时从本文挑选范围，结束后：① 同步更新 [`03-feature-map.md`](./03-feature-map.md) 的状态矩阵；② 关闭或新增 `ISS-xxx`；③ 更新本文件的验收定义勾选状态。
