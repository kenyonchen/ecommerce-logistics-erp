# 数据字典

> 对象：**40 张 `@Table` 表** + 2 张 `@ElementCollection` 隐式集合表（`user_roles`、`role_jpa_entity_permissions`）= 运行时库 **42 张表**（计量时点 2026-01-25 工作树，逐列与 `Hibernate ddl-auto=update` 生成的实库比对一致；旧记录 21→24→30 均已过期）；**26 个领域仓储接口 + 26 个 Spring Data JPA 接口 + 26 个 `*RepositoryImpl` + 12 个 MapStruct 转换器**（`InventoryRecord`、各 `*ImportJob`/`*FieldMapping` 的转换写在 RepositoryImpl 内，无独立 Mapper）。
> 生成时间：2026-01-25，逐文件核对自 `infrastructure/persistence/jpa/entity/`。

## 1. Schema 的真实来源（务必先读）

| 事实 | 值 |
|---|---|
| 建表方式 | `spring.jpa.hibernate.ddl-auto: update` —— **由 Hibernate 依据实体注解自动建表/加列** |
| 迁移工具 | **无**。未引入 Flyway / Liquibase，无 `db/migration`、无 `schema.sql`、无 `data.sql` |
| 唯一权威 | JPA 实体代码。`docs/db/schema.sql` 是**快照**，不是执行入口 |
| `update` 的语义 | 只建表、只加列；**从不删列、不改类型、不删表** → 实体里的历史冗余列会长期残留 |
| 物理外键 | 仅由 @ManyToOne/@OneToOne 关联产生；裸 String 引用的列**没有外键** |
| 索引 | ~~已有 9 个实体声明 `@Table(indexes=...)`（`customers`、`orders`、`products` 与三类导入任务表等）；其余多数表仍无显式索引；唯一约束来自 `@Column(unique=true)`（如 `customers.email`）~~ **2026-01-25 复测**：~~**18 个实体**声明了 `@Table(indexes=...)`、共 **27 处 `@Index`**（按 `@Index` 数分布：`OrderJpaEntity` 4、`LogisticsOrderJpaEntity`/`ProductJpaEntity` 各 3、`CustomerJpaEntity`/`ShopWarehouseJpaEntity` 各 2、其余 **13 个实体各 1**（6 张 `*_import_jobs` + 6 张 `*_field_mappings` + `logistics_channels`）；4+3+3+2+2+13 = 27）~~；另有 ~~**10 个实体 11 处 `@UniqueConstraint`**~~ → 🔄 **2026-03-22 复测：11 个实体 12 处**；~~**18 个实体**声明了 `@Table(indexes=...)`、共 **27 处 `@Index`**~~ → **19 个实体 / 30 处 `@Index`**（新增的是 `PurchaseOrderJpaEntity` 3 处：`idx_purchase_orders_tenant`/`_status`/`_created_at`；分布 `OrderJpaEntity` 4 + `LogisticsOrderJpaEntity`/`ProductJpaEntity`/`PurchaseOrderJpaEntity` 各 3 + `CustomerJpaEntity`/`ShopWarehouseJpaEntity` 各 2 + 其余 13 个实体各 1 ＝ 4+3+3+3+2+2+13 = 30）；运行时库 `information_schema.statistics` 实测已确认这三条索引存在。因此"仍无显式索引"的名单要**去掉 `purchase_orders`**：仍为 `users`/`roles`/`tenants`/`warehouses`/`inventory_records`/`locations`/`financial_transactions`/`platform_integrations` 等（实测 `warehouses` 只有 PK＋2 个 UNIQUE、`inventory_records` 只有 PK＋2 个外键索引、`locations` 只有 PK＋1 个外键索引、`financial_transactions` **只有 PK**）（详见 R-06）；唯一约束也来自 `@Column(unique=true)`（如 `customers.email`、`users.username`） |

**要变更表结构的正确顺序**：改实体 → 本地重启验证 Hibernate 生成的 DDL（`show-sql: true` 已开启）→ 手工把变更同步进 `docs/db/schema.sql`。若要把 schema 变为可评审的制品，需按 [ADR-0002](../adr/0002-hibernate-ddl-auto.md) 记录的迁移路径切换到 Flyway。

