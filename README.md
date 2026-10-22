# 跨境大卖ERP系统

基于领域驱动设计（DDD）的跨境电商 ERP，为卖家提供订单、物流、仓储、产品、采购、财务、客户与平台集成的一体化管理。前后端分离：React + Vite 前端，Java 17 + Spring Boot 3.5 后端，MySQL 8 存储，RocketMQ 用于领域事件。

> **文档索引**
>
> 产品与功能文档体系（产品定位 / 角色画像 / 功能全景 / 9 份模块规格 / API 总览 / 已知缺陷台账 / 冒烟清单 / 路线图）集中在 **[文档中心 `docs/README.md`](docs/README.md)**，按角色给了阅读路径；其 §4 定义**单一权威划分**与**编号命名空间**（`ISS-`/`S-M-L-`/`R-`/`US-`/`BR-`）。
>
> 工程文档（本仓库工程线所写）：
> - [技术架构设计](docs/architecture.md) — 分层、限界上下文、领域事件、事务、命名约定、技术债
> - [接口设计](docs/api/api.md) — 214 个端点清单、响应信封、错误处理、鉴权现状
> - [数据库设计](docs/db/data-dictionary.md) · [ER 图](docs/db/er-diagram.md) · [schema 快照](docs/db/schema.sql)（⚠️ 快照落后于实体：缺 `audit_logs`、`shops`、`system_configs` 三张；快照里的 42 张 = 40 张业务表 + `user_roles`/`role_jpa_entity_permissions` 两张 `@ElementCollection` 桥表，实体侧为 43 个 `@Entity`）
> - [测试文档](docs/testing.md) — 真实测试水位与修复路径
> - [部署与运维](docs/deployment.md) — 依赖矩阵、启动顺序、配置面、缺失能力
> - [安全与风险发现](docs/security-findings.md) — 逐条带行号的代码级事实清单
> - [架构决策记录](docs/adr/README.md) — ADR-0001 ~ ADR-0013（0001~0005 与 0002 为选型决策，0006~0012 为工程侧实现差距与技术债，[ADR-0013](docs/adr/0013-shop-entity-and-platform-connection-split.md) 为店铺实体与平台连接拆分）

## 项目现状（2026-10-20 逐条重新实测；聚合数字与 2026-10-10 一致，`@Transactional` 的 `readOnly` 计数已由 9 订正为 20）

| 指标 | 实测值 |
|---|---|
| 后端主源码 | 568 个 `.java`（`domain` 205 / `infrastructure` 209 / `interfaces` 110 / `application` 43） |
| 控制器 / 端点 | 24 个 `@RestController` / **214 个端点**（已全部实现路由与方法体）；`@PreAuthorize` **136 处**（分布在 26 个文件） |
| 领域包结构 | `domain/` 下 **14 个包**：9 个业务上下文 + 共享内核 `common` + `audit`/`config`/`overview`/`shop` 四个支撑上下文 |
| 持久化 | 43 张表（`@Entity`）+ 59 Spring Data 仓储接口 + 30 `*RepositoryImpl`（自定义实现）+ 12 MapStruct Mapper |
| 领域事件 | 25 个 `*Event`；发布走双通道（进程内 `@EventListener` + RocketMQ，`rocketmq.enabled: true`），但 4 个 MQ 消费者按 `rocketmq.internal-consumers.enabled=false` **不装配**，副作用只由进程内通道执行一次 |
| 写端点留痕 | 126 个写端点中 **118 个**带 `@Auditable`，8 个有书面豁免，**0 缺口**（2026-10-20 由 `scripts/smoke/audit-coverage-scan.js` 实测） |
| 后端测试 | 102 个测试类 / 801 个 `@Test`（见 [testing.md](docs/testing.md)） |
| 前端 | user-web 46 个页面文件（43 条路由，已联网）；admin-web 6 个页面（**已接入后端**） |
| CI / 迁移工具 / 覆盖率 | 均**无** |

**需要知道的四个前提**（不是待办清单，是当前事实）：

