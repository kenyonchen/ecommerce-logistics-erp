# 数据模型演进与迁移方案

> 定位：把 [ADR-0002](../adr/0002-hibernate-ddl-auto.md) 的 7 步迁移路径**展开成可执行的迁移计划**（脚本清单、顺序、数据回填、校验、回滚）。
> 数据语义与风险编号（`R-01~R-12`）以 [data-dictionary.md](data-dictionary.md) 为唯一权威，本文不复述列定义，只讲**怎么从现状演进到目标态**。
> 事实基线：2026-01-25 工作树；`spring.jpa.hibernate.ddl-auto: update`（`application.yml:33`）、`show-sql: true`（`:34`）；**全仓无 Flyway/Liquibase**，`resources/` 只有 `application.yml` 一个文件。
> ⚠️ 2026-01-25 按工作树 + 运行时库（`ecommerce_logistics_erp`，只读查询）复测：**表数从 24 涨到 42**（40 张单列主键 + `user_roles` 复合主键，外加 `role_jpa_entity_permissions` **完全没有主键**）；**23/42 张表已有 `tenant_id`**；**已声明 27 个 `@Index` + 11 个 `@UniqueConstraint`**（分布在 18 / 10 个实体），运行时库有 **66 个非主键索引**，仅 9 张表还是「只有 PRIMARY」。下文原写法（24 张表、其余 21 张无索引）已按实测就地更正。

## 1. 为什么必须迁移（三条硬约束）

| 约束 | 后果 |
|---|---|
| `ddl-auto: update` **只加不改不删** | 实体删字段不会回收列；改类型可能启动失败或静默留旧列。**回滚一次实体变更 ≠ 回滚一次库结构**（[deployment.md §6](../deployment.md)） |
| 无迁移工具 → schema 变更不可评审、不可复现 | 新环境只能靠「跑一遍应用让它自己建表」，且建出的结构与 [schema.sql](schema.sql) 快照不一致（原写法「快照含物理外键，Hibernate 默认不生成同名约束」已被实测推翻：`ddl-auto: update` **会**为 `@ManyToOne` 建出 19 个物理外键，两边的差异是**索引名哈希化 + 原生 ENUM/binary(16) 类型**而不是 FK 有无，见 §5；另有更硬的一条：保留字列名会让建表**静默失败**，见 [ISS-049](../qa/known-issues.md)——6 张 `*_import_job_errors` 曾因 `row_number` 报 1064 而从未建出来，启动只有一个 WARN） |
| 数据风险已固化在库结构里 | `R-05` 无唯一约束（可写重复库存行）、`R-07` 业务表无 `tenant_id`（越权无法用 DB 兜底）、`R-09` 金额用 `DOUBLE`、`R-11` 主键三套策略 |

## 2. 目标态

- Flyway 管结构，Hibernate 只做校验：`ddl-auto: validate`。
- 所有结构变更 = 一份带编号、可评审、可重放的 SQL；`schema.sql` 从「手工同步的快照」降级为**由 `V1__baseline.sql` 生成的产物**。
- 每环境可复现：新库起来 = 跑迁移，不依赖应用启动顺序。

依赖：`flyway-core` + `flyway-mysql`（Spring Boot 3.5 对应 Flyway 10.x；MySQL 8 需要 `flyway-mysql`，缺它启动即报 Unsupported Database）。

## 3. 阶段计划（M0→M4，按依赖顺序，不可乱序）

### M0 基线冻结与对齐（先决条件）

1. 冻结实体改动（与 product 模块未提交的分页改动协调后再开始，见 §7）。
2. 用一个**干净库**跑一次应用，导出 Hibernate 实际生成的 DDL（`show-sql: true` 已开，或 `spring.jpa.properties.javax.persistence.schema.global.create.drop` 到临时库）。
3. 与 `docs/db/schema.sql` 逐行比对，**以生成结果为准**修正快照，使快照成为真值的可读版本（ADR-0002 迁移路径 step 2）。
4. 差异清单里若出现「快照有、生成无」的外键，明确记录为**决策**（是否引入物理外键，见 §5 的取舍）。

产出：`docs/db/schema.sql` 与真实库结构 100% 一致，且这份一致性是可复述的。

### M1 引入 Flyway 与基线脚本

```
db/migration/
├── V1__baseline.sql                 # 42 张表 + 已有索引/唯一键，由 M0 的快照生成
├── V2__add_missing_unique_keys.sql
├── V3__add_hot_path_indexes.sql
├── V4__add_tenant_id_to_business_tables.sql
├── V5__backfill_tenant_id.sql
├── V6__narrow_tenant_unique_keys.sql
├── V7__money_to_decimal.sql
├── V8__timestamp_column_type_unification.sql
├── V9__add_version_column.sql
├── V10__soft_delete_unification.sql
└── U1__*.sql                        # 仅本地/测试用的 undo（Flyway Teams 才有 undo；社区版见 §6）
```

配置：