## 2. 表总览

| # | 表 | JPA 实体 | 上下文 | 主键策略 | 领域仓储接口 | Spring Data 接口 |
|---|---|---|---|---|---|---|
| 1 | `tenants` | TenantJpaEntity | tenant | String（领域赋值） | TenantRepository | TenantJpaRepository |
| 2 | `users` | UserJpaEntity | tenant | String | UserRepository | UserJpaRepository |
| 3 | `roles` | RoleJpaEntity | tenant | String | RoleRepository | RoleJpaRepository |
| 4 | `role_jpa_entity_permissions` | （@ElementCollection 桥表） | tenant | 复合 | — | 随 RoleRepository |
| 5 | `customers` | CustomerJpaEntity | customer | String（应用层 `"CUST-"+UUID前8位`） | CustomerRepository | CustomerJpaRepository |
| 6 | `orders` | OrderJpaEntity | order | String | OrderRepository | OrderJpaRepository |
| 7 | `order_items` | OrderItemJpaEntity | order | String | — | 随 OrderRepository |
| 8 | `shipping_addresses` | ShippingAddressJpaEntity | order | String | — | 随 OrderRepository |
| 9 | `payment_infos` | PaymentInfoJpaEntity | order | String | — | 随 OrderRepository |
| 10 | `logistics_orders` | LogisticsOrderJpaEntity | logistics | String | LogisticsOrderRepository | LogisticsOrderJpaRepository |
| 11 | `logistics_channels` | LogisticsChannelJpaEntity | logistics | String | LogisticsChannelRepository | LogisticsChannelJpaRepository |
| 12 | `logistics_labels` | LogisticsLabelJpaEntity | logistics | String | — | 随 LogisticsOrderRepository |
| 13 | `logistics_fees` | LogisticsFeeJpaEntity | logistics | String | — | 随 LogisticsOrderRepository |
| 14 | `warehouses` | WarehouseJpaEntity | warehouse | String | WarehouseRepository | WarehouseJpaRepository |
| 15 | `locations` | LocationJpaEntity | warehouse | String | — | 随 WarehouseRepository |
| 16 | `inventory_records` | InventoryRecordJpaEntity | warehouse | String（应用层赋值） | InventoryRecordRepository | InventoryRecordJpaRepository |
| 17 | `products` | ProductJpaEntity | product | String | ProductRepository | ProductJpaRepository |
| 18 | `skus` | SkuJpaEntity | product | String | — | 随 ProductRepository |
| 19 | `purchase_orders` | PurchaseOrderJpaEntity | purchase | String | PurchaseOrderRepository | PurchaseOrderJpaRepository |
| 20 | `purchase_order_items` | PurchaseOrderItemJpaEntity | purchase | **BIGINT AUTO_INCREMENT** | — | 随 PurchaseOrderRepository |
| 21 | `financial_transactions` | FinancialTransactionJpaEntity | finance | String | FinancialTransactionRepository | FinancialTransactionJpaRepository |
| 22 | `platform_integrations` | PlatformIntegrationJpaEntity | platform | UUID @GeneratedValue | PlatformIntegrationRepository | PlatformIntegrationJpaRepository |
| 23 | `order_import_jobs` | OrderImportJobJpaEntity | order | String（应用层 UUID） | OrderImportJobRepository | OrderImportJobJpaRepository |
| 24 | `order_import_job_errors` | OrderImportJobErrorJpaEntity | order | String | — | 随 OrderImportJobRepository |
| 25 | `order_field_mappings` | OrderFieldMappingJpaEntity | order | String | OrderFieldMappingRepository | OrderFieldMappingJpaRepository |
| 26 | `product_import_jobs` | ProductImportJobJpaEntity | product | String（应用层 UUID） | ProductImportJobRepository | ProductImportJobJpaRepository |
| 27 | `product_import_job_errors` | ProductImportJobErrorJpaEntity | product | String | — | 随 ProductImportJobRepository |
| 28 | `product_field_mappings` | ProductFieldMappingJpaEntity | product | String | ProductFieldMappingRepository | ProductFieldMappingJpaRepository |
| 29 | `customer_import_jobs` | CustomerImportJobJpaEntity | customer | String（应用层 UUID） | CustomerImportJobRepository | CustomerImportJobJpaRepository |
| 30 | `customer_import_job_errors` | CustomerImportJobErrorJpaEntity | customer | String | — | 随 CustomerImportJobRepository |
| 31 | `customer_field_mappings` | CustomerFieldMappingJpaEntity | customer | String | CustomerFieldMappingRepository | CustomerFieldMappingJpaRepository |
| 32 | `logistics_import_jobs` | LogisticsImportJobJpaEntity | logistics | String（应用层 UUID） | LogisticsImportJobRepository | LogisticsImportJobJpaRepository |
| 33 | `logistics_import_job_errors` | LogisticsImportJobErrorJpaEntity | logistics | String | — | 随 LogisticsImportJobRepository |
| 34 | `logistics_field_mappings` | LogisticsFieldMappingJpaEntity | logistics | String | LogisticsFieldMappingRepository | LogisticsFieldMappingJpaRepository |
| 35 | `finance_import_jobs` | FinanceImportJobJpaEntity | finance | String（应用层 UUID） | FinanceImportJobRepository | FinanceImportJobJpaRepository |
| 36 | `finance_import_job_errors` | FinanceImportJobErrorJpaEntity | finance | String | — | 随 FinanceImportJobRepository |
| 37 | `finance_field_mappings` | FinanceFieldMappingJpaEntity | finance | String | FinanceFieldMappingRepository | FinanceFieldMappingJpaRepository |
| 38 | `shop_warehouses` | ShopWarehouseJpaEntity | platform | String | ShopWarehouseRepository | ShopWarehouseJpaRepository |
| 39 | `shop_warehouse_import_jobs` | ShopWarehouseImportJobJpaEntity | platform | String（应用层 UUID） | ShopWarehouseImportJobRepository | ShopWarehouseImportJobJpaRepository |
| 40 | `shop_warehouse_import_job_errors` | ShopWarehouseImportJobErrorJpaEntity | platform | String | — | 随 ShopWarehouseImportJobRepository |
| 41 | `shop_warehouse_field_mappings` | ShopWarehouseFieldMappingJpaEntity | platform | String | ShopWarehouseFieldMappingRepository | ShopWarehouseFieldMappingJpaRepository |
| 42 | `user_roles` | （`UserJpaEntity:65` 的 `@CollectionTable` 集合表） | tenant | 复合（user_id + role） | — | 随 UserRepository |

