# 需求 ↔ 技术实现追溯矩阵

> 定位：把产品文档的编号（`US-xxx` 用户故事、`BR-xx` 业务规则、`R-xxx` 需求条目、`F1~F8` 流程）与工程实现（控制器/端点/表/事件/类）以及缺陷编号（`ISS-xxx`、`S/M/L-xx`、`R-0x` 数据风险）**逐项对齐**，回答两个问题：① 这条需求由哪些代码承载；② 改这段代码要回改哪些文档。
> 单一权威不变：端点清单看 [api/api.md](../api/api.md)，表语义看 [db/data-dictionary.md](../db/data-dictionary.md)，缺陷立项看 [qa/known-issues.md](../qa/known-issues.md)。本文只做**映射**，不重述内容。
> 事实基线：2026-01-25 工作树（含未提交的 product-import / platform-fetcher 改动），实测口径见 §6。

## 1. 模块级追溯矩阵（9 个限界上下文）

### 1.1 订单（核心域）

| 维度 | 承载 |
|---|---|
| 需求 | `US-101~US-107`（E1）、`R-103/R-104/R-107`（v0.1）、`R-201`（事件链路）、`R-207`（报表）、`F1` |
| 规格 | [spec/order.md](../spec/order.md) `BR-01~BR-18`（18 条，全模块最多） |
| 接口 | `OrderController` → `/api/orders`，~~**4 个端点**~~ **2026-01-25 实测 9 个**：`POST /`、`GET /`（分页列表）、`GET /statuses`、`GET /number/{orderNumber}`、`GET /{id}`、`PUT /{id}/status`、`POST /{id}/cancel`、`DELETE /{id}`、`GET /customer/{customerId}` |
| 应用 | `application/service/OrderApplicationService`（事件发布 `:42,58,68`） |
| 领域 | 聚合 `Order`（⚠️ 未继承 `BaseAggregateRoot`，ISS-025）、实体 `OrderItem`、VO `OrderStatus`/`PaymentInfo`/`ShippingAddress`、服务 `OrderProcessingService`/~~`OrderSyncService`（桩）~~（**该类已删除**） |
| 事件 | 4：`OrderCreated`/`OrderStatusChanged`/`OrderShipped`/`OrderCompleted` → topic `order-events` |
| 表 | `orders`、`order_items`、`shipping_addresses`、`payment_infos` |
| 前端 | ~~`pages/order/{List,Detail,Create}`（仅 `CreateOrder`、`OrderDetail` 部分真实联网）~~ → **2026-01-25：订单三页已全接真实接口**（List 走 `GET /api/orders` 分页） |
| 缺陷/风险 | ~~ISS-001（订单号 null → 建单失败）~~（已修：`Order.java:195-198` 生成 `ORD+yyyyMMdd+UUID8`）、ISS-002/003（租户硬编码、状态绕过领域约束）~~、ISS-014（无列表接口）~~（订单列表接口已补）、⚠️ ISS-004 仍未修（`CON_FIRMED`↔`CONFIRMED` 由 `OrderMapper:195,213` 显式桥接，见 known-issues ISS-004）、~~ISS-036（无事务）~~（已修，110 处 `@Transactional`）、~~ISS-037（`IllegalArgumentException`→500）~~（已修，`:108-118` 映射 400）；**新增已修：ISS-056（`order_items` 主键未赋值 → 建单 500）**、**ISS-057（请求体绑定/类型不匹配 500 → 400）**；M-05、S-06、R-02/R-09 |
| 技术文档 | [backend-standards §5](backend-standards.md)（事务）、[error-codes §3](../api/error-codes.md)（`B001xx`）、[security-design §4](security-design.md)、[performance-capacity §5](performance-capacity.md)（`orders(tenant_id,status,created_at)`） |

⚠️ 需求↔实现的**语义缺口**：~~`R-104`「订单列表/分页」在端点层面尚不存在（`/api/orders` 只有 byId/byCustomer/create/status），产品文档里的「订单列表页」实为 mock（ISS-015）~~ → **2026-01-25 已闭合**：`GET /api/orders` 存在并返回 `PageResult`（实测 `{content,total,page,size,totalPages}`），前端列表页已去 mock。

### 1.2 物流（核心域）