```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true          # 现有非空库需要它
    baseline-version: 0
    locations: classpath:db/migration
  jpa:
    hibernate.ddl-auto: validate       # 从 update 改过来
```

⚠️ 顺序陷阱：**必须先 `validate` 通过再上线 `V1`**。若实体与基线脚本有任何出入，`validate` 会报 `Schema-validation: missing column`——那是信号不是故障，按 ADR-0002 step 5 处理（修脚本或修实体，不要改回 `update`）。

### M2 约束与索引（低风险，先做）

对应 [data-dictionary.md §7](data-dictionary.md) 已给出的 SQL，逐条拆脚本：

| 脚本 | 内容 | 风险点 |
|---|---|---|
| `V2` | `uk_inv_sku_wh(sku_code,warehouse_id)`、`uk_loc_wh_code(warehouse_id,location_code)`、`uk_po_item(purchase_order_id,sku_code)`、`financial_transactions(reference_id,reference_type)` 唯一键（`R-05`） | **加唯一前必须查重并清洗**：`SELECT sku_code,warehouse_id,COUNT(*) ... GROUP BY ... HAVING COUNT(*)>1`。有重复时脚本会失败在 prod——把清理写成 `V2.1__dedup_*.sql`（保留最小 id 或按业务规则保留）。~~**2026-01-25 复测：`R-05` 原样未修**，这 4 个唯一键在运行时库里都不存在（`inventory_records`/`locations`/`purchase_order_items` 只有 Hibernate 为 `@ManyToOne` 自动生成的 `FK…` 索引，`financial_transactions` 除 PRIMARY 外没有任何索引），重复库存行仍可写入~~ → 🔄 **2026-03-22 复测：4 条里第 3 条已落地**——`purchase_order_items` 实测已有 `uk_po_item(purchase_order_id, sku_code)`（`non_unique=0`，声明在 `PurchaseOrderItemJpaEntity:25-26`，由 `ddl-auto: update` 建出，**不是**版本化迁移脚本建的）；其余 3 条仍未修：`inventory_records`（只有 PK + 2 个 `FK…` 索引）、`locations`（只有 PK + 1 个 `FK…` 索引）、`financial_transactions`（**只有 PRIMARY，连外键索引都没有**）。所以本行的 `V2` 范围应改为「3 个唯一键 ＋ 为 `financial_transactions` 补外键/查询索引」，且上线前要先查重（同一 SKU 因 `/in｜/out` 与 `/move` 两条路径键不同而可能已存在重复行，见 [spec/warehouse.md](../spec/warehouse.md) §4 BR-06 的 2026-03-22 复测注） |
| `V3` | `data-dictionary §7` 的 8 条 `CREATE INDEX`（`R-06`：所有 `*_id` 关联列无索引） | 大表建索引锁/耗时；MySQL 8 在线 DDL（`ALGORITHM=INPLACE, LOCK=NONE`）优先。当前是开发库，先建，无历史包袱 |
| — | product 上下文已声明索引，基线必须包含：`products` 的 `idx_products_tenant`/`idx_products_category`/`idx_products_source` + `uk_products_external_ref`（`ProductJpaEntity:22-30`）、`product_import_jobs` 的 `idx_import_jobs_tenant`（`ProductImportJobJpaEntity:25`）、`product_field_mappings` 的 `idx_field_mappings_lookup` + `uk_field_mappings_ref`（`ProductFieldMappingJpaEntity:24-27`）。 | ~~其余 21 张表目前没有任何索引，全靠 `V3` 补~~ → **2026-01-25 实测**：除 product 上下文外，order / logistics / customer / finance / shop-warehouse 的实体也已声明索引，合计 **27 个 `@Index`（18 个实体）+ 11 个 `@UniqueConstraint`（10 个实体）**，运行时库已有 **66 个非主键索引**；仍只有 PRIMARY 的只剩 **9 张表**（`financial_transactions`、`logistics_fees`、`logistics_labels`、`payment_infos`、`platform_integrations`、`purchase_orders`、`roles`、`shipping_addresses`、`user_roles`）。`V3` 的范围因此从「补 21 张表」收窄为「补这 9 张表 + 校验既有索引命名与列宽」 |

同时补实体侧注解，保持 `validate` 与生成一致（`@Table(indexes=..., uniqueConstraints=...)`）。

### M3 多租户列（最高业务价值，也是最大 blast radius）

现状：只有 3 张表有 `tenant_id`（`ProductJpaEntity:38`、`UserJpaEntity:31`、`RoleJpaEntity:27`），其余 21 个实体没有（`R-07`）；`users.username`/`email` 是**全局唯一**（`UserJpaEntity:34,40`），与租户模型直接冲突。