> 🆕 第 32–42 行为 2026-01-25 补齐：上方 §2 的清单原先只到 31 行，物流/财务/店仓三套导入上下文（`*_import_jobs`、`*_import_job_errors`、`*_field_mappings`）、`shop_warehouses` 与 `user_roles` 集合表未登记，导致清单与运行时库的 42 张表对不上。逐列定义见 [schema.sql](./schema.sql)（已同步）。
> ⚠️ 其中 6 张 `*_import_job_errors` 在 2026-01-25 之前**在真实库里根本不存在**——行号列 `row_number` 撞 MySQL 8 保留字使建表 DDL 失败，已改名 `line_number` 修复（R-13 / ISS-049）。

列的完整定义见 [schema.sql](./schema.sql)，本文件不重复；下面只记录**列级语义**中不自明或有坑的部分。

## 3. 列级语义要点

### 通用域
- `tenants.status` / `users.status`：普通 `VARCHAR`，值由构造函数写死 `"ACTIVE"`。领域侧存在 `TenantStatus{ACTIVE,INACTIVE,SUSPENDED,DELETED}`、`UserStatus{ACTIVE,INACTIVE,LOCKED,PENDING}`，但**没有任何持久层映射代码**，写入什么完全取决于应用层传字符串——枚举约束实际不存在。
- `users.username` / `users.email`：**全局唯一**（`@Column(unique=true)`）。这与 `tenant_id` 的共存在语义上矛盾：跨租户重名注册会被数据库拒绝。若要"租户内唯一"，需改为 `UNIQUE(tenant_id, username)`。
- `role_jpa_entity_permissions.permission`：值列为自由字符串，权限点无字典表、无校验（`POST /api/roles/{id}/permissions` 可写入任意串）。
- `users.password`：BCrypt 摘要（`BCryptPasswordEncoder`），列长 255 足够，但未显式声明长度。