| 维度 | 承载 |
|---|---|
| 需求 | `US-201~US-206`（E2）、`R-205`（渠道与价格规则/费用试算）、`R-209`（轨迹明细）、`R-210`（面单 PDF）、`F2/F3` |
| 规格 | [spec/logistics.md](../spec/logistics.md) `BR-01~BR-11` |
| 接口 | `LogisticsController` → `/api/logistics`，~~**5 个端点**~~ **2026-01-25 实测 13 个**（另：`LogisticsChannelController` 7、`LogisticsImportController` 12） |
| 应用 | `LogisticsApplicationService`（事件 `:55,69`；⚠️ 直连订单域仓储，[architecture §7](../architecture.md)） |
| 领域 | `LogisticsOrder`、`LogisticsChannel`；VO `LogisticsStatus`/`LogisticsFee`/`LogisticsLabel`/`PriceRule`；服务 `LogisticsOrderProcessingService`/`LogisticsTrackingService`（**仍是桩**：`:30-32` 注释「简化处理，直接返回当前状态」，不调渠道 API，故 `R-209` 轨迹明细未真正打通）/`LogisticsChannelService` |
| 事件 | 4 → topic `logistics-events`（`LogisticsCompletedEvent` 触发财务侧消费者，现状仅打印） |
| 表 | `logistics_orders`、`logistics_channels`、`logistics_labels`、`logistics_fees` |
| 前端 | ~~`pages/logistics/{List,Detail,Create}`（mock，`logisticsApi` 空壳）~~ → **2026-01-25 已接真实 `/api/logistics*`**（ISS-015/016 收尾） |
| 缺陷/风险 | ISS-005（状态任意跳转，🟡 已加终态守卫 `LogisticsOrder.updateStatus:201-209`，非法前置流转仍缺）、~~ISS-016（api 空壳）~~（已填）；R-04/R-10（`LogisticsOrderJpaEntity:57-60` 用 `Long` 存时间）、M-07 |
| 技术文档 | [migration-plan M4](../db/migration-plan.md)（`V8` 时间口径）、[performance-capacity §5](performance-capacity.md)（`(order_id)`、`(tenant_id,status)`、`(tracking_no)`） |

### 1.3 仓储与库存（支撑域）

| 维度 | 承载 |
|---|---|
| 需求 | `US-301~US-307`（E3）、`R-202`（入/出/调拨 + 变动流水）、`R-301`（缺货预警→采购建议）、`F4` |
| 规格 | [spec/warehouse.md](../spec/warehouse.md) `BR-01~BR-12` |
| 接口 | `WarehouseController` → `/api/warehouses`，**6 个端点** |
| 应用 | `WarehouseApplicationService`（⚠️ ~~`:52-94` 绕过聚合直接 `builder()+save`，`:93` 仍在~~ → 🔄 **2026-03-22 锚点订正**：绕过领域方法的写法现在是 `changeWarehouseStatus:79-83`（`warehouse.toBuilder().active(active).build()` 之后直接 `warehouseRepository.save`，`:82`）；`addLocation` 里的 `Location.builder()` 在 `:93-100`，但它是交给 `WarehouseManagementService.addLocation` 的构造入参，不算绕过；~~`:177` `UnsupportedOperationException` → 调拨接口必 500~~ **✅ 2026-01-25 已修**：`moveInventory:201-232` 按 ~~`INBOUND/OUTBOUND/TRANSFER`~~ **四类**分派（`INBOUND :207`、`OUTBOUND :215`、`ADJUSTMENT :221`、`TRANSFER :227`，`@Transactional` 在 `:200`），`TRANSFER` 走 `transferInventory:237-252`，未支持类型抛 `ValidationException`（`:230`）。⚠️ ~~调拨时 `sku/productId` 先回落到 `movement.getSku()` 再回落，且目标仓入库会把 `productId` 当 SKU 写进新行~~ → 🔄 **2026-03-22 复测：这条机制写反了，调拨不产生该问题**——`:247` 的 sku 回落是死代码（构造器 `InventoryMovement:54` 已 `requireNonNull(sku)`），`:248` 的 `productKey` 在 `productId` 非 null 时恒等于 `productId`，`:250-251` 给目标仓那行 productId/sku 各归其位（只有显式传 `"productId": ""` 空串才回落成 sku）。真正的键不一致在**另一条路径**：`inventoryIn:118` 与 `inventoryOut:133` 把同一个 `skuCode` 同时当 productId 和 sku 往下传 ⇒ 走 `/inventory/in｜/out`（含采购到货的服务端自动入库）建的库存行，`product_id` 列存的是 SKU 字符串；运行时库 `inventory_records` 现有 7 行**全部 `product_id = sku_code`**（实测），与走 `/move`（写真 productId）的行不是同一把键，叠加 R-05（缺 `uk(sku_code,warehouse_id)`）后同一物理 SKU 可落两行） |
| 领域 | `Warehouse`（未继承 `BaseAggregateRoot`）、`InventoryRecord`、`Location`；VO `InventoryStatus`/`InventoryMovement`；服务 `WarehouseManagementService`/`InventoryManagementService`/`InventoryCountService`。<br>🔴 **2026-01-25 关键约束（ISS-060）**：`WarehouseRepositoryImpl.findById` 会把该仓全部 `inventory_records` 重新水化成**新的领域实例**，与 `InventoryRecordRepository` 取到的实例不是同一个对象。因此库存定位**必须**走聚合入口 `Warehouse.findInventoryRecord(productId)`（`Warehouse.java`，已改 null-safe），再 `warehouse.updateInventory(...)`；用不受仓约束的 `findByProductId` 兜底会命中**别的仓库**的同 productId 行，导致调拨/出库 400 `Inventory record not found` |
| 事件 | 3 → topic `inventory-events`（`InventoryShortageEvent` → 建采购单，tenant 硬编码 `"DEFAULT_TENANT"`） |
| 表 | `warehouses`、`locations`、`inventory_records` |
| 前端 | ~~`pages/warehouse/{List,Detail,Create}`（mock）~~ → **2026-01-25 已接真实 `/api/warehouses`**；库存动作见 smoke 4.5 |
| 缺陷/风险 | ~~ISS-012（调拨必失败）~~（**已修**）、ISS-025；R-05（`inventory_records` 缺 `uk(sku_code,warehouse_id)`）、R-03（无 `@Version` → 并发丢写）；**新增已修：ISS-059（`InventoryRecord` 主键未赋值 + MapStruct 无条件实例化嵌套实体 → 入库/调拨 500）**、**ISS-060（跨仓库库存定位错误 → 调拨/出库/盘点 400）** |
| 技术文档 | [migration-plan M2](../db/migration-plan.md)（`V2` 唯一键）、[performance-capacity §5](performance-capacity.md)、[integration-design §7](integration-design.md)（库存 outbound） |