> **2026-01-25 复测更正**：`tenant_id` 已从 3 张扩到 **23/42 张表**（订单、物流、仓库、店铺仓、客户、平台、财务、采购主单及各自的 import/field-mapping 表都已带列），仍缺 **19 张**——其中 `tenants` 是主体表不需要，真正待补的是 6 张 `*_import_job_errors` 子表、`logistics_channels`，以及 `order_items`/`purchase_order_items`/`shipping_addresses`/`payment_infos`/`logistics_fees`/`logistics_labels`/`inventory_records`/`locations`/`skus`/`user_roles`/`role_jpa_entity_permissions` 这类**靠父表归属的子表与关联表**。
> 另一个落地细节：唯一性是 `@Column(unique = true)` 声明的——运行时库里实际索引名是 Hibernate 生成的哈希名（`UK6dotkott2kjsp8vw4d0m25fb7` on email、`UKr43af9ap4edm43mmtq01oddj6` on username），**不叫 `uk_users_username`/`uk_users_email`**。下文 V6 的 `DROP INDEX uk_users_username` 是按「有命名约束」的理想写法写的，落地时必须先从 `information_schema.statistics` 取真实名，否则脚本直接失败。

```sql
-- V4：加列（先 nullable，避免存量行失败）
ALTER TABLE orders ADD COLUMN tenant_id VARCHAR(64) NULL;
-- …18 张业务表逐表加；统一 VARCHAR(64)（当前是 VARCHAR(255)，与 tenants.id 口径对齐后再收窄）

-- V5：回填（当前单租户现实 → 全部填默认租户，可预测）
UPDATE orders SET tenant_id = 'default-tenant' WHERE tenant_id IS NULL;

-- V6：收窄 + 唯一键改造
ALTER TABLE orders MODIFY COLUMN tenant_id VARCHAR(64) NOT NULL;
CREATE INDEX idx_orders_tenant ON orders (tenant_id, status, created_at);
ALTER TABLE users  DROP INDEX uk_users_username,
                   ADD UNIQUE KEY uk_users_tenant_username (tenant_id, username);
ALTER TABLE users  DROP INDEX uk_users_email,
                   ADD UNIQUE KEY uk_users_tenant_email (tenant_id, email);
```

**依赖关系（务必按此顺序）**：`V4/V5/V6` 完成后，才能启用 `TenantIsolationAspect` 的真实过滤逻辑（现状逻辑整体注释、只剩 `System.out.println`，`TenantIsolationAspect:39-41`）并删除「`tenantId` 由客户端传参」（M-05）。否则隔离逻辑无列可过滤——见 [tech/security-design.md §4](../tech/security-design.md)。

> **2026-01-25 复测：这条依赖的顺序已经反过来了**（就地更正，原句保留以便对照上面的理想路径）。`TenantIsolationAspect` 的注释逻辑**已删除并真正生效**（`:55-86`：非 `ROLE_PLATFORM_ADMIN` 且参数名为 `tenantId` 的实参与 `TenantContext` 不一致 → 抛 `BusinessException("无权访问其他租户的数据","TENANT_ACCESS_DENIED")`；`:91-97` Repository 切面**只做 trace 不过滤**），`TenantContext` 也补了清理。也就是说**写路径的越权拦截不再等 M3**。但它是「按参数名校验」，不是「按列过滤」，所以：
> - 读路径仍然没有隔离——`PurchaseOrderRepositoryImpl.java:58,70,83,97,111` 等查询根本不带 `tenantId` 条件（登记为 ISS-050），这正是本节 M3 的剩余价值：**给缺列的 19 张表补 `tenant_id`，才能让仓储查询带上租户谓词**，而不只是校验入参。
> - 客户端传参~~只剩 1 处：`PurchaseController.java:47` 的 `tenantId` 仍是 `@RequestParam`（ISS-051），其余控制器已改从令牌取~~ → 🔄 **本轮自查订正："`@RequestParam(defaultValue=…)`" 这种写法确实只剩 `PurchaseController:47` 1 处，但"缺省回落 `default-tenant`"的逻辑有 **17 处字面量 / 10 个 `DEFAULT_TENANT_ID` 常量 / 15 处引用**（9 个应用服务 + `FinancialTransactionServiceImpl:26` + `OrderController:170`、`CustomerController:68` 兜底 + 异步/映射 5 处）。详见 [security-findings M-05](../security-findings.md)。**

另需一并清理的硬编码：~~`LogisticsOrderMapper:52-54`、`WarehouseMapper:54-56` 用反射注入 `"default-tenant"`（注释「暂时设置默认租户ID」）~~ → **2026-01-25 复测：`LogisticsOrderMapper` 已完全不处理 `tenantId`（该方法内 0 命中，读出来即为 null，属另一种缺陷形态）；`WarehouseMapper:59-60` 仍是反射注入 `"default-tenant"` 兜底**。此外新增查明的更大面：字面量 `"default-tenant"` 主源码 **17 处**、`DEFAULT_TENANT_ID` 常量 **10 个**（9 个应用服务 + `domain/finance/service/FinancialTransactionServiceImpl:26`）、引用 **15 处**，异步侧另有 `EventHandler:100`、`OrderCreatedEventConsumer:38`、`InventoryShortageEventConsumer:37`、`BusinessProcessOrchestrationService:48,58` → 详见 [security-findings M-05](../security-findings.md)（本表的 V4/V5/V6 之后必须连这批一起清，否则加列只是"能过滤"而非"会过滤"）。