### 订单 / 物流
- `orders.created_at` / `updated_at`：**BIGINT（epoch millis）**，与 `tenants`/`customers` 的 `DATETIME(6)` 不一致；同一张业务表内混用两种时间口径（`orders` 用 epoch，`order_items` 无时间列）。
- `orders.customer_name` / `customer_email`：下单时的**客户信息快照**，与 `customer_id` 并存；更新客户不会回写历史订单——快照冗余是有意设计，但无注释说明，易被误读为脏数据。
- `order_items` 只存 `product_name` + `sku_code`，**不持有 product 外键**：商品改名/下架不影响历史订单，这是订单不可变性的正确做法，但代价是无法 join 出商品当前状态。
- `logistics_orders.status`：持久化为 **JPA 枚举名**，与领域枚举名不同（见 §4）。
- `logistics_labels.label_data`：`@Lob` → `TEXT`，用于存面单内容；当前生成逻辑是桩（未真实产出面单）。

### 仓储 / 产品
- `inventory_records.id` 由应用层赋值且**无唯一约束**，而 `findBySkuCodeAndWarehouseId` 返回 `Optional`——一旦并发插入重复行，查询会抛 `NonUniqueResultException`。这是最需要立刻补的约束。
- `locations` 有 `capacity`/`occupied` 两个 INT，`warehouses` 有 `capacity`；两者无一致性校验（库位容量之和可以超过仓库容量）。
- `products.category_id`：`UUID.randomUUID().toString()` 现场拼装而来（`ProductApplicationService`），**指向不存在的 categories 表**，因此该列只能作为分类名的伴生标识，无法 join。
- `skus.weight` / `dimensions`：自由字符串（`dimensions` 例如 "10x10x10"），无单位约定——物流计费 `price_per_gram` 依赖重量，但单位未在 schema 层面约束。

### 采购 / 财务
- `purchase_orders` 主键列名是 `purchase_order_id`（其余表都是 `id`/业务名 PK），MyBatis/JPA 混用或写手写 SQL 时极易踩坑。
- `purchase_orders.supplier_id` / `supplier_name`：**无 suppliers 主数据表**，供应商信息只有下单快照。
- ~~`financial_transactions.amount` 用 `DECIMAL(19,2)`（正确），但 `orders.total_amount`、`skus.price`、`logistics_fees.*` 用 `DOUBLE`（不正确）。~~ → 🔄 **2026-03-22 实测更正**：`financial_transactions.amount` 是 **`decimal(38,2)`** 不是 19,2（`FinancialTransactionJpaEntity:33` 未声明 `precision/scale`，Hibernate 默认 38）。运行时库 5 个 decimal 列实测：`financial_transactions.amount`、`purchase_orders.total_amount`、`purchase_order_items.unit_price`/`total_price` = **`decimal(38,2)`**，只有 `payment_infos.amount` = `decimal(19,2)`。`orders.total_amount`、`skus.price`、`logistics_fees.*` 用 `DOUBLE`（不正确）**这一点不变**（12 个 double 列 / 6 张表，见 R-09 与 [migration-plan.md](migration-plan.md) `V7`）⇒ 真正的风险是「浮点金额」+「同一语义两种 DECIMAL 精度」并存。
- `financial_transactions.reference_id` + `reference_type` 多态引用：无外键、无枚举约束，`reference_type` 的合法取值只存在于调用方代码里。
- `financial_transactions.currency`：`@Column(length = 3)`，是全库唯一显式收紧长度的列。

### 客户 / 导入任务
- `customers.email`：`@Column(unique=true)` **全局唯一**（跨租户）；领域服务把空串归一为 `NULL` 并统一小写后再查重，落库值恒为小写。
- `customers` 导入幂等键 `(tenant_id, source_platform, external_buyer_id)`：只有查询匹配，**无数据库唯一约束**（仅 `idx_customers_tenant`/`idx_customers_status` 两个索引）——并发导入理论上可产生重复买家行。
- `customers.company`：VARCHAR(256)，2026-01-25 补全（映射→预览→落库→DTO 全链路贯通）；`ddl-auto: update` 会在既有库上自动加列。
- `customer_status` 落库为枚举名 `ACTIVE/INACTIVE/SUSPENDED/DELETED`（`@Enumerated(STRING)`），`DELETED` 即软删除终态，行不物理移除。
- `*_import_job_errors` 的 `message` 由应用层截断至 300 字符；错误行与任务同库持久化，任务列表可事后回溯。
- 三类导入任务表（order/product/customer）结构同构：String UUID 主键 + `tenant_id` 索引 + 计数列（total/success/failure/skipped）+ 起止时间。