### 1.4 产品主数据（支撑域）

| 维度 | 承载 |
|---|---|
| 需求 | `US-401~US-405`（E4）、`R-105`（列表接口）、`R-303`（分类树与属性变体）、`R-203/R-204` 的导入侧 |
| 规格 | [spec/product.md](../spec/product.md) `BR-01~BR-10` |
| 接口 | `ProductController` → `/api/products`（**12**）+ `ProductImportController` → `/api/products/import`（**10**：target-fields、transforms、field-mappings GET/PUT、preview、upload、import、jobs、jobs/{id}、template）＝**22 个端点，全系统最大模块** |
| 应用 | `ProductApplicationService`、`ProductImportApplicationService`（均在途改动中） |
| 领域 | `Product`、`SKU`、`ProductCategory`；VO `ProductPrice`/`ProductAttribute`；新增导入聚合 `ProductImportJob` + `ProductFieldMapping`、VO `ImportJobStatus`/`ImportTargetField`/`FieldTransform`/`ImportedProductRow`；仓储新增 `ProductImportJobRepository`/`ProductFieldMappingRepository`；`ProductSpecification`（唯一的动态条件查询） |
| 事件 | 3：`ProductCreated`/`ProductUpdated`/`SKUCreated`（~~⚠️ **无 topic 路由**，`DomainEventPublisher:85-99` 未覆盖 product → 事件静默丢弃~~ **✅ 2026-01-25 已补 `product-events` 映射，ISS-039**；但 MQ 侧反序列化仍不通，ISS-052） |
| 表 | `products`、`skus`、**`product_import_jobs`**、**`product_import_job_errors`**、**`product_field_mappings`**（后 3 张为在途新增） |
| 前端 | ~~`pages/product/{List,Detail,Create}`（mock；`productApi` 空壳 ISS-016）~~ → **2026-01-25 已接真实 `/api/products`**（`productApi` 已填充，见 `services/api.js:240`；`CreateProduct.jsx`/`EditProduct.jsx` 是 `ProductForm.jsx` 的薄封装，真实调用在 `:41,91,94`） |
| 缺陷/风险 | ~~ISS-014、ISS-016~~（商品侧均已收口：**user-web `services/api.js` 现有 21 个 API 模块，唯一残留空壳是 `platformApi`（`:464-466`，注释「根据后端API补充」）**）；✅ 唯一有索引/唯一键的表：`products.idx_products_tenant`、`uk_products_external_ref(tenant_id,source_platform,source_product_id)` |
| 技术文档 | [performance-capacity §4](performance-capacity.md)（分页方案已在此首发：`PageResult`/`PageRequest`）、[migration-plan §7](../db/migration-plan.md)（基线必须包含新表）、[integration-design §7](integration-design.md)（`sku_mappings`） |