> **2026-01-25 复测更正**：`LogisticsOrderMapper` 已不再注入常量，直接透传 `logisticsOrder.getTenantId()`（`:48`，回读 `:80`）；`RocketMQEventConsumer` 这个类**已不存在**（MQ 监听器拆成 `infrastructure/messaging/consumer/` 下的 static 顶层类）。当前实测的 `"default-tenant"` 硬编码是：`WarehouseMapper.java:58`（实体值缺失时兜底）、`EventHandler.java:100`、`OrderCreatedEventConsumer.java:38`、`InventoryShortageEventConsumer.java:37`、`BusinessProcessOrchestrationService.java:48,58`（编排服务直接写死租户与 `DEFAULT_CHANNEL`/`DEFAULT_SUPPLIER`），另有 **10 处 `public static final String DEFAULT_TENANT_ID = "default-tenant"`** 分散在各 ApplicationService 里作为「参数为空则回落默认租户」的兜底（`OrderApplicationService:31`、`ProductApplicationService:42`、`CustomerApplicationService:23`、`FinancialTransactionServiceImpl:26` 等）。**大小写只有一种**（`"default-tenant"`），原文「两种大小写并存」已不成立。

### M4 类型与策略口径统一（改列类型，需应用同步发布）

| 脚本 | 内容 | 配套代码改动 |
|---|---|---|
| `V7` | `DOUBLE` → `DECIMAL(19,2)`：`orders`、`order_items`、`skus`、`logistics_*` 的金额列（`R-09`；~~purchase/finance 已是 `DECIMAL(19,2)`~~ → 🔄 **2026-03-22 实测：purchase/finance 是 `DECIMAL(38,2)`**——`PurchaseOrderJpaEntity:51` 的 `total_amount`、`PurchaseOrderItemJpaEntity:43`（`unit_price`）/`:46`（`total_price`）、`FinancialTransactionJpaEntity:33` 的 `amount` 都没写 `precision/scale`，Hibernate 默认给 38；全库唯一显式 19,2 的仍是 `payment_infos.amount`。⇒ `V7` 的范围要重新界定：目标若统一成 19,2，这 4 列也得 `MODIFY`，不能把「已经是 DECIMAL」当成「已经达标」）。**2026-01-25 实测范围**：运行时库还有 **12 个 double 列 / 6 张表**（`orders.total_amount`、`order_items.unit_price`+`total_price`、`skus.price`+`cost_price`+`weight`、`logistics_channels.base_price`+`price_per_gram`、`logistics_fees.base_fee`+`weight_fee`+`total_fee`、`logistics_labels.package_weight`）；对应实体侧 **12 个 `private Double`** 字段，代码里也没有集中精度口径 | 实体字段 `Double` → `BigDecimal`；`ProductPrice`/`LogisticsFee` 等值对象、`calculateTotal()` 精度与舍入口径需在 `spec/order.md` 的 `BR-xx` 明确 |
| `V8` | `BIGINT` epoch → `DATETIME(6)`：`logistics_orders`（`LogisticsOrderJpaEntity:57-60` 用 `Long`）（`R-10`）。**行号更正**：实际是 `:94`（`createdAt`）与 `:97`（`updatedAt`）两个 `Long`；同表的 `shippedAt`/`estimatedArrivalAt`/`deliveredAt`（`:79,82,85`）已经是 `LocalDateTime`，所以 V8 只需处理这两个审计列 | Mapper 转换与前端时间格式约定 |
| `V9` | 状态迁移类聚合加 `version INT NOT NULL DEFAULT 0`（`R-03`：全仓 `@Version` 0 处，并发更新丢写） | 实体加 `@Version`；冲突走 `B00004`（[error-codes.md §3](../api/error-codes.md)） |
| `V10` | 软删除统一：`active` 布尔（products/skus/warehouses/locations/channels）+ `DELETED` 状态（`CustomerStatusJpaEntity:12`）+ `orphanRemoval` 硬删（其余）三套并存（`R-08`）→ 统一 `deleted TINYINT(1) NOT NULL DEFAULT 0` + 查询过滤 | 需要 Hibernate `@Where(clause="deleted=0")` 或 `@SQLRestriction`，并逐个改仓储查询 |
<!-- 2026-01-25 复测：V10 的两处引用按实测更正——① `active` 列实际是 **6 张表**的 `bit(1)`：`products`、`skus`、`warehouses`、`shop_warehouses`、`locations`、`logistics_channels`（原文漏了 `shop_warehouses`）；② **不存在 `CustomerStatusJpaEntity`**，`DELETED` 是领域枚举值 `domain/customer/valueobject/CustomerStatus.java:15`（`DELETED("deleted","已删除")`，`isDeleted()` 在 `:58`，聚合侧软删入口 `Customer.java:149-152`），持久化为 `customers.status` 列。→ V10 要把「布尔 active」「枚举含 DELETED 状态」「orphanRemoval 物理删」三种语义压到一列，冲突点在于 `customers` 已有 5 个状态值，不能简单加 `deleted` 列后忽略状态机。 -->
| （可选）`V11` | 主键策略统一（`R-11`：String 领域赋值 15 表 / UUID `@GeneratedValue` 2 表 / IDENTITY 自增 1 表；PK 列名 `id` vs `purchase_order_id` vs `transaction_id`） | **改动面极大**，建议不做统一，只在文档固化「新表一律 `VARCHAR(64) id` + 应用层赋值」的约定 |
<!-- 2026-01-25 复测（42 张表实测口径）：R-11 的比例已随表数变化——**40 张 String 赋值主键**（39 张 `VARCHAR(255)` + `customers.id VARCHAR(64)`，`CustomerJpaEntity:41` 是应用层赋值而非 `@GeneratedValue`）、**1 张 UUID `@GeneratedValue`**（`platform_integrations.id`，实体 `:33-35`，建出来是 `binary(16)`）、**1 张 IDENTITY 自增**（`purchase_order_items.id bigint`）。PK 列名不统一的是 3 处：`financial_transactions.transaction_id`、`purchase_orders.purchase_order_id`，以及 `user_roles(user_id, role_id)` **复合主键**；另有 `role_jpa_entity_permissions`（`@ElementCollection` 生成的权限表，列为 `role_jpa_entity_id` + `permission`）**完全没有主键**——这是 ISS-041 权限点模型的落地形态，V1 基线必须显式决定是给它加 PK 还是改成独立权限表。原文「String 15 / UUID 2 / IDENTITY 1」是 24 表时代的数字。 -->