### 平台集成
- `platform_integrations.api_key` / `api_secret`：**明文列**，无加密注解、无脱敏。写入即落库，且 `GET /api/platform-integrations/{id}` 会把 `apiSecret` 原样返回给前端。

## 4. 枚举存储口径（三套并存 —— 最需要统一的部分）

| 口径 | 出现位置 | 存储值示例 |
|---|---|---|
| A. `@Enumerated(EnumType.STRING)` | `orders.status`、`logistics_orders.status`、`inventory_records.status`、`customers.status`、`platform_integrations.platform_name/status` | `PENDING`、`CONFIRMED` |
| B. 手写 String + 领域 code | `purchase_orders.status`、`financial_transactions.type/status`（经 `*Mapper` 转换） | 小写 `pending`、`income` |
| C. 自由 String（构造函数默认值） | `tenants.status`、`users.status` | `ACTIVE`（无校验） |

**没有任何列使用 `EnumType.ORDINAL`**——这一点是安全的（枚举新增常量不会导致历史数据错位）。

口径 A 内部还有 **领域枚举名 ≠ JPA 枚举名** 的桥接，靠 MapStruct/手写 switch 硬映射，新增常量极易漏改：

| 上下文 | 领域枚举 | JPA 枚举（= 落库值） |
|---|---|---|
| order | `OrderStatus.CON_FIRMED` | `OrderStatusJpaEntity.CONFIRMED` |
| logistics | `COLLECTED` | `PICKED_UP` |
| logistics | `DELIVERING` | `OUT_FOR_DELIVERY` |
| logistics | `FAILED` | `EXCEPTION` |
| logistics | `LABEL_GENERATED` | `LABEL_GENERATED` |