1. **鉴权链路已落地（非桩）**：`JwtTokenProvider.validateToken()` 现校验签名（`parseClaims != null`），`JwtAuthenticationFilter` 每次请求从库重载用户、把角色（`ROLE_` 前缀）与权限点写入 `GrantedAuthority`、写入租户上下文，停用/删除用户降级为匿名（ISS-007/008 已修）。方法级鉴权已成规模（`@PreAuthorize` 136 处 / 26 个文件）；两个例外各有替代防线——`TenantController` 靠 [SecurityConfig.java:83](backend/src/main/java/site/hexaarch/ecommerce/logistics/infrastructure/config/SecurityConfig.java#L83) 的 `/api/tenants/** → hasRole(PLATFORM_ADMIN)` URL 规则，`AuthController` 三条路径 `permitAll` + 自身校验（ISS-061 修复后 `GET /api/tenants` 实测 200，早先「恒 403」的结论已失效）。剩余真实风险见 [security-findings.md](docs/security-findings.md)（如 `Result.code` 仍为 `String`、无 Bean Validation）。
2. **声明式事务已较普遍**：全仓 `@Transactional` 共 **145 处**（约 27 个文件，application 96 / infrastructure 49），`readOnly` **20 处**、`rollbackFor` 仍为 0（ISS-036），事件在事务内是否真正发布仍缺集成测试验证（ISS-062）。
3. **平台对接按认证协议分派，已有 9 个适配器**：`PlatformAuthType` 共 **11** 个协议枚举，其中 **9** 个有落地适配器——`CUSTOM_SHOP`（自建店铺约定通道）+ Shopify / Magento / Amazon SP API / eBay Sell / AliExpress / Shopee / Mercado Libre / Ozon 八家；按资源的 `Http*Fetcher` 通用通道与 `Sample*Fetcher` 示例通道并存。⚠️ `TEMU` 与 `TIKTOK_SHOP` **只有枚举、没有适配器**；⚠️ 无一家有真实平台沙箱凭据，正确性证据只有本地 fixture 契约测试（ISS-064，P0）。
4. **审计、系统配置、店铺与概览已是独立上下文**：`domain/audit`（`AuditLog` + `@Auditable` AOP + `AuditLogController` 2 端点）、`domain/config`（`SystemSetting` + `PlatformConfigKey` + `SystemConfigController` 2 端点）、`domain/shop`（`ShopController` 7 端点 + `ShopWarehouseController` 5 端点，见 [ADR-0013](docs/adr/0013-shop-entity-and-platform-connection-split.md)）、`domain/overview`（`GET /api/overview`）。这四组能力由 `6927f01` 与 `37415ac` 落地，README 早期版本完全没有它们的条目。

## 设计理念

### 领域识别与划分

| 子域类型 | 子域名称 | 描述 | 重要性 |
|---------|---------|------|--------|
| 核心域 | 订单管理 | 处理订单生命周期，是系统的核心价值 | 高 |
| 核心域 | 物流管理 | 管理物流渠道和物流状态，直接影响客户体验 | 高 |
| 支撑域 | 仓储管理 | 支持订单和物流的执行，管理库存 | 中 |
| 支撑域 | 产品管理 | 管理商品信息，为订单和仓储提供基础数据 | 中 |
| 支撑域 | 采购管理 | 保证库存充足，支持销售业务 | 中 |
| 支撑域 | 财务管理 | 处理财务核算，为决策提供数据 | 中 |
| 支撑域 | 客户管理 | 管理客户信息，提升服务质量 | 中 |
| 通用域 | 平台集成 | 与外部电商平台对接，获取订单数据 | 低 |
| 通用域 | 用户与租户管理 | 多租户、用户、角色、认证授权 | 低 |

### 限界上下文

9 个业务上下文已在 `domain/` 下落地为包结构，另有共享内核与四个后加的支撑上下文（实测 `domain/` 下 14 个包）：
订单、物流、仓储、产品、采购、财务、客户、平台集成、租户（tenant：租户/用户/角色）
+ `common`（共享内核）、`audit`（操作留痕）、`config`（系统配置）、`shop`（店铺与店仓绑定）、`overview`（跨域统计）。

每个上下文内部结构为 `aggregate / entity / valueobject / service / event / repository`，详见 [architecture.md §2](docs/architecture.md#2-分层与依赖方向)。

### 架构分层

```
interfaces  →  application  →  domain  ←  infrastructure
  110            43             205           209
```

单 Maven 模块内以包实现分层（[ADR-0012](docs/adr/0012-ddd-single-module-layering.md)），领域模型为纯 POJO，JPA 实体与领域模型经 MapStruct 双向隔离（[ADR-0010](docs/adr/0010-mapstruct-domain-jpa-bridge.md)）。

## 项目结构

```
ecommerce-logistics-erp/
├── backend/                     # Spring Boot 单 Maven 模块
│   ├── pom.xml
│   └── src/
│       ├── main/java/site/hexaarch/ecommerce/logistics/
│       │   ├── interfaces/      # 24 Controller · Result 信封 · GlobalExceptionHandler · dto
│       │   ├── application/     # 应用服务（43 文件）· command/query
│       │   ├── domain/          # 14 包：9 业务上下文 + common + audit/config/shop/overview
│       │   └── infrastructure/  # persistence(jpa+mapper+repository) · security · platform(9 协议适配器) · messaging · aop · config
│       ├── main/resources/application.yml   # 唯一配置文件（无 profile；口令与 JWT 密钥走 `${ENV:默认值}`）
│       └── test/java/…                       # 102 个测试类
├── frontend/
│   ├── user-web/                # :5120 卖家业务端（46 页面文件 / 43 路由）
│   ├── admin-web/               # :5110 平台管理端（6 页面：Login + PlatformDashboard + SystemConfig + AuditLogList + TenantList + UserList）
├── scripts/
│   ├── docker/                  # docker-compose（mysql:8.0 + rocketmq:5.3.2 namesrv/broker）+ erp-services.sh
│   └── smoke/                   # 11 支真 HTTP 端到端探针 + 2 个统计脚本（见下方「端到端探针」）
├── docs/                        # 架构 / API / DB / 测试 / 部署 / ADR / 安全
├── LICENSE   COPYRIGHT   README.md
```

## 功能实现状态

图例：✅ 后端端点 + 前端页面均已打通 · 🟡 后端可用但行内写明的限制仍成立 · ⚪ 尚未实现

> 🔄 **2026-10-20 图例订正**：`🟡` 原本定义为「后端有 API，前端仍是模拟数据」，这个前提**已整体过期**——
> `grep -rl 'mock' frontend/user-web/src/pages frontend/admin-web/src/pages` 命中 **0 个文件**，两个前端的每个页面都直连后端。
> 因此本表现在的 `🟡` 只表示「可用但有明确缺口（缺口在说明列里）」，不再表示前端是假的。

### 1. 订单管理
| 功能 | 状态 | 说明 |
|---|---|---|
| 创建订单 | ✅ | `POST /api/orders`；🔄 租户归属已收口——`OrderApplicationService.java:115` 走 `TenantAccessGuard.resolveWriteTenant()`，订单缺租户时回退到**当前登录租户**而非 `default-tenant`（`DEFAULT_TENANT_ID` 仅作为无上下文时的最后兜底） |
| 订单详情 | ✅ | `GET /api/orders/{id}`:116，前端 `OrderDetail.jsx:63` 已接 |
| 按订单号查 | 🟡 | `GET /api/orders/number/{orderNumber}`:107 有端点，`api.js:198` 有 `getOrderByNumber`，但**没有页面调用** |
| 按客户查订单 | ✅ | `GET /api/orders/customer/{customerId}`:156，`api.js:201` 已被页面调用 |
| 订单状态变更 | ✅ | `PUT /api/orders/{id}/status`:126，前端 `updateOrderStatus` 已接；🔄 2026-10-07 发货联动库存已打通（首次 SHIPPED 逐行扣减出库仓库存，缺货/无仓库回滚，BR-04） |
| 订单状态字典 | ✅ | `GET /api/orders/statuses`:96（前端下拉框与状态标签使用） |
| 订单列表 | ✅ | ~~无 list-all 端点~~ → `GET /api/orders`:75（分页 + 9 个筛选条件），前端 `OrderList` 已去 mock（`api.js:195`） |
| 订单删除 | ✅ | `DELETE /api/orders/{id}`:147，前端 `OrderList.jsx:122`、`OrderDetail.jsx:113` 已接 |
| 订单取消 | ✅ | ~~无专用端点（可用 status 变通）~~ → 🔄 `POST /api/orders/{id}/cancel`:137，前端 `services/api.js:199` 已接 |
| 订单同步（平台） | 🟡 | ~~`OrderSyncService` 为桩实现~~（该类已不存在）→ `POST /api/platform-integrations/{id}/sync-orders` 走 `PlatformFetcherRegistry` 分派：`CUSTOM_SHOP` 真实 HTTP、`SHOPIFY` 按官方 Admin REST、`SAMPLE` 示例数据；结果落 `order_import_jobs`/`_errors`。⚠️ **未经真实平台验证**（ISS-064），且**无定时调度**（只能人工触发，`R-204`） |

### 2. 物流管理
| 功能 | 状态 | 说明 |
|---|---|---|
| 物流单创建/详情/按订单查/状态变更 | ✅ | `POST`:77、`GET /{id}`:113、`GET /order/{orderId}`:121、`PUT /{id}/status`:130，前端 `logisticsApi`（`api.js:231-251`）一一对应 |
| 物流单列表 | ✅ | ~~有 `GET /api/logistics`（全量无分页），前端未接~~ → 🔄 2026-10-20 复测：`LogisticsController.java:84` 已改 `PageResult` + 8 个筛选条件（运单号/订单/渠道/状态/关键字/日期区间），前端 `api.js:234` + `LogisticsList.jsx` 已接 |
| 发货 / 取消 / 追踪 / 轨迹追加 / 批量发货 / 删除 | ✅ | `POST /{id}/ship`:141、`/cancel`:152、`/track`:168、`/traces`:176、`POST /batch-ship`:186、`DELETE /{id}`:199，前端全部有对应方法 |
| 物流状态字典 | ✅ | `GET /api/logistics/statuses`:102 |
| **物流渠道管理** | ✅ | ~~有表与仓储，**无 API**~~ → 🔄 `LogisticsChannelController` **7 个端点**：`GET`:54、`GET /{id}`:67、`POST`:78、`PUT /{id}`:92、`PUT /{id}/status`:117、`DELETE /{id}`:127、`GET /{id}/fee`:135（运费试算）；前端 `LogisticsChannels.jsx` + `api.js:258-266` 七个方法全接。⚠️ `GET` 是全量返回**无分页**，且运费试算结果只用于前端展示、**不落库**（落库的是建单时按渠道口径预估的那一笔，见 §6 费用核算） |
| **物流标签生成** | 🟡 | ~~无端点，状态可置 `LABEL_GENERATED` 但不产面单~~ → 🔄 `POST /api/logistics/{id}/label`:160，`LogisticsApplicationService:222-238` 生成并持久化 `LogisticsLabel`（运单号 + 收件人 + `labelFormat=PDF`，幂等：已有标签直接回读）。⚠️ 仍是**本地建档**：`labelUrl` 与 `barcode` 不赋值，**拿不到承运商真实面单文件** |

### 3. 仓储管理
| 功能 | 状态 | 说明 |
|---|---|---|
| 仓库创建/详情/按编码查/列表/更新/删除 | ✅ | `WarehouseController`：`POST`:84、`GET /{id}`:99、`GET /code/{code}`:107、`GET`:115、`PUT /{id}`:137、`DELETE /{id}`:154；🔄 列表已改分页 + 租户过滤（`tenantId` 缺省取 JWT 上下文，`resolveTenant()` 走 `TenantContext.normalizeRequested()`，ISS-050 仓库段） |
| 仓库启停 | 🟡 | `PUT /{id}/status`:164 端点可用，`api.js` 也有 `changeWarehouseStatus`，但**没有任何页面调用它** |
| 库存记录查询 | 🟡 | `GET /api/warehouses/{warehouseId}/inventory`:184 |
| 库存入库 / 出库 | 🟡 | `POST .../inventory/in`:193、`.../inventory/out`:205（出库触发 `InventoryShortageEvent` → 自动补货单，见 §5） |
| 库存盘点 | 🟡 | `POST .../inventory/count`:217（应用层为简化实现；盘点只发 `InventoryCountEvent` 留痕，**不触发补货**——这是设计选择，见 ISS-145 撤销说明） |
| 库存移动 | 🟡 | 🔄 2026-10-07 前后端已通：`POST .../inventory/move`:228，详情页库存弹窗支持调拨（TRANSFER + 目标仓库）；仍无变动流水表 |
| **库位管理** | 🟡 | ~~有 `locations` 表，**无 API**~~ → 🔄 2026-10-20 复测：`POST /api/warehouses/{id}/locations`:174 已存在，前端 `WarehouseDetail.jsx` + `api.js:350` 已接。⚠️ **只有新增**——库位无独立查询/更新/删除端点，随仓库聚合读写 |
| **店仓绑定** | ✅ | `ShopWarehouseController` 5 个端点：`GET`:47、`GET /{id}`:57、`POST /bind`:66、`POST /{id}/unbind`:74、`POST /sync/{integrationId}`:83（见 [ADR-0013](docs/adr/0013-shop-entity-and-platform-connection-split.md)），前端 `ShopWarehouseSync.jsx` |

### 4. 产品管理
| 功能 | 状态 | 说明 |
|---|---|---|
| 创建/更新/详情/按 SKU 查/上下架/删除 | ✅ | `ProductController` 7 个 CRUD 端点：`POST`:92、`PUT /{id}`:101、`GET /{id}`:117、`GET /sku/{skuCode}`:126、`PUT /{id}/activate`:136、`PUT /{id}/deactivate`:146、`DELETE /{id}`:156；前端 `api.js:300-307` 全接 |
| **产品列表** | ✅ | ~~无 list 端点~~ → 🔄 `GET /api/products`:63（分页 + 筛选），前端 `ProductList.jsx:63` 已去 mock。⚠️ 前端两处调用仍显式传 `DEFAULT_TENANT_ID`（`ProductList.jsx:98`），真实归属由服务端 `TenantAccessGuard` 决定 |
| **SKU 管理** | ✅ | ~~仅只读查询，无增删改~~ → 🔄 `POST /{id}/skus`:166、`DELETE /{id}/skus/{skuCode}`:176，前端 `ProductDetail.jsx` 已接增删 |
| 批量上下架 / 批量删除 | ✅ | `PUT /batch/active`:187、`DELETE /batch`:197，前端 `ProductList.jsx` 已接 |
| **产品分类** | ⚪ | 🔄 措辞订正：`domain/product/entity/ProductCategory` 与 `ProductCategoryService` **已存在**，但**仍无 `categories` 表、无 REST 端点**（`schema.sql:336` 的 `category_id` 是现场拼的 UUID，风险 R-01） |
| **产品属性管理** | 🟡 | ~~仅领域值对象 `ProductAttribute`~~ → 🔄 已随聚合持久化：`ProductMapper:64` 把 `attributes` 序列化为 `attributes_json`（列在 `skus` 表上，`SkuJpaEntity:70`），`:95` 反序列化回值对象。⚠️ 无独立属性字典/校验，属性项由调用方自由填写；`schema.sql:360` 另记该列 `VARCHAR(4000)`，超长会截断（风险 R-08） |

### 5. 采购管理
| 功能 | 状态 | 说明 |
|---|---|---|
| 采购单创建/详情/列表/状态字典 | ✅ | `POST`:67、`GET /{purchaseOrderId}`:119、`GET`:81、`GET /statuses`:105 |
| 明细行增 / 改 / 删 | ✅ | `POST /{id}/items`:133、`PUT /{id}/items/{itemId}`:150、`DELETE /{id}/items/{itemId}`:167，前端 `api.js:496-503` 三方法一一对应 |
| 采购审批（批准/拒绝） | ✅ | `PUT .../approve`:183、`.../reject`:200（拒绝必须填 comment，`approvedBy` 省略时记登录账号） |
| 运输中 / 已送达 / 取消 | ✅ | `PUT .../in-transit`:220、`.../delivered`:236、`.../cancel`:251；⚠️ 列表接口不带 status 时回退查 `PENDING`，非「全部」 |
| **采购入库** | ✅ | ~~无专用端点~~ → 🔄 2026-10-20 复测：入库内联在 `.../delivered` 里，`PurchaseApplicationService.receiveIntoWarehouse:276-288` 按明细行逐条 `inventoryIn`（BR-08/BR-09），**无明细行的空单只告警跳过**；同方法随后 `createPurchasePayable` 挂采购应付（BR-10） |
| **缺货自动补货** | ✅ | 出库后库存判为 `SHORTAGE`/`OUT_OF_STOCK` → `InventoryShortageEvent` → `ReplenishmentApplicationService.replenishForShortage()`（唯一语义入口，R-61）建带明细的采购单；单价只取 `skus.cost_price`，取不到就整张单不建（R-60）。⚠️ 补货阈值仍**硬编码 10**（`InventoryRecord.calculateInventoryStatus:47-57`），故建议量对任何 SKU 最多 10 件 |
| **供应商主数据** | ⚪ | 无 `suppliers` 表（`schema.sql:375` 的 `supplier_id/supplier_name` 是自由文本，风险 R-01）；自动补货单固定挂 `DEFAULT_SUPPLIER` / "Default Supplier" |

### 6. 财务管理
| 功能 | 状态 | 说明 |
|---|---|---|
| 交易创建/列表/详情/按状态/按类型/按关联查 | ✅ | `FinanceController`：`POST`:63、`GET`:92、`GET /{transactionId}`:133、`/by-status`:150、`/by-type`:169、`/by-reference`:188，前端财务台账页（`/finance`，只在 user-web）已接 |
| 完成 / 失败 / 取消 | 🟡 | `PUT .../complete`:210、`.../fail`:228、`.../cancel`:246。⚠️ **三个过账动作目前没有 UI**——`api.js:445-447` 有 `complete/fail/cancelTransaction` 三个方法，但**没有一个页面调用**；因此 §6「费用核算」生成的应付分录会一直停在 `pending`，过账只能靠 API/探针调用。⚠️ ISS-140（P3，未修）：状态机三门违规时抛 `IllegalArgumentException` 被控制器就地吞掉，返回 **HTTP 200 + `Result.code` 失败**而不是 4xx |
| **费用核算（运费）** | ✅ | ~~有 `logistics_fees` 表，无核算逻辑~~ → 🔄 2026-10-10 两轮收口（ISS-137 / ISS-138，口径 R-55~R-59）：建物流单时按渠道口径**预估并落库**运费；货已交出（`PICKED_UP`/`IN_TRANSIT`/`DELIVERING`/`DELIVERED`/`DELIVERY_FAILED`）时生成 `EXPENSE` 应付分录，金额**只读已落库的 `total_fee`、从不重算**，幂等键 `(reference_id, 'LOGISTICS_ORDER', 'EXPENSE')`，删除单据会撤销分录。⚠️ 分录落库即 `pending`，过账仍需手工 `PUT .../complete` |
| **订单对账** | ⚪ | 无端点 |
| **报表生成** | 🟡 | ~~无端点~~ → 🔄 `GET /api/overview`（`OverviewController:47`）一次返回订单各状态计数、产品/仓库/客户/采购/平台集成总数、`totalRevenue` 与最近订单，前端 `Home.jsx` 已接。⚠️ 这是**首页聚合统计**，不是可对外的报表：无导出、无时间区间聚合、无成本/毛利口径 |

### 7. 客户管理
| 功能 | 状态 | 说明 |
|---|---|---|
| 创建/详情/更新/删除 | ✅ | 软删除；删除后不可再变更（`DELETED` 终态） |
| 客户列表 | ✅ | 分页 + ID/名称/邮箱/电话/状态/平台/关键字/注册时间区间筛选 |
| 状态变更 | ✅ | activate/deactivate/suspend 端点 + 前端按钮 |
| **平台客户导入** | ✅ | `/api/customers/import` 10 端点 + `CustomerImport.jsx`：从平台订单萃取买家 / 文件上传（JSON/CSV/xlsx），字段映射模板、预览、幂等 upsert、行级异常、任务化结果反馈。⚠️ 「平台买家接口直拉」这一路只对 `CUSTOM_SHOP` 与 `SAMPLE` 两种协议生效（客户数据线只有这两个注册键，见 §8） |
| **平台一键同步客户** | 🟡 | `POST /api/platform-integrations/{id}/sync-customers`:273。⚠️ `api.js:530-537` 把 `syncCustomers` / `syncInventory` / `syncLogistics` 三个按钮都真的发出去了，但**对八家协议平台这些线都会抛 `PLATFORM_ADAPTER_NOT_FOUND`**——`PlatformCustomerFetcher` / `PlatformWarehouseFetcher` / `PlatformLogisticsFetcher` 只注册了 `CUSTOM_SHOP` 与 `SAMPLE` 两个键 |
| **客户沟通记录** | ⚪ | 无模型无端点（P1） |
| **客户分类/标签** | ⚪ | 仅 `CustomerStatus` 枚举（P1） |

### 8. 平台集成
> 🔄 **2026-03-08 更新**（详见 [docs/spec/platform-integration.md](docs/spec/platform-integration.md) 与 [docs/tech/integration-design.md](docs/tech/integration-design.md)）：

| 功能 | 状态 | 说明 |
|---|---|---|
| 集成配置 CRUD/启停 | ✅ | **15 个端点**（`PlatformIntegrationController`:90-384，含 `GET /auth-specs` 协议字典与 `POST /{id}/push-fulfillment` 发货回写）。**出入参已改 DTO**（`PlatformIntegrationSaveRequest` / `PlatformIntegrationDto`，出参 `apiKeyMasked` + `hasApiSecret`/`hasAccessToken` 布尔，不再回传任何凭据）；落库为 AES-256-GCM 密文（`enc:v1:` 前缀）。⚠️ 前提是环境变量 `PLATFORM_CREDENTIAL_MASTER_KEY` 已注入，缺键时加密静默关闭（ISS-011 门槛 / ISS-064 ③） |
| 订单/客户/产品/库存/物流同步 | 🟡 | 14 端点，走 `PlatformFetcherRegistry` 按**认证协议**分派；`useSampleData=true` 时走示例链路。⚠️ **数据线覆盖并不齐**（实测注册键）：订单线 10 个键（8 家平台 + `CUSTOM_SHOP` + `SAMPLE`）最全，商品线只有 6 个（`SHOPIFY`/`MAGENTO`/`SHOPEE`/`OZON` + 两条通用），而**客户 / 物流 / 店铺仓库 / 财务四条线只有 `CUSTOM_SHOP` 与 `SAMPLE` 两个键**——对八家协议平台调用这些同步端点会抛 `PLATFORM_ADAPTER_NOT_FOUND`（`PlatformFetcherRegistry:131-136`，无静默回退，提示改用 `CUSTOM_SHOP` 或文件上传导入） |
| **按认证协议分派适配器** | ✅ | ~~`useSample ? sample : http` 布尔三元~~ → 注册表按 `PlatformAuthType` 分派。新增一家平台 = 注册一个 `type()` 返回该协议名的适配器，不改导入服务 |
| **Shopify（首家按官方协议对接）** | 🟡 | **代码符合官方文档规范、经 fixture 验证，但未经真实平台验证**：`X-Shopify-Access-Token` + `Link` 头 `page_info` 游标（从不发 `page`）+ 250 上限 + 429 退避；订单增量拉单 / 商品拉取 / 发货回写 / `test-connection`。**库存推送未实现**（需 `inventory_item_id`/`location_id` 主数据映射）。无沙箱凭据，27 例契约测试全是本地 `HttpServer` 桩 + 手写 fixture ⇒ **ISS-064（P0）** |
| **其余七家协议适配器** | 🟡 | ~~Amazon / eBay / Magento / AliExpress / Wish ⚪ **零适配器**~~ → 🔄 2026-10-20 复测：`MAGENTO`（订单+商品+回写+探测）、`OZON`（订单+商品+回写+探测）、`EBAY_SELL`（订单+回写+探测）、`SHOPIFY`、`SHOPEE`（订单+商品+探测）、`AMAZON_SP_API`（LWA 令牌 + 订单 + 探测）、`MERCADO_LIBRE`（令牌 + 订单 + 探测）**均已有代码**。⚠️ `Wish` 不在 `PlatformAuthType` 里（早已不是目标平台）。卡点仍在**平台侧准入**而非代码：开发者账号、APP 审核、OAuth/LWA + RDT、scope 申请，逐家前置见 [integration-design §11](docs/tech/integration-design.md) |
| **TEMU / TikTok Shop** | ⚪ | `PlatformAuthType` 里有这两个枚举值，但**零适配器**——选中它们的集成在拉单时直接抛 `PLATFORM_ADAPTER_NOT_FOUND` |
| 发货回写（反向写数据） | 🟡 | `PlatformFulfillmentPusher` 只注册了 4 个键：`SHOPIFY`/`MAGENTO`/`OZON`/`EBAY_SELL`。⚠️ `CUSTOM_SHOP` 没有回写适配器 |
| 连通性探测 | 🟡 | 9 个 `PlatformConnectionTester`（`CUSTOM_SHOP` + 8 家）。⚠️ ISS-133（P3，未修）：只有 `HttpPlatformConnectionTester` 走 `PlatformFailures` 共用传输层，其余 8 家各自外呼 |
| 定时调度 / 对账 | ⚪ | 全仓 `@Scheduled` **0 处**，同步只能人工点（`R-204`）；`syncFinanceFromPlatform` 仍是**无入口的死方法**（`PlatformIntegrationApplicationService:200` 只有声明，无调用点） |

### 9. 租户 / 用户 / 角色 / 审计 / 系统配置（README 早期未列，代码已实现）
| 功能 | 状态 | 说明 |
|---|---|---|
| 登录 / 刷新 / 登出 / 当前用户 | ✅ | `AuthController` 4 个端点（`/login`:67、`/refresh`:121、`/logout`:174、`/me`:206）+ 前端 `AuthContext`。🔄 早期记的「token 双重解包 bug」已不存在——`api.js` 响应拦截器解一层后返回 `response.data.data`，两个前端的 `Login.jsx` 直接把结果交给 `login()`，`AuthContext.jsx:77` 读 `.token`，与后端 `TokenResponse(token, refreshToken, username, tenantId, roles)` 对齐 |
| 登录安全（失败锁定） | ✅ | `LoginSecurityService` + `AccountLoginGuard`：`AuthController:71` 在认证**之前**判锁定阈值，失败计入安全事件（BR-12） |
| 租户管理 9 端点 | ✅ | ~~前端已实现 9 个调用，但**后端因角色判断恒 403**~~ → 🔄 该结论已被 ISS-061 的修复超越：`DataInitializationConfig:262-277` 幂等地给 `admin` 挂上 `PLATFORM_ADMIN`，`SecurityConfig:83` 的 `/api/tenants/** → hasRole(PLATFORM_ADMIN)` 实测返回 **200**（证据见 [security-findings.md S-02](docs/security-findings.md)）。⚠️ 副作用：`admin` 因此是跨租户平台管理员，演示租户隔离时不要拿它当「普通租户用户」 |
| 用户管理 11 端点 | ✅ | 前后端已打通 |
| 角色与权限管理 10 端点 | ✅ | 前后端已打通（权限点是自由字符串，无字典） |
| **审计留痕** | ✅ | `AuditLogController` 2 个读端点（`GET`:45、`GET /recent`:60）；写入侧是 `@Auditable` + `TenantIsolationAspect` AOP，`domain/audit`（`AuditLog` 实体 + `AuditActions` 名册 + `AuditLogRepository`），admin-web `AuditLogList.jsx` 已接 |
| **系统配置** | ✅ | `SystemConfigController` 2 个端点（`GET`:47、`PUT`:56）+ `domain/config`（`SystemSetting` 实体、`PlatformConfigKey` 字典），admin-web `SystemConfig.jsx` 已接 |
| **店铺与店仓绑定** | ✅ | `ShopController` 7 端点（含 `PUT /{id}/credentials`:135、`PUT /{id}/push-back-policy`:148）+ `ShopWarehouseController` 5 端点，见 [ADR-0013](docs/adr/0013-shop-entity-and-platform-connection-split.md) |
| **首页概览统计** | ✅ | `GET /api/overview`（`OverviewController:47`），`Home.jsx` 已接 |

## 技术栈

### 后端
Java 17 · Spring Boot **3.5.9** · Spring MVC · Spring Data JPA / Hibernate 6（`MySQL8Dialect`）· Spring Security + JJWT 0.12.5（HS512）· MySQL Connector/J · RocketMQ `rocketmq-spring-boot-starter` 2.2.3（服务端 5.3.2）· springdoc-openapi 2.6.0 · MapStruct 1.5.5.Final · Lombok 1.18.34 · Maven（无 wrapper）

### 前端（两个独立工程）
React 19.2 · Vite 7.2 · Ant Design 6.2 + `@ant-design/icons` 6.1 · React Router DOM 7.12 · axios 1.13 · JavaScript/JSX（无 TypeScript）· ESLint 9 flat config

## 如何运行

依赖版本与完整说明见 [docs/deployment.md](docs/deployment.md)。

### 前置
```bash
# 1) 建库（表结构由 Hibernate 自动创建，无需手工建表）
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS ecommerce_logistics_erp \
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"

# 2) 一键拉起数据库与消息中间件（MySQL 8.0 + RocketMQ 5.3.2）
cd scripts/docker && ./erp-services.sh start
#   其他管理命令：stop / restart / status / logs / ps / down / reset
#   端口映射：MySQL 宿主机 3306（容器内 3306）· RocketMQ namesrv 9876 · broker 10909/10911/10912 · broker 内嵌 proxy 8080/8081
```

> 数据库口令默认 `123456`（可被 `ERP_DB_PASSWORD` 覆盖）、库名 `ecommerce_logistics_erp`（`SPRING_DATASOURCE_URL`）、
> 字符集 `utf8mb4`，与 `backend/src/main/resources/application.yml:26-28` 的 `spring.datasource` 一致。
> 数据通过 Docker 命名卷持久化，重启容器不丢数据。
>
> 🔄 **RocketMQ 通道当前是开的**：`application.yml:42` 实测 `rocketmq.enabled: true`，即装配 `RocketMQTemplate`，
> 事件会真发到 broker（`localhost:9876`，已自动建 Topic）——**本机不起 broker 会看到发送失败日志**。
> 而 `rocketmq.internal-consumers.enabled: false`（`:45-46`）让 4 个 `@RocketMQMessageListener` 消费者**不装配**，
> 跨聚合编排只由进程内 `EventHandler` 唯一执行，因此不会出现「同一事件进程内 + MQ 各跑一遍」的重复副作用（ISS-052/054 已收口）。
> 需要真正闭环 MQ 链路时显式置 `internal-consumers.enabled=true`，但要同时保证只有一条通道在跑。

### 后端
```bash
cd backend
mvn clean compile
mvn spring-boot:run          # http://localhost:8082
```
首次启动会创建默认租户与管理员账号 **`admin` / `123456`**——口令取 `app.seed.default-tenant-admin-password`（`DataInitializationConfig:51`，未注入环境变量时退到开发值 `123456`，见 [security-findings.md S-04](docs/security-findings.md)）。
🔄 该账号还会被幂等授予 `PLATFORM_ADMIN` 角色（`DataInitializationConfig:262-277`），即 `admin` 是**跨租户的平台管理员**：能用它管理租户，但演示租户隔离时不要拿它当「普通租户用户」（`user-web` 的部分页面仍以 `DEFAULT_TENANT_ID` 取数，见 §4）。
API 文档：`http://localhost:8082/swagger-ui.html`。

### 前端
```bash
cd frontend/user-web  && npm install && npm run dev   # http://localhost:5120
cd frontend/admin-web && npm install && npm run dev   # http://localhost:5110
```
两个工程都没有配置 Vite proxy，前端直连 `http://localhost:8082/api`，依赖后端 CORS（已放行 `http://localhost:5120` 与 `http://localhost:5110`）。

### 测试与静态检查
```bash
cd backend          && mvn test        # 102 类 / 801 例，全绿（不采信管道退出码，用下一条核对）
node scripts/smoke/sum-surefire.js backend/target/surefire-reports   # 汇总 surefire 的 run/failures/errors/skipped
cd frontend/user-web  && npm run lint
cd frontend/admin-web && npm run lint
node scripts/smoke/audit-coverage-scan.js   # 扫没有 @Auditable 的写端点，豁免须写理由，--strict 下有缺口即退出 1
```
⚠️ 仓库里最后一次实测的 surefire 时间戳是 **2026-10-10 19:18**，而最后一个代码提交（`6927f01`）在 **22:19**——「801 例全绿」对 HEAD 严格说仍是**未复测**状态，动代码后请按上面两条重跑并核对。

### 端到端探针（真实 HTTP，非 Mock）
`scripts/smoke/` 是本仓唯一能证明「副作用真的发生了」的一层，判定一律回读数据库而不是看 HTTP 码：

| 脚本 | 验什么 |
|---|---|
| `smoke.sh` | 主链路 下单 → 发货 → 扣库存 → 事件联动，并逐项断言已修 ISS 的效果（覆盖 [smoke-test-checklist.md](docs/qa/smoke-test-checklist.md) §1/§2/§4） |
| `security-slice-smoke.sh` | 审计留痕、平台系统配置「存了要真生效」、登录失败锁定（BR-12） |
| `iss072-ship-paths-probe.sh` | 三条「发货」入口（PUT 状态 / batch-ship / `{id}/ship`）必须得到同一业务结果且扣减幂等 |
| `iss122-platform-ownership-probe.sh` | 平台集成按 ID 的写操作是否校验租户归属（跨租户伪造集成 ID） |
| `iss125-created-at-probe.sh` | 读路径重建聚合时 `createdAt` 不得被请求时刻伪造（PUT 不得覆盖库里 `created_at`） |
| `iss137-freight-persistence-probe.sh` | 运费在建单时按渠道口径落库、三条创建路径同结果、发货不重算 |
| `iss138-freight-posting-probe.sh` | 货已交出的四个入账门各记一条应付流水，且不重复记 |
| `iss143-shortage-replenishment-probe.sh` | 缺货自动单必须带明细行，且这张单能真走完 BR-09 到货入库 |
| `r32-tenant-sentinel-probe.sh` | 客户端把历史默认桶字面量当显式 `tenantId` 传上来时，服务端归一化为「由会话决定归属」 |

前置都是同一套：`./scripts/docker/erp-services.sh start` → `mvn -o spring-boot:run` → 跑脚本；环境变量 `SMOKE_BASE`（默认 `http://localhost:8082`）、`SMOKE_USER/SMOKE_PASS`（默认 `admin/123456`）。探针自己造的数据由探针自己复原。

## 已知问题与改进路线

按影响排序，详细论证见对应文档。**2026-10-20 复测后本表已重排**——早先的四条 P0/P1（认证链路、事务边界、`mvn test` 全绿与空断言清零、admin-web 接入后端）**均已关闭**，见本节末尾。

| 优先级 | 事项 | 出处 |
|---|---|---|
| P0 | 平台对接的全部「已完成」结论**只有本地 fixture 证据，没有一次真实平台调用**；且八家协议平台的**客户 / 物流 / 店铺仓库 / 财务四条数据线还没有适配器**（只有 `CUSTOM_SHOP` 与 `SAMPLE`） | ISS-064、本文 §8 |
| P0 | 自动化验证的水位仍全是单元级：102 类 / 801 例**全部脱离 Spring 上下文与数据库**，全仓 `@SpringBootTest` **0 个**；**无 CI、无覆盖率门禁**，且最后一次 `mvn test`（surefire 时间戳 2026-10-10 19:18）**早于**最后一个代码提交（22:19） | [testing.md](docs/testing.md) |
| P1 | 配置外部化只做了一半：`ERP_DB_PASSWORD` / `JWT_SECRET` / `PLATFORM_CREDENTIAL_MASTER_KEY` 已可被环境变量覆盖，但**默认值仍写进受版本控制的 `application.yml`**；仍无 profile；CORS origin 硬编码（[SecurityConfig.java:63](backend/src/main/java/site/hexaarch/ecommerce/logistics/infrastructure/config/SecurityConfig.java#L63)）、两个前端 `baseURL` 写死 `http://localhost:8082/api` | ISS-021、[ADR-0009](docs/adr/0009-config-externalization.md) |
| P1 | **两条用户能点到的断链**：① `PlatformIntegration.jsx:139/196/237` 的「同步客户 / 物流 / 库存」三个按钮对八家协议平台一律抛 `PLATFORM_ADAPTER_NOT_FOUND`（数据线只有 `CUSTOM_SHOP`/`SAMPLE` 两个注册键）；② 财务过账三门（complete/fail/cancel）**没有任何页面调用**，运费应付分录落库后一直停在 `pending` | 本文 §6/§7/§8、ISS-064 |
| P1 | 统一响应封装未收口：平台域已 DTO 化，但 `GET /api/warehouses`（[WarehouseController.java:116](backend/src/main/java/site/hexaarch/ecommerce/logistics/interfaces/controller/warehouse/WarehouseController.java#L116)）等仍直接序列化领域聚合；`Result.code` 仍为 `String`、无错误码字典 | [ADR-0011](docs/adr/0011-result-envelope-and-errors.md)、[error-codes.md](docs/api/error-codes.md) |
| P1 | `@Transactional` 的 `rollbackFor` **仍为 0 处**；事件是否在事务提交后才发布未经集成测试验证 | ISS-036、ISS-062 |
| P1 | 无定时调度：`@Scheduled` 0 处，同步只能人工点；`syncFinanceFromPlatform` 仍是无入口的死方法 | `R-204`、本文 §8 |
| P2 | 列表无分页的残留：`GET /api/logistics-channels` 全量返回（M-07）；`schema.sql` 快照与实体已差 3 张表 | 本文 §2、[docs/db/schema.sql](docs/db/schema.sql) |
| P2 | 不引入 Flyway（按既定边界保持）：`ddl-auto: update`、无版本化 DDL、唯一约束与索引缺口未收敛 | ISS-020、[ADR-0002](docs/adr/0002-hibernate-ddl-auto.md) |
| P2 | 时间戳时区口径混用：JDBC URL 是 `serverTimezone=UTC`，领域代码用 naive `LocalDateTime.now()` | ISS-126 |
| P3 | 财务状态机三门违规返回 **HTTP 200 + 失败码**；`applyPlatformUpdate` 可绕过状态门 | ISS-140、ISS-141 |
| P3 | 8 个 `PlatformConnectionTester` 未过共用传输层；导入作业与客户墓碑无回收入口；租户详情把集合字段序列化成空数组 | ISS-133、ISS-124、ISS-128 |
| P3 | 库存补货阈值硬编码 10（`inventory_records` 无 `safety_stock` 列）；权限点是自由字符串、无字典 | 本文 §5、[spec/warehouse.md](docs/spec/warehouse.md) |

**已关闭、不再占位**：认证链路（S-01~S-03 + ISS-061，`/api/tenants` 实测 200）、三条主链路的声明式事务（下单 / 审批 / 库存移动）、`mvn test` 全绿与 `assertTrue(true)` 空断言清零（实测 0 处）、admin-web 接入后端、平台域出入参 DTO 化与凭据加密入库、租户归属收口（`TenantAccessGuard` 被 20 个文件引用 + `TenantIsolationAspect`，ISS-050/117/120/135 已修）、MQ 双通道重复副作用（`internal-consumers.enabled=false`，ISS-052/054 已修）。

## 产品与功能文档

上一节是**技术视角**的文档；下面这些是**产品与功能视角**的补充文档，入口同样是 [`docs/README.md`](docs/README.md)。

| 分类 | 内容 |
|---|---|
| 产品层 [`docs/product/`](docs/product/) | 产品概览与成功指标、6 类用户画像与场景、功能全景**实现状态矩阵**、信息架构与页面清单、8 条端到端业务流程、术语表、用户故事与验收标准、版本路线图 |
| 规格层 [`docs/spec/`](docs/spec/) | 9 个模块的功能规格：状态机、业务规则 BR-xx、接口对照、领域事件、缺口与验收要点 |
| 质量层 [`docs/qa/`](docs/qa/) | 已知缺陷清单（ISS-xxx，含用户可感知影响与规避）、冒烟测试清单 |

> ⚠️ 提示：本节「功能实现状态」表格是**代码视角**的功能/端点清单；若要判断"用户能否真的用它完成工作"，请对照
> [`docs/product/03-feature-map.md`](docs/product/03-feature-map.md) 的端到端可用性分级（A/B/C/D 四级）。

## 贡献指南

1. 分支：从 `main` 切出 `feat/<scope>-<summary>` 或 `fix/<scope>-<summary>`。
2. 提交信息：中文或英文均可，但需说明**动机**而非仅"改了什么"；一次提交只做一件事。
3. 变更表结构 → 改 JPA 实体后**必须同步 `docs/db/schema.sql`**（当前 schema 由 Hibernate 生成，文档是唯一评审载体，见 [ADR-0002](docs/adr/0002-hibernate-ddl-auto.md)）。
4. 新增/变更端点 → 同步更新 [docs/api/api.md](docs/api/api.md) 的端点清单，并补 `@Operation`/`@Schema`。
5. 提交前本地执行：`mvn -f backend/pom.xml test`、两个前端的 `npm run lint` 与 `npm run build`。
6. 涉及架构取舍（新增依赖、改变分层边界、引入中间件）→ 在 `docs/adr/` 新增一条 ADR。

## 许可证

本项目采用研究与商业授权双轨制许可证，详细条款见 [LICENSE](./LICENSE)。简要说明：
- 个人研究和学习使用：允许自由使用、修改和分发
- 商业使用：需获得书面授权
- 禁止将本项目代码用于盈利性或商业化活动

## 联系方式

如有问题或建议，请联系项目维护者。