`R-04`（时间戳填充三套并存，全仓无 `@EnableJpaAuditing`/`@PrePersist`/`@CreationTimestamp`）属于**代码侧修复**：开 JPA Auditing + `@CreatedDate/@LastModifiedDate + @EntityListeners(AuditingEntityListener.class)`，DB 侧配 `DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)` 兜底。

## 4. 字符集、排序与列宽约定（新表强制）

| 项 | 约定 | 理由 |
|---|---|---|
| 字符集/排序 | `utf8mb4` / `utf8mb4_general_ci`（与建库语句一致，[dev-guide §2](../tech/dev-guide.md)） | emoji 与多语言 SKU 名 |
| 主键 | `VARCHAR(64)`（业务编码型）或 `BINARY(16)`（UUID 型），**一表一策，不混用** | 现状 `customers`/`platform_integrations` 用 UUID `@GeneratedValue` |
| 金额 | `DECIMAL(19,2)`，禁用 `FLOAT/DOUBLE` | `R-09` |
| 时间 | `DATETIME(6)`，禁用 epoch `BIGINT` | `R-10`；注意 JDBC URL 已有 `serverTimezone=UTC`（`application.yml:17`），改列类型前要确认历史数据语义 |
| 状态枚举 | `VARCHAR(32)` 存**枚举 name**，禁用 `ORDINAL` | 枚举三套口径（`R-02`，见 [data-dictionary §4](data-dictionary.md)）；`CON_FIRMED`↔`CONFIRMED` 类名称漂移必须由 Mapper 显式桥接，不能靠顺序 |
| 租户列 | `tenant_id VARCHAR(64) NOT NULL` + 复合索引首列 | M3 |

## 5. 物理外键：明确不做

- Hibernate 侧 grep `foreignKey` = 0（无显式 FK 配置），关系靠 `@ManyToOne/@OneToMany`；`docs/db/schema.sql` 作为快照却**包含物理外键**，两者不完全相同（[deployment.md §2.1](../deployment.md) 已警告不要混用于同一库）。
  - ⚠️ **2026-01-25 复测：这条「两者不完全相同」已经不成立**。实体里确实没有显式 `foreignKey` 配置（grep 仍为 0），但 13 个 `@ManyToOne` 足以让 Hibernate 在 `ddl-auto: update` 下**自己建出物理外键**——运行时库实测已有 **19 个 FOREIGN KEY 约束**（27 张表没有任何 FK）。所以「快照有、生成无」的假设是反的：现在两边**都有** FK，`V1__baseline.sql` 若不写 FK 就和真实库不一致，写了就和「不引入物理外键」的决策冲突。这一条必须在 M0 里当作**已存在的既成事实**处理（要么基线保留这 19 个 FK，要么专门写一支 `DROP FOREIGN KEY` 迁移并同步去掉 `@ManyToOne` 的级联），不能继续按「Hibernate 默认不生成」下结论。
  - 顺带：`R-01`（`supplier_id`/`category_id` 裸引用）在新增表上仍是风险，但**存量** FK 已经锁住了 `order_items→orders`、`inventory_records→locations/warehouses` 这类子表关系，删主表数据的操作现在会被库直接拒绝。