### 1.5 采购（支撑域）

| 维度 | 需求 `US-501~US-505`（E5）、`R-206`（到货入库联动）；规格 `BR-01~BR-12`；接口 `PurchaseController` `/api/purchases`（**9**）；领域 `PurchaseOrder`（✅ 继承 `BaseAggregateRoot`）+ `PurchaseOrderItem` + `PurchaseStatus`；事件 2；表 `purchase_orders`（PK 列名 `purchase_order_id`；**2026-01-25 补 `tenant_id` 列，ISS-046**）、`purchase_order_items`（BIGINT 自增，`R-11`）；~~前端 `pages/purchase/*`（mock）~~ → **2026-01-25 三页已去 mock**（`PurchaseList`/`PurchaseDetail`/`CreatePurchase` 全接 `purchaseApi`，列表主行改为采购单、金额筛选改前端本地过滤；⚠️ 直调必须显式传 `tenantId`，ISS-051）；缺陷 ~~ISS-006（状态未强校验）~~（🟡 approve/reject/in-transit/delivered/cancel 已按前置状态把关，实库实测「已送达再取消」被拒）、M-08（吞异常，🟡 重复审批仍返回 HTTP 200 + `code:"500"`） |
|---|---|

### 1.6 财务（支撑域）

| 维度 | 需求 `US-601~US-605`（E6）、`R-201`（事件驱动生成）、`R-207`/`R-310`（对账与利润报表）；规格 `BR-01~BR-11`；接口 `FinanceController` **`/api/finance/transactions`**（~~**8**~~ **2026-01-25 实测 9**，注意前缀不是 `/api/finance`；另有 `FinanceImportController` 11）；领域 `FinancialTransaction`（✅ 继承基类）+ `TransactionStatus`/`TransactionType`；事件 2 → topic `finance-events`；表 `financial_transactions`（~~金额 `DECIMAL(19,2)` ✅~~ → 🔄 2026-03-22 实测 **`decimal(38,2)`**，实体未声明 `precision`（`FinancialTransactionJpaEntity:33`），见 [data-dictionary R-09](../db/data-dictionary.md)；缺 `(reference_id,reference_type)` 唯一键**这点已再次确认**——该表 `@Table` 没有任何索引/唯一约束声明，并发重复入账拦不住）；🔄 2026-03-22 追加来源：**采购到货编排现在会写财务流水**（`PurchaseApplicationService:307-308`，`type=EXPENSE`、`reference_type="PURCHASE_ORDER"` 常量 `:45`），财务流水除手工建单与平台导入外多了第三个写入方，`R-201` 所说的「事件驱动生成」实际落地为**同事务同步调用**而非事件订阅（[../spec/purchase.md §7](../spec/purchase.md)）；~~前端 `pages/finance/*`（mock）~~ → **2026-01-25 已接真实 `/api/finance*`**（⚠️ 控制器有 `@PreAuthorize`，需 `finance:read`；`by-type` 要传枚举原名如 `INCOME`）；缺陷 ISS-014（🟡 财务列表接口已有）、M-08；风险 R-05 |
|---|---|

### 1.7 客户（支撑域）

| 维度 | 需求 `US-701~US-703`（E7）、`R-302`（沟通记录与分层标签，⚪ 完全缺失）；规格 `BR-01~BR-08`（全模块最少）；接口 `CustomerController` `/api/customers`（~~**3**~~ **2026-01-25 实测 9**：列表（多条件+分页）、`/status-options`、详情、创建、编辑、`/activate|/deactivate|/suspend`、删除；另有 `CustomerImportController` 10）；领域 `Customer`（✅ 继承基类）+ `CustomerStatus`（含 `DELETED`，`R-08` 软删除口径不一）；事件 2（`CustomerCreatedEvent:17` 含 email）；表 `customers`（UUID 主键，`R-11`）；~~前端 `pages/customer/*`（mock）~~ → **2026-01-25 已去 mock**（列表筛选分页、详情合并历史订单、编辑、状态按钮、导入全链路）；缺陷 ISS-014（已收口） |
|---|---|

### 1.8 平台集成（通用域）