API 查询参数按**领域枚举常量名**绑定（见 [api.md §5](../api/api.md#5-枚举取值)），因此同一个状态在「API 入参 / 领域 / 数据库」三层的拼法可能各不相同，排障时必须逐层确认。

## 5. 仓储自定义查询方法

| Spring Data 接口 | 方法 | 备注 |
|---|---|---|
| UserJpaRepository | `findByUsername` / `findByEmail` / `findByTenantId` | |
| RoleJpaRepository | `findByNameAndTenantId` / `findByTenantId` | |
| TenantJpaRepository | `findByName` / `findByContactEmail` | |
| ProductJpaRepository | `findByName` / `findBySkuCode` | 全仓**唯一** `@Query`：`JOIN p.skus s WHERE s.skuCode = :skuCode` |
| WarehouseJpaRepository | `findByWarehouseCode` / `findByWarehouseName` / `findByActiveTrue` | |
| InventoryRecordJpaRepository | `findBySkuCodeAndWarehouseId`(Optional) / `findByWarehouseId` / `findBySkuCode` | Optional 语义缺唯一约束 |
| OrderJpaRepository | `findByOrderNumber` / `findByCustomerId` / `findByStatus` | |
| LogisticsOrderJpaRepository | `findByLogisticsOrderNumber` / `findByOrderId` | |
| LogisticsChannelJpaRepository | `findByChannelCode` / `findByChannelName` / `findByActiveTrue` | |
| PlatformIntegrationJpaRepository | `findByStatus` | |
| PurchaseOrderJpaRepository | `findByStatus(String)` / `findBySupplierId` / `findByWarehouseId` | |
| FinancialTransactionJpaRepository | `findByStatus(String)` / `findByType(String)` / `findByReferenceIdAndReferenceType` | |
| CustomerJpaRepository | （无自定义方法） | |

**没有任何列表查询支持分页、排序、时间范围或关键字检索**（仓储层就没有 `Pageable` 方法签名）。

## 6. 风险清单

编号与 `schema.sql` 内注释一致。

| ID | 风险 | 严重度 | 说明 |
|---|---|---|---|
| R-01 | 引用缺失主表 | 高 | `supplier_id`、`category_id` 无对应表；`customer_id`/`sku_code`/`order_id`/`warehouse_id`/`reference_id` 均裸引用无外键 |
| R-02 | 枚举三套口径 | 高 | 见 §4，跨层名称漂移 |
| R-03 | 无 `@Version` 乐观锁 | 高 | 并发更新同一订单/库存记录将丢失更新（Last-Write-Wins） |
| R-04 | 时间戳填充三套并存 | 中 | 构造器 now（Role/Tenant/User，且 `updated_at` **不会自动刷新**）、纯应用层赋值（Customer/PlatformIntegration，忘赋值直接违反 NOT NULL）、手工 set epoch（Order/Logistics/Inventory）。全仓无 `@EnableJpaAuditing`/`@PrePersist`/`@CreationTimestamp`。<br>✅ 2026-01-25：`PurchaseOrder.Builder` 原先丢弃 `createdAt`（字段曾为 `final`，读改写时一律重取 `now()`），导致每次 approve/in-transit/delivered 都把 `created_at` 刷新——已改为 builder 还原原值，仅新建时才 `now()` 兜底（ISS-048），实库回归验证连续 3 次写入后 `created_at` 不变 |
| R-05 | 唯一约束缺失 | 高 | `inventory_records(sku_code,warehouse_id)`、`locations(warehouse_id,location_code)`、~~`purchase_order_items(purchase_order_id,sku_code)`~~、`financial_transactions(reference_id,reference_type)`——2026-01-25 复测实库这 4 张表仍只有 PRIMARY + FK 索引，结论不变 → 🔄 **2026-03-22 复测：4 条里已补 1 条**，`purchase_order_items` 现在有 `uk_po_item(purchase_order_id, sku_code)`（声明在 `PurchaseOrderItemJpaEntity:25-26`，运行时库 `information_schema.statistics` 实测该索引 `non_unique=0` 已建），所以 R-05 还剩 **3 条**：`inventory_records`（实测只有 PK + `location_id`/`warehouse_id` 两个外键索引）、`locations`（只有 PK + `warehouse_id` 外键索引）、`financial_transactions`（**只有 PK，连外键索引都没有**） |
| R-06 | 外键列无索引 | 中 | ⚠️ 2026-01-25 更正：并非「所有」。① 声明了 `@ManyToOne`/`@OneToMany(mappedBy)` 的关联列由 MySQL 自动建 FK 索引（`purchase_order_items.purchase_order_id`、`inventory_records.warehouse_id`/`location_id`、`locations.warehouse_id`、6 张 `*_import_job_errors.job_id` 等）；② `*_import_jobs`/`*_field_mappings`/orders/customers/products/logistics_orders/shop_warehouses 的 `tenant_id` 已带显式 `@Index`（全仓 27 个 `@Index`）。**仍无索引的 `tenant_id`：`users`、`roles`、`warehouses`、`platform_integrations`、`financial_transactions`、`purchase_orders`（6/23）**；裸字符串引用列（`order_items.sku_code`、`inventory_records.sku_code`、`financial_transactions.reference_id`）依旧全表扫描 |
| R-07 | 多租户隔离缺位 | 高 | ⚠️ 2026-01-25 实测：42 张表里 **23 张有 `tenant_id`、19 张没有**（旧口径「24 张里 5 张」已过期，本次给 `purchase_orders` 补了列）。但**有列 ≠ 隔离生效**：`PurchaseApplicationService` 的 `findAllPurchaseOrders()` / `findPurchaseOrdersByStatus()` / `findPurchaseOrderById()` 均不带租户条件，列表与详情仍是跨租户可读（ISS-050）；`users.username/email` 全局唯一与租户模型冲突也未变 |
| R-08 | 软删除无统一策略 | 中 | `active` 布尔（products/skus/warehouses/locations/channels）、`DELETED` 状态（customers）、`orphanRemoval` 硬删除（其余）三种并存 |
| R-09 | 金额类型混用 | 高 | ⚠️ 2026-01-25 更正类型口径：未显式声明 precision 的 `BigDecimal` 实际生成 **`DECIMAL(38,2)`**（`purchase_orders.total_amount`、`purchase_order_items.unit_price`/`total_price`、`financial_transactions.amount`），只有 `payment_infos.amount` 显式写了 `precision=19,scale=2` 才是 `DECIMAL(19,2)`。`DOUBLE`（orders/order_items/skus/logistics_*）与 DECIMAL 并存、浮点用于货币有精度风险这两点不变 |
| R-10 | 时间类型混用 | 中 | `DATETIME(6)` 与 `BIGINT` epoch 并存 |
| R-11 | 主键策略与命名不统一 | 中 | String 领域赋值（15 表）/ UUID @GeneratedValue（2 表）/ IDENTITY 自增（1 表）；PK 列名 `id` vs `purchase_order_id` vs `transaction_id` |
| R-12 | 敏感数据明文 | 高 → 🟢已缓解（有运维门槛） | ~~`platform_integrations.api_secret`、`api_key` 明文入库并原样返回 API。**2026-01-25 复核：仍未修**——本轮明确排除该项（同 ISS-011）~~ → 🔄 **2026-03-22 按代码复测：已修**。列里存的是 AES-256-GCM 密文（`enc:v1:base64(iv‖ct‖tag)`，`infrastructure/security/CredentialCipher.java:18,31,33`），加解密收敛在仓储边界（`PlatformIntegrationRepositoryImpl:80-92`，含 `access_token`/`refresh_token` 共四列）；API 出参不含明文（`PlatformIntegrationDto:33,35,37` 只给 `apiKeyMasked` + `hasApiSecret`/`hasAccessToken`），`docs/db/schema.sql:420-422` 的列注释已同步为密文口径。**两个必须记住的数据事实**：① `platform.credential.master-key` 未注入时（`application.yml:60` 是 `${PLATFORM_CREDENTIAL_MASTER_KEY:}` 空缺省）`CredentialCipher:42-44` 只 WARN 后**按明文读写**，此时本条风险原样存在；② 密文有膨胀——`api_key`/`api_secret` 仍是 `VARCHAR(255)`，约只容 180 字符原文，`access_token`/`refresh_token` 给到 1024 ≈ 740 字符（`schema.sql:437-438`）。修复前已入库的明文仍须平台侧轮换才算解除泄漏。 |
| R-13 | 保留字撞名 | 中 | 🆕 2026-01-25（ISS-049，已修）：6 张 `*_import_job_errors` 的行号列原名 `row_number`，是 MySQL 8 保留字，Hibernate 未加引号 → `create table` 报 1064，这 6 张表在 `ddl-auto=update` 下**从未被建出来**，导入错误明细写入功能整体不可用。实体与快照统一改名为 `line_number` |

## 7. 建议的目标约束（切换到 Flyway 时一次性落地）

```sql
ALTER TABLE inventory_records ADD UNIQUE KEY uk_inv_sku_wh (sku_code, warehouse_id);
ALTER TABLE locations         ADD UNIQUE KEY uk_loc_wh_code (warehouse_id, location_code);
ALTER TABLE purchase_order_items ADD UNIQUE KEY uk_po_item (purchase_order_id, sku_code);
CREATE INDEX idx_users_tenant     ON users (tenant_id);
CREATE INDEX idx_roles_tenant     ON roles (tenant_id);
CREATE INDEX idx_order_items_ord  ON order_items (order_id);
CREATE INDEX idx_orders_customer  ON orders (customer_id, status);
CREATE INDEX idx_skus_product     ON skus (product_id);
CREATE INDEX idx_inv_records_sku  ON inventory_records (sku_code);
CREATE INDEX idx_lo_order         ON logistics_orders (order_id);
CREATE INDEX idx_ft_ref           ON financial_transactions (reference_id, reference_type);
-- 多租户：业务表补 tenant_id 并把全局唯一改为租户内唯一
ALTER TABLE orders ADD COLUMN tenant_id VARCHAR(255) NULL, ADD INDEX idx_orders_tenant (tenant_id);
ALTER TABLE users  DROP INDEX uk_users_username, ADD UNIQUE KEY uk_users_tenant_username (tenant_id, username);
-- 乐观锁 / 金额 / 时间口径：需与实体代码同步变更，见 ADR-0002
```