- 决策建议：**V1 基线不引入物理外键**，一致性由应用层 + 唯一键 + 迁移脚本校验承担。理由：`R-01`（`supplier_id`/`category_id` 引用缺失主表、多处裸引用）在引入 FK 时会直接迁移失败；且分库/批量导入/软删除场景 FK 会成为阻碍。
- 若未来要引入，作为独立 ADR，并先补齐 `suppliers`、`categories` 两张缺失主表。

## 6. 校验、回滚与演练

**每次迁移前**：
1. `mvn -f backend/pom.xml flyway:migrate` 对临时空库执行全量脚本 → 必须一次通过（等价于「新环境可复现」的证明）。
2. 备份：`mysqldump --single-transaction` 到带时间戳文件；MySQL 8 在线 DDL 失败也需可用备份兜底。
3. `ddl-auto: validate` 启动一次，确认实体与脚本无出入。

**回滚**：Flyway 社区版**无 undo**。因此：
- 所有脚本按「向前修复」设计：加列/加索引类可直接再执行反向 DDL（`DROP INDEX`/`DROP COLUMN`），单独写成 `rollback/` 目录下的手工脚本并**与主脚本同 PR 评审**；
- 改列类型类（`V7/V8/V10`）**不可逆**——迁移前必须做列影子方案：先 `ADD COLUMN total_dec DECIMAL(19,2)` → 双写 → 回填校验 → `RENAME`，禁止 `ALTER TABLE ... MODIFY` 一步到位。
- 一旦切到 Flyway，「回滚一次实体变更」变成「执行反向迁移」，可评审可复现（对齐 [deployment.md §6](../deployment.md)）。

## 7. 已落地的 product 改动与快照漂移（2026-01-25 更新）

> 🔺 **2026-03-08 追加（平台集成域）**：`platform_integrations` 新增 5 列 `auth_type(32)` / `access_token(1024)` / `refresh_token(1024)` / `token_expires_at` / `api_version(32)`，并把 `api_key`/`api_secret` 的**内容**从明文换成 AES-256-GCM 密文（`enc:v1:` 前缀）。
> - **表数量不变**（仍 42 张 `CREATE TABLE`），所以快照的逐表 diff 结论仍成立；但**这 5 列必须进 `V1__baseline.sql`**，且它们是「**只加不减**」的 `ddl-auto=update` 产物——`api_key` 的语义已从"明文"变成"密文"，**列名没变、内容口径变了**，这类变更在快照里看不出来，基线化时要以实体 + `CredentialCipher` 为准（详见 [schema.sql](schema.sql) 该表下方的 ⚠️ 注）。
> - ⚠️ **密文比原文长**：`api_key`/`api_secret` 仍是默认 `VARCHAR(255)`，Base64 后大约只容纳 **180 字符左右**的原文。若某平台凭据更长（eBay 的 user access token 可达 1000+），需要显式 `@Column(length=…)` 扩列，否则 `ddl-auto=update` **不会**自动放宽已存在列的宽度（它只加列/加表，不 MODIFY）。这条属于"必须靠迁移脚本才能修"的形态，正是 M0 立 Flyway 基线的理由之一。
> - ⚠️ **无唯一约束**：`platform_integrations` 至今没有 `(tenant_id, platform_name, store_url)` 唯一键（BR-03），而它的**存在前提是历史行没有重复**——所以补约束前必须先跑一次重复度预检（同 §6 的 `V2` 预检 SELECT 做法）。
> - ✅ `product_import_job_errors` 那条"子表无 `tenant_id`"的例外**没有扩大**：本轮未新增任何表。


早期版本把这一节写成「与在途未提交改动协调」；这批改动**已经合入工作树**，所以本节改为记录它给基线带来的具体差异：