| 维度 | 承载 |
|---|---|
| 需求 | `US-801~US-804`（E8）、`R-203`（Shopify 样板）、`R-204`（定时同步 + 同步日志）、`F7` |
| 规格 | [spec/platform-integration.md](../spec/platform-integration.md) `BR-01~BR-11`——**其中 BR-03~BR-10 全为 ⚪ 未实现** |
| 接口 | `PlatformIntegrationController` → `/api/platform-integrations`（~~**10**：CRUD + activate/deactivate + sync-orders/products/inventory~~ → **2026-01-25 实测 12**：CRUD + activate/deactivate + **5** 个 sync 端点，新增 `sync-customers`、`sync-logistics`） |
| 应用/领域 | `PlatformIntegrationApplicationService`、`PlatformIntegration` 聚合（✅ 继承基类）+ `PlatformName`/`PlatformStatus`；~~⚠️ `PlatformIntegrationServiceImpl:96,104,112` 三个 sync 为 `System.out` 桩~~ → **✅ 2026-01-25 已消除**：全仓 `System.out.println` **0 处**，该类做真实编排、失败抛 `BusinessException`（`PLATFORM_INTEGRATION_NOT_FOUND`/`_INACTIVE`/`_STORE_URL_MISSING`/`_INVENTORY_PUSH_FAILED`，`:158,162,166,198`） |
| 在途新增 | `domain/platform/client/{PlatformProductFetcher, PlatformProductFetchRequest, PlatformProductFetchException}` + `infrastructure/platform/{HttpPlatformProductFetcher, SamplePlatformProductFetcher}`——**首个真实 HTTP 适配器**（`RestClient` + `JdkClientHttpRequestFactory`，约定 `GET {storeUrl}/products`，`X-API-Key`/`X-API-Secret` 头），用于商品批量导入而非 Amazon/eBay/Shopify 官方 API。<br>🔄 2026-01-25 已扩到 **6 对** `HttpPlatform*Fetcher` / `SamplePlatform*Fetcher`，超时由 `platform.http.connect-timeout: 5s` / `read-timeout: 15s` 约束（ISS-044）；⚠️ 走哪一对由**请求参数** `useSampleData` 决定（如 `OrderImportApplicationService.java:87-88,432`）——客户端传 `true` 即在真实环境导入示例数据 |
| 事件 | 2（~~无 topic 路由~~ → **✅ 2026-01-25 已补 `platform-events`，ISS-039**） |
| 表 | `platform_integrations`（UUID 主键；`api_key`/`api_secret` **明文**，`R-12`；⚠️ 本轮按指示**未修**） |
| 前端 | ~~`pages/platform-integration/*`（mock）+ `pages/platform/{PlatformDashboard,SystemConfig}`（与 admin-web 逐字节重复）~~ → **2026-01-25**：`platform-integration/*` 已接真实 `/api/platform-integrations`（未建集成时返回 `data: []`）；`pages/platform/{PlatformDashboard,SystemConfig}` **仍是假数据**且无菜单入口（ISS-029） |
| 缺陷/风险 | ISS-011（密钥明文且原样返回，**按指示保持未修**）、~~ISS-013（sync 是桩）~~（已修）；S-05、~~S-03（安全规则路径写错）~~（已改为 `/api/tenants/**` + 兜底 `/api/**`）、M-04 |
| 技术文档 | [integration-design.md](integration-design.md)（本文域的技术方案权威）、[security-design §7](security-design.md)、[migration-plan M3](../db/migration-plan.md) |

### 1.9 多租户与权限（通用域）