- 新增 3 张表 + 3 套仓储：`product_import_jobs`（`ProductImportJobJpaEntity`，有 `tenant_id`、`idx_import_jobs_tenant`）、`product_field_mappings`（有 `tenant_id`、`idx_field_mappings_lookup`、`uk_field_mappings_ref`）、`product_import_job_errors`（**无 `tenant_id`**，靠 `job_id` 间接归属）。这三张必须进入 `V1__baseline.sql`。
- 修改实体：`ProductJpaEntity`（新增 `idx_products_category`/`idx_products_source` 与 `uk_products_external_ref`、来源平台列）、`SkuJpaEntity`、`Product`/`SKU` 聚合。
- 新增跨层分页容器 `domain/common/PageResult` + `domain/product/repository/ProductQuery` —— 这是分页规范的目标形态（[tech/performance-capacity.md §4](../tech/performance-capacity.md)），后续 11 个模块应复用它而不是各自造响应结构。
- ⚠️ ~~**`docs/db/schema.sql` 已漂移**：只有 22 条 `CREATE TABLE`，缺上述 3 张表（登记为 ISS-038）~~ → **ISS-038 已修（2026-01-25）**：快照现为 **42 条 `CREATE TABLE`**，与运行时库逐表做过列名/可空性 diff，**42/42 一致**（仅 `financial_transactions.tenant_id`、`purchase_orders.tenant_id` 为后加列，历史行是 NULL）。但 **`V1__baseline.sql` 仍不要直接从快照生成**：Hibernate 6 + `MySQL8Dialect` 对部分 `@Enumerated(STRING)` 列建出的是**原生 `ENUM(...)`**（`customers.status`、`*_import_jobs.status`、`*_field_mappings.target_field/transform`、`logistics_orders.status`），`platform_integrations.id` 建出的是 **`binary(16)`**，而快照里写的是 `VARCHAR`/`CHAR(36)` 声明形态——快照是「可读真值」不是「字节级镜像」，基线仍应以 §3 M0 从实际库 `mysqldump --no-data` 重导为准（差异口径已在 [schema.sql](schema.sql) 头部 ⚠️ 注记录）。
- ⚠️ `product_import_job_errors` 是唯一一张「子表无 `tenant_id` 但父表有」的表。M3 租户列迁移时要么给它补列，要么在文档里明确「按父任务归属」的例外，否则会变成一个难以解释的不一致点。
- 索引与分页是配对收益：`ProductRepositoryImpl`/`ProductImportJobRepositoryImpl` 已用 `PageRequest` + `PageResult`（全仓唯一），其余 11 个模块的分页与索引选择见 [tech/performance-capacity.md](../tech/performance-capacity.md)。
  <!-- 2026-01-25 复测：「全仓唯一」已过期。现在使用 PageRequest 的 RepositoryImpl 有 **11 个**：ProductRepositoryImpl、ProductImportJobRepositoryImpl、OrderRepositoryImpl、OrderImportJobRepositoryImpl、CustomerRepositoryImpl、CustomerImportJobRepositoryImpl、LogisticsOrderRepositoryImpl、LogisticsImportJobRepositoryImpl、FinancialTransactionRepositoryImpl、FinanceImportJobRepositoryImpl、ShopWarehouseImportJobRepositoryImpl；`PageResult` 被 48 个文件引用（domain/common 定义 → 仓储 → 应用服务 → 控制器 → 前端）。仍未分页的是 warehouse / purchase / platform / tenant / user / role / auth 这一类仓储，M2 的索引选择应以「已分页的热路径」优先。 -->


## 8. 门禁接入

| 检查 | 位置 | 作用 |
|---|---|---|
| `flyway:migrate` on 空库 | CI（[tech/ci-cd.md §4](../tech/ci-cd.md)） | 结构变更不可复现 → 红灯 |
| `ddl-auto: validate` 冒跑 | CI | 实体与脚本漂移 → 红灯 |
| PR 含 JPA 实体 diff 但无 `db/migration/*.sql` | CI 规则（`danger`/自研脚本） | 强制「改实体必带迁移」；同时要求更新 `schema.sql` |
| 重复数据预检 SELECT | 迁移脚本自带（`V2` 前） | 避免唯一键在生产失败 |

在 M0 完成前，CI 无法引入这些检查——这也是当前 [testing.md §1](../testing.md) 记录「无 CI」的直接后果之一。


## 9. M5 店铺分表：一刀切重建（2026-10-06，见 [ADR-0013](../adr/0013-shop-entity-and-platform-connection-split.md)）

**本节不适用「只加不改不删」的旧约束**——系统未上线、现库零存量，用户 2026-10-06 明确要求不做迁移、直接重建。

### 9.1 存量底账（2026-10-06 直读 `ecommerce_logistics_erp`）

| 对象 | 行数 | 结论 |
|---|---|---|
| `platform_integrations` | 0 | 无需搬迁凭据与站点串 |
| `shop_warehouses` | 0 | `shop_code` 裸引用可直接换成 `shop_id` 外键 |
| `orders` | 63（`FILE` 57 + `MANUAL` 6） | **没有一条来自平台对接**，`shop_id` 无需回填 |
| `order_items` | 63 | 同上 |

⇒ 历史数据风险为零，本节的成本全部在 DDL 与代码切流，不在数据。

### 9.2 为什么仍然要写显式 DDL（不能图省事让 Hibernate 建）

`platform_integrations.platform_name` 在运行库是 MySQL **ENUM，只有 7 个值**，而 `PlatformName` 枚举有 12 个。回滚式插探针实测：

```
INSERT ... platform_name='OZON'  →  ERROR 1265 (01000): Data truncated for column 'platform_name'
（sql_mode 含 STRICT_TRANS_TABLES；auth_type 的 ENUM 则是完整的 11 值，两者不对称）
```

`ddl-auto=update` 只加不减**也不改类型**，所以这个窄化不会因为改实体而消失。它的来源是历史手工 DDL，不是 Hibernate 生成的形状（Hibernate 对 `@Enumerated(STRING)` 生成 varchar）。因此：

1. 重建时必须用**显式 DDL** 建 `platform_integrations` 与 `shops`，`platform_name`/`status`/`auth_type` 一律 `varchar(32)`，主键一律 `varchar(36)`（现状 `id` 是 **`binary(16)`**——Hibernate 6 把 UUID 存成 16 字节原生二进制，不是字符列，两者形状与比较语义都不同，不能混）。
2. 否则新建库会重演同类窄化风险，且 `docs/db/schema.sql` 与真实库再度失真。
3. 这条是 M1 引入 Flyway 的第一个真实用例，建议 `V1__baseline.sql` 直接以本节脚本为种子。

### 9.3 重建顺序（每步可独立验证）

1. 停应用 → `DROP DATABASE ecommerce_logistics_erp; CREATE DATABASE ... CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;`（列宽与字符集遵循 §4 的新表强制约定）
2. 跑显式 DDL：§9.2 的 `platform_integrations`（对接定义形状，已去掉卖家凭据列与 `tenant_id`）+ ADR-0013 §2.2 的 `shops` + 其余业务表
3. 起应用（`ddl-auto: update`）补齐实体声明的新列；`DataInitializationConfig` 自动灌基础数据（§9.4）
4. 代码切流（ADR-0013 §6 步骤 5）：`PlatformCredential.from(shop)`、`AmazonOrderFetcher` 按站点逐次调用并回读 `MarketplaceId`、导入落库写 `shop_id`
5. 上守卫测试（ADR-0013 §4.3 锁 1）与权限 catalog（`TENANT_SCOPED` 19→23、`ALL` 22→26）+ 前端 `PERMISSION_PRESETS` 副本

### 9.4 重建后必须齐备的基础数据

现有种子已覆盖，重建即自动恢复：默认租户 + `admin/123456`（TENANT_ADMIN，并在 `ensureAdminHasPlatformAdminRole()` 里幂等补挂 PLATFORM_ADMIN）、`founder@local.com`、`PLATFORM_ADMIN` 系统角色全量权限点、`ensureTenantAdminPermissions()` 对每个租户的 TENANT_ADMIN 幂等对齐、8 个内置物流渠道（`DEFAULT_CHANNEL`/SF/ZTO/YTO/JD/EMS/USPS 等，`:46-63` 与 `:299-317`）。

**需新增的种子**：`shop:read`/`shop:write`/`shop:credential_write`/`shop:sync` 四个权限点进 catalog（`ensureTenantAdminPermissions()` 的幂等补齐会让存量与新建租户的 TENANT_ADMIN 自动获得，无需人工授权）。

**不重建的部分（需确认）**：现库 63 条订单、2 商品、2 仓库、5 财务流水都是历轮验证的手工/导入残留。若需要可复现的演示数据集，应显式写成脚本（当前 `scripts/` 下只有 `docker/`，无灌数脚本），不要依赖旧库数据。

### 9.5 校验点（重建后逐条跑，全绿才算完成）

```sql
-- 1. 店铺表存在且唯一键形状正确
SHOW COLUMNS FROM shops;  -- 期望 24 列
SHOW INDEX FROM shops;    -- 期望 uk_shops_tenant_code(unique)、uk_shops_marketplace(unique)、idx_shops_tenant、idx_shops_integration

-- 2. 列型窄化已消除：这四列必须是 varchar，不得再是 ENUM 或 binary(16)
SELECT COLUMN_NAME, DATA_TYPE FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA='ecommerce_logistics_erp'
   AND TABLE_NAME IN ('platform_integrations','shops')
   AND COLUMN_NAME IN ('platform_name','platform','status','auth_status','id');

-- 3. 订单已具备店铺归属列与配套索引
SHOW COLUMNS FROM orders LIKE 'shop_id';
SHOW INDEX FROM orders WHERE Key_name='idx_orders_shop';

-- 4. 基础数据齐备
SELECT (SELECT COUNT(*) FROM tenants)            AS tenants_ge_1,
       (SELECT COUNT(*) FROM users)              AS users_ge_2,
       (SELECT COUNT(*) FROM logistics_channels) AS channels_eq_8;
```

权限侧的校验不在 SQL 里，而是 ADR-0013 §4.3 的 `TenantParameterGuardTest`：它同时断言「服务方法都有 `tenantId` 形参」与「参数名可反射取到（即 `-parameters` 未被关掉）」——后者是隔离切面的单点故障，2026-10-06 实测编译产物含 `MethodParameters` 属性，当前生效，但无守卫。

### 9.6 快照连带

实现落地后 `docs/db/schema.sql` 需同步：新增 `shops` 块（表数 42→43）、`orders` 加 `shop_id` 与索引、`platform_integrations` 改写为对接定义形状、`shop_warehouses` 的 `shop_code`→`shop_id`。**在代码落地前不要改快照**——本文件是 As-Is 记录，To-Be 在此节与 ADR-0013。