| 维度 | 承载 |
|---|---|
| 需求 | `US-901~US-905`（E9）、`R-101`（JWT 校验与 authorities）、`R-102`（业务表 `tenant_id` + 隔离切面）、`R-110`（密钥外部化）、`R-306`（管理端重构）、`R-307`（角色-菜单-路由权限）、`F8` |
| 规格 | [spec/tenant-and-iam.md](../spec/tenant-and-iam.md) `BR-01~BR-12` + §5 鉴权目标时序（权威） |
| 接口 | ~~**33 个端点（4 控制器）**：`AuthController /api/auth`(3)~~ → **2026-01-25 实测 34 个（4 控制器）**：`AuthController /api/auth`(**4**：login/refresh/logout/**me**)、`UserController /api/users`(11)、`RoleController /api/roles`(10)、`TenantController /api/tenants`(9) |
| 应用/领域 | `CustomUserDetailsService`（~~⚠️ 权限恒空 `:40`~~ **✅ 2026-01-25 已修**：`:57-73` 由角色名生成 `ROLE_*` 并把 `role.getPermissions()` 逐条加进 authorities，ISS-041）、`UserServiceImpl/RoleServiceImpl/TenantServiceImpl`（⚠️ 实现落在 `application`，ISS-027）；`Tenant`/`User`/`Role`，无 `event` 目录（0 事件） |
| 基础设施 | `JwtTokenProvider`（~~⚠️ `validateToken` 恒 true `:126-128`~~ **✅ 已改为 `parseClaims(token) != null`**，`:189-191`；租户 claim 亦真实解析，`:141-144`）、`JwtAuthenticationFilter`（✅ `Bearer` 前缀已剥离 `:88`；~~⚠️ 自建实例 `:28-30`、authorities 恒空 `:46-48`~~ **✅ 已改为构造注入 `JwtTokenProvider`（`:31-35`），并从 token 的 roles/permissions 组装 authorities（`:46-72`）**）、`TenantContext`（~~⚠️ `clearTenantId` 无调用点~~ **✅ 已在 filter `finally` 调用（`:82`）**）、`TenantIsolationAspect`（~~⚠️ 逻辑注释 `:39-41`~~ **✅ 已生效 `:55-86`**，但仅校验入参中出现的 `tenantId`，读路径无隔离 → ISS-050）、`SecurityConfig`（~~`:31` CORS 单值、`:40` 路径错、无 `anyRequest()`~~ **✅ 现 CORS 放行 5173+5174（`:50`）、`/api/tenants/**` = `hasRole("PLATFORM_ADMIN")`、兜底 `.requestMatchers("/api/**").authenticated()`（`:63-67`**）；⚠️ 仍无 `httpBasic/formLogin/exceptionHandling` → 未认证与坏令牌都是 **HTTP 403 空响应体**，永远不返 401（ISS-017） |
| 表 | `tenants`、`users`、`roles` + `@ElementCollection` 桥表 `user_roles`（`UserJpaEntity.java:64-67`，实测 2 行）与 `role_jpa_entity_permissions`（不计入物理表口径）；~~⚠️ 仅 `users`/`roles` 有 `tenant_id`（+ 在途 `products`）~~ → **2026-01-25 实测 42 张表里 23 张有 `tenant_id` / 19 张没有**（ISS-010）；~~⚠️ `UserJpaEntity` **无角色关联**（角色不持久化，er-diagram §8）~~ **✅ 已持久化**（`user_roles`），但只存 role id 字符串、无 FK 指向 `roles` |
| 前端 | `auth/Login` + `tenant/*3` + `user/*3`（真实联网 9 页主要集中在此域）；~~`AuthGuard` 角色检查被注释~~ **✅ 已实现**：`user-web/src/components/Auth/AuthGuard.jsx:33-43` `requiredRoles` 不匹配渲染 403 `Result`，同文件另有平台管理员守卫 |
| 缺陷/风险 | ~~ISS-007/008/009~~（**均已修**：JWT 边界带 roles/permissions、`TenantContext` 清理、切面生效）、~~ISS-035~~；ISS-010（19 张表无 `tenant_id`）、**ISS-050（读路径无隔离，本域当前最高风险）**、**ISS-051（`PurchaseController:47` 仍收客户端 `tenantId`）**、ISS-021（凭据明文，按边界未修）、ISS-024（`username`/`email` 全局唯一）；~~S-01、S-02、S-03、S-04、M-01~M-06~~（S-01/S-02/S-03/M-01/M-02/M-06 已修，S-04 按边界保留）；**本轮新增并已修：ISS-058（`AccessDeniedException` 落 500 → 403 `ACCESS_DENIED`）**、**ISS-061（初始化在非 Web 线程读懒加载集合被吞 → `admin` 拿不到 `PLATFORM_ADMIN`，整个管理端恒定 403）**；M-07、L-01 |
| 技术文档 | [security-design.md](security-design.md)（本文域的技术方案权威）、[migration-plan M3](../db/migration-plan.md)、[observability §3.1](observability.md)（tenantId 进 MDC） |

## 2. 横切能力 → 文档映射

| 横切主题 | 技术文档 | 关联编号 |
|---|---|---|
| 响应信封与错误码 | [api/error-codes.md](../api/error-codes.md) | ISS-017、ISS-037、ADR-0011、`R-108` |
| 数据库迁移与约束 | [db/migration-plan.md](../db/migration-plan.md) | ISS-020、ISS-023/024、R-05/06/07/08/09/10/11、ADR-0002、`R-208` |
| 鉴权/授权/租户 | [security-design.md](security-design.md) | S-01~06、M-01~M-09、ISS-007~011、`R-101/102/110/307` |
| 编码规范与分层 | [backend-standards.md](backend-standards.md) | ISS-019/025/027、ADR-0001/0010/0012 |
| 前端工程 | [frontend-architecture.md](frontend-architecture.md) | ISS-015/016/018/026/029/030/032/033/035、ADR-0005/0008 |
| 可观测 | [observability.md](observability.md) | L-03、`R-132`（v1.0 观测必要条件） |
| 性能与容量 | [performance-capacity.md](performance-capacity.md) | M-07、R-06、v1.0 性能条件 |
| 外部平台集成 | [integration-design.md](integration-design.md) | BR-01~11、ISS-011/013、`R-203/204` |
| CI/CD 与门禁 | [ci-cd.md](ci-cd.md) | L-05、v1.0 质量条件 |
| 部署与运维 | [deployment-architecture.md](deployment-architecture.md) | ISS-020/021、v1.0 运维条件、ADR-0009 |
| 上手与协作 | [dev-guide.md](dev-guide.md) | — |

## 3. 反向追溯：代码变更 → 必须回改的文档

| 你改了这个 | 必须同步更新 |
|---|---|
| `interfaces/controller/*` 新增/删除端点 | [api/api.md §4](../api/api.md)（端点唯一权威）→ 对应 `spec/*.md` §接口对照 → 本文 §1 端点数 |
| `*JpaEntity` 字段 | [db/schema.sql](../db/schema.sql)（强制义务）→ [db/data-dictionary.md](../db/data-dictionary.md) → 必要时 [db/er-diagram.md](../db/er-diagram.md)；未来还需 `db/migration/V*.sql`（[migration-plan §8](../db/migration-plan.md)） |
| 枚举取值/名称 | [api.md §5](../api/api.md) + [data-dictionary §4](../db/data-dictionary.md)（`R-02` 三套口径），并检查 Mapper 桥接 |
| 领域事件（增删/载荷） | [architecture.md §3](../architecture.md) topic 表 + [backend-standards §6](backend-standards.md) + 对应 `spec/*.md` §领域事件 |
| 状态机迁移规则 | `spec/*.md` 的 `BR-xx`（先改）→ `product/08-user-stories.md` 验收标准 → `product/03-feature-map.md` 状态矩阵（[docs/README §4.2](../README.md) 的顺序） |
| 错误码 | [api/error-codes.md §3](../api/error-codes.md)（码表冻结）→ 前端 `ERROR_MESSAGES` 单一来源 |
| 配置项 | [deployment.md §3](../deployment.md) 配置面清单 + [ADR-0009](../adr/0009-config-externalization.md) |
| 页面/路由 | `product/04-information-architecture.md`（路由表与 `IA-xx`）+ [frontend-architecture.md §5](frontend-architecture.md) |
| 修复了某个安全/缺陷项 | [security-findings.md](../security-findings.md) 严重度回改 + [qa/known-issues.md](../qa/known-issues.md) 关闭 `ISS-xxx`（[security-design §10](security-design.md) 要求） |

## 4. 编号命名空间（本文新增/沿用约定）

| 前缀 | 归属 | 来源 | 注意 |
|---|---|---|---|
| `P-n` | **性能瓶颈** | 本文档新增：[performance-capacity.md §2](performance-capacity.md) | 新命名空间，已登记到 [docs/README §4.7](../README.md)；两位以内编号，不与 `R-0x` 混用 |
| `C-n` | **CI 前置整改项** | [ci-cd.md §2](ci-cd.md) | 同上，新命名空间 |
| `O-n` | **可观测阶段** | [observability.md §10](observability.md) | 同上 |
| `I-n` | **集成落地分期** | [integration-design.md §9](integration-design.md) | 同上 |
| `M-n`（迁移阶段） | **数据迁移里程碑** | [migration-plan §3](../db/migration-plan.md) | ⚠️ 与 [security-findings.md](../security-findings.md) 的 `M-xx`（设计缺陷）字母相同、语义不同；引用时必须写全「migration-plan M3」/「security-findings M-03」 |
| `BR-xx` | 业务规则 | 各 `spec/*.md` | ⚠️ **每模块各自从 BR-01 起，跨模块不唯一**（order 有 BR-01~18，customer 只有 BR-01~08）；引用必须带模块限定，如 `spec/order.md BR-07` |
| `R-0x` / `R-xxx` | 数据风险 / 需求条目 | data-dictionary / roadmap | 靠位数区分（[docs/README §4.7](../README.md)） |
| `ISS-xxx` / `US-xxx` / `S/M/L-xx` / `IA-xx` / `E1~E9` / `F1~F8` | 缺陷 / 故事 / 安全 / IA / 史诗 / 流程 | known-issues / 08-user-stories / security-findings / 04-IA / 08 / 05 | `E`/`F` 未在 README §4.7 清单内，建议补登记 |

## 5. 追溯完整性检查（可自动化）

| 检查 | 方法 | 当前结果 |
|---|---|---|
| 每个端点被某个 `spec` 引用 | 扫 `api.md §4` 路径 vs `spec/*.md` 接口对照表 | 待做（[ci-cd §6](ci-cd.md) 的端点漂移检查可顺带实现） |
| 每个 `US-xxx` 有端点或页面承载 | `product/08` 的 47 条 × 本文 §1 | 已知缺口：`US-7xx` 客服沟通（`R-302` ⚪）、`US-6xx` 对账报表（`R-207/310` ⚪） |
| 每张表被某个模块主张 | **40 张 `@Table`**（+ 2 张集合表 = 运行时库 42 张）vs 本文 §1 | ✅ 全覆盖（2026-01-25 复核：新增的 logistics/finance/shop-warehouse 三套导入上下文归各自模块，`shop_warehouses` 归 platform） |
| 每个事件有 topic | 22 个 `*Event` vs `DomainEventPublisher.getTopicForEvent`（`:110-128`）覆盖的 **8 个包** | ✅ **2026-01-25 已修（ISS-039）**：`product-events`/`customer-events`/`platform-events` 已补映射，`getTopicForEvent` 不再有返回 `null` 的分支。<br>⚠️ 但「有 topic」不等于「能消费」：22 个事件类无 `@JsonCreator`/空参构造，MQ 侧反序列化仍失败（**ISS-052**），且进程内 handler 与 MQ 消费者双通道仍会重复副作用（**ISS-054**） |
| 每个 `BR-xx` 有实现或缺口编号 | 105 条 BR × `ISS` | 规格文档已逐条标注 ✅/🟡/⚪/❌，本文不重复 |

## 6. 实测口径（本文数字怎么来的）

```bash
# 控制器 / 端点（基线 2026-03-08：20 个 @RestController / 198 个映射方法；2026-01-25 = 196，旧 13/100 已过期）
# ⚠️ 原文的 `--include=*.java` 与 `src/main/java/*/interfaces/controller/*.java` 两种写法在 zsh 下会直接
#    报 "no matches found"（前者要引号、后者层数不对），2026-03-08 换成下面这两条可原样重跑的命令：
grep -rl "@RestController" backend/src/main/java | wc -l
grep -rE "@(Get|Post|Put|Delete|Patch)Mapping" backend/src/main/java | grep -c 'interfaces/controller/'

# 表 / 事件 / 仓储 / Mapper（基线 2026-01-25：40 张 @Entity/@Table（运行时库 42 张）、22 个事件、26 领域仓储↔26 Impl、26 JpaRepository、12 Mapper）
grep -rl "@Entity" backend/src/main/java/**/jpa/entity | wc -l
find backend/src/main/java -path '*/domain/*/event/*.java' | wc -l

# 主源码规模（基线 2026-03-08：463 个 .java；测试 31 类 / 211 个 @Test；上一档 2026-01-25 = 444 / 26 类 / 164 个）
find backend/src/main -name '*.java' | wc -l
```

⚠️ **漂移警告**：本套文档的数字口径统一为 **20 控制器 / 198 端点 / 40 张 `@Table`（运行时库 42 张）/ 22 领域事件 / 463 主源码文件 / 31 测试类 211 `@Test`**（**2026-03-08 工作树实测**；端点与测试两档的增量全部来自平台对接这一轮）。历史三档：`bfbce09` =「12 / 83 / 21 / 23 / 220」，2026-01-25 =「13 / 100 / 24 / 22 / 266 / 10 类 53 测试」，2026-01-25 =「20 / 196 / 40 / 22 / 444 / 26 类 164 测试」——`bfbce09` 的**事件数 23 属计量错误**（多计 1 个），不是代码减少。任何引用这些数字的段落都必须同时标明**所依据的提交时点**——没有时点的数字就是不可核查的数字。上方 §5 的命令就是用来重算它们的。

⚠️ **本轮踩到的坑（务必照做）**：2026-01-25 复查时发现 `backend/target/classes` 里的 `.class` 比源码更新、且注解值与源码不一致（`line_number` vs `row_number`），说明**工作树源码与被验证过的编译产物存在漂移**——上一轮的结论可能对应的是另一份源码状态。因此本文件的每个数字都必须是**当场重跑**得到的，不能沿用上一次的同名结论；`mvn -o surefire:test` 不重新编译，尤其容易读到旧 `.class`。
