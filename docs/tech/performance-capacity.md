# 性能与容量设计

> 定位：把 [architecture.md §10](../architecture.md) 的「一行现状」展开成**容量模型 + 瓶颈清单 + 优化方案 + 验收标准**，并对接 [product/07-roadmap.md §6](../product/07-roadmap.md) 的 v1.0 目标（1 万单量级列表响应 < 1s）。
> 索引/列的 DDL 落地步骤在 [db/migration-plan.md M2](../db/migration-plan.md)，本文只定**为什么是这些索引、分页怎么设计、缓存放不放**。
> 事实基线：2026-01-25 工作树；单实例应用 + 单实例 MySQL 8 + 无 Redis + 无连接调优 + 无缓存。

## 1. 负载模型（假设需产品确认后固化）

| 维度 | 保守假设 | 说明 |
|---|---|---|
| 租户数 | 10 → 100 | 多租户共享库（[ADR-0003](../adr/0003-multi-tenancy-isolation.md)） |
| 订单量 | 1 万/日（v1.0 验收口径）→ 峰值 5 TPS 下单 | roadmap §6 |
| 单据膨胀比 | 1 订单 ≈ 3 订单项 + 1 物流单 + 1~2 库存流水 + 1 财务流水 | 由 `OrderItem`/`LogisticsOrder`/`InventoryRecord`/`FinancialTransaction` 关系推得 |
| 数据规模（1 年） | orders 365 万、order_items 1100 万、inventory_records 数百万 | **单表千万级**是索引与分页必须现在做的根本原因 |
| 读放大 | 列表/搜索远多于写；ERP 后台典型 10:1 | 决定「分页 + 索引」优先于「缓存」 |
| 平台同步 | 每租户每平台 15~60min 一次批量拉单 | 外部限流主导，见 [integration-design.md §6](integration-design.md) |

任何一项与实际不符都应回到本文更新——容量结论依赖假设，不写假设的压测没有意义。

## 2. 瓶颈清单（按影响排序，全部实测）

| # | 瓶颈 | 证据 | 影响量级 |
|---|---|---|---|
| P-1 | **索引覆盖：从「极低」升到「局部可用」**（2026-01-25 重测；旧描述「24 张表里只有 product 上下文的 3 张有索引声明」已过期） | 实测实体侧 **27 个 `@Index` + 11 个 `@UniqueConstraint`，分布在 18 个实体**；`ddl-auto` 建出的运行时库有 **66 个非主键索引**。旧口径「5 个 `@Index` 全在 product 上下文」已不成立。⚠️ 两处口径修正：① MySQL 会为 FK 列自动建索引，所以「外键列没有索引」本来就不准确；② 真实性能缺口是 **6 张表的 `tenant_id` 无索引**（租户过滤走全表扫描）+ **业务关联列无 DB 级外键约束**（[data-dictionary R-06](../db/data-dictionary.md)） | 列表/关联查询全表扫描，随数据线性恶化 |
| P-2 | **11/13 控制器无分页，无界返回** | 分页仅落到 product 上下文：`domain/common/PageResult` + `domain/product/repository/ProductQuery` + `ProductRepositoryImpl`/`ProductImportJobRepositoryImpl` 用 `PageRequest`，对应 `ProductController`/`ProductImportController`；`Order/Logistics/Warehouse/Purchase/Finance/Customer/Tenant/PlatformIntegration` 共 11 个控制器的 list 端点仍全量返回（M-07） | 一次查询拖垮 JVM 堆 + MySQL；同时也是数据泄漏面 |
| P-3 | **N+1 加载**：4 个 `@OneToMany` + 逐条转聚合 | `ProductJpaEntity:65`、`OrderJpaEntity:50`、`WarehouseJpaEntity:50,53`、`PurchaseOrderJpaEntity:51`；`ProductRepositoryImpl:74-77` 对每条记录 `toDomainAggregate` | 1 次列表 = 1 + N 次 SQL；实测规模取决于行数 |
| P-4 | **双仓储体系的额外转换开销** | 13 领域仓储 Impl + 12 MapStruct Mapper + 21 `@Entity`，一次读要走 `JpaEntity → domain → (Mapper/手工转换) → DTO`，且 6 个控制器直接序列化领域聚合 | CPU/GC；集合大时对象图翻倍驻留 |
| P-5 | **无事务 → 无批处理**：全仓 `@Transactional` 0 处 | [security-findings S-06](../security-findings.md) | 无法享受 JPA batch（`jdbc.batch_size` 未配）、每条 insert/delete 各自 autocommit，写放大 |
| P-6 | **反射发布事件** | `DomainEventPublisher:37-39` 每聚合一次 `getDeclaredField` + `setAccessible`，且对仅继承基类的聚合直接失效 | 热路径反射 + 静默丢事件 |
| P-7 | **无缓存、无连接池调优、无 SQL 日志分级** | pom 无 cache starter；`application.yml` 无 Hikari 参数；`show-sql: true`（`:27`） | 重复查询全打到 DB；stdout I/O 拖慢请求 |
| P-8 | **无并发控制 → 冲突重试成本** | `@Version` 0 处（[data-dictionary R-03](../db/data-dictionary.md)） | 丢写带来业务返工成本，而非 CPU 成本 |

## 3. 优化路线（按 ROI 排序）

| 阶段 | 动作 | 收益 | 风险 |
|---|---|---|---|
| **第 1 步** | 补索引（`V2/V3` 迁移脚本）+ 唯一键去重 | 列表/关联查询从全表扫描降到索引范围扫，**收益最大且改动最小** | 加唯一前需清重复数据 |
| **第 2 步** | 所有列表端点强制分页（`page/size`，`size ≤ 200`） | 消除 P-2 的堆与带宽风险 | 前端 25 个 mock 页面接真实接口时同步改造 |
| **第 3 步** | 消除 N+1：`@EntityGraph`/`join fetch` + `hibernate.batch_fetch_size=50` + 列表改「读投影不读聚合」 | P-3、P-4 | 需逐个仓储核对，避免 fetch 引发笛卡尔积 |
| **第 4 步** | 应用服务加 `@Transactional`，写路径批量化（`jdbc.batch_size`） | P-5，且是正确性修复 | 事务变长需评估锁等待 |
| **第 5 步** | 缓存与热点读（§7）——**仅在分页与索引落地后** | 重复报表/字典读 | 缓存一致性；多租户 key 基数 |
| **第 6 步** | 事件发布去反射（聚合暴露 `getDomainEvents()`）+ `AFTER_COMMIT` 投递 | P-6 | 改 `DomainEventPublisher`，需回归 |

## 4. 分页方案

统一走**已存在但未接线**的 `domain/common/PageResult`（工作树新增，当前仅 `ProductRepositoryImpl:78` 使用）：

```
GET /api/orders?page=0&size=20&status=PENDING&sort=createdAt,desc
→ Result<PageResult<OrderListItemDto>>   // { items, page, size, total, totalPages }
```

设计约定：

1. `page` 从 **0** 起（Spring Data 口径），`size` 默认 20、**上限 200**（超出直接 `C00004`）。
2. **排序必须白名单化**：`sort` 只接受 `(createdAt|status|orderId)` 等已建索引列，禁止透传实体字段（既防全表排序也防注入）。
3. **列表不返回聚合，返回行投影**（`OrderListItemDto`），详情才走聚合。这是消除 P-4 的关键——列表页不需要 `orderItems`/`shippingAddress` 整棵对象树。
4. 深翻页：ERP 场景建议**游标（keyset）分页**用于「按时间倒序拉历史」（`WHERE (created_at, id) < (:cursor)`），避免 `OFFSET 100000` 的扫描代价；对 UI 页码需求保留 `LIMIT/OFFSET`。
5. `total` 计数昂贵时，用「是否有下一页」替代总数（游标模式），或走计数缓存（§7）。

⚠️ 迁移期兼容：现有前端 `pagination.total` 取的是 mock 数组长度（`OrderList.jsx:55-57`），后端加分页后前端必须同步改，否则页面显示与真实总数不一致。

## 5. 索引方案（与高频查询配对）

| 表 | 索引 | 支撑查询 |
|---|---|---|
| `orders` | `(tenant_id, status, created_at)`、`(customer_id)` | 工作台首页列表、按客户查单 |
| `order_items` | `(order_id)` | 聚合加载（N+1 缓解） |
| `logistics_orders` | `(order_id)`、`(tenant_id, status)`、`(tracking_no)` | 轨迹回查、待处理队列 |
| `inventory_records` | `uk(sku_code, warehouse_id)`、`(warehouse_id, quantity)` | 补货筛选（`R-05` 缺唯一键） |
| `locations` | `uk(warehouse_id, location_code)` | 库位唯一性 |
| `products` / `skus` | `idx_products_tenant` ✅ 已有、`uk(tenant_id, source_platform, source_product_id)` ✅ 已有、`skus(product_id)` | 平台商品去重（`ProductJpaEntity:24-27`） |
| `purchase_orders` | `(status, expected_arrival)`、`(supplier_id)` | 待审采购列表 |
| `financial_transactions` | `(reference_id, reference_type)`、`(tenant_id, occurred_at)` | 对账（`R-05`） |
| `customers` | `(tenant_id, email)` | 客户查找（现状 email 全局唯一，需随 M3 改租户内唯一） |
| `platform_integrations` | `(tenant_id, platform_name)` | 集成状态查询 |

原则：**索引由查询驱动**，每条索引必须能指回一个具体端点或一条 `BR-xx` 规则；不加「以后可能用」的索引（写放大 + 迁移成本）。所有业务表加 `tenant_id` 后（[migration-plan M3](../db/migration-plan.md)），复合索引一律以 `tenant_id` 开头。

## 6. ORM 层优化

```yaml
spring:
  jpa:
    open-in-view: false                       # 必须显式关闭（默认 true 会掩盖懒加载越界）
    properties:
      hibernate.jdbc.batch_size: 50
      hibernate.order_inserts: true
      hibernate.order_updates: true
      hibernate.batch_fetch_size: 50
      hibernate.query.in_clause_parameter_padding: true
```

- **`open-in-view: false`**：现状未配置（默认 true）。打开时，控制器序列化领域聚合（6 个控制器如此）会在视图渲染期触发懒加载 SQL——请求耗时不可控且事务已出边界。关闭后 N+1 会立刻以 `LazyInitializationException` 暴露，**先做 §3 第 3 步再关**。
- `@EntityGraph` / `join fetch` 用于「详情必须带子集合」的场景；对 `@OneToMany` 大集合优先「分页子查询」而非 fetch（防笛卡尔膨胀）。
- MapStruct 转换（12 个 `*Mapper`）是编译期生成，本身不慢；慢的是转换的**对象数量**，所以列表走投影。
- ⚠️ `LogisticsOrderMapper:52-54`、`WarehouseMapper:54-56` 用反射注入默认租户——反射 + 每行一次，属热路径上的额外开销，也应随 M3 移除。

## 7. 缓存策略（明确「现在不做」与「做什么」）

| 层 | 结论 | 理由 |
|---|---|---|
| Hibernate 二级缓存 | **不做** | 多租户 + 状态频繁变更 + 双仓储体系（领域侧手工转换）会让缓存命中与一致性都难验证；收益 < 运维成本 |
| Redis 读缓存 | **暂缓**（§3 第 5 步） | 无 Redis 依赖；先靠索引+分页解决 80% 问题 |
| 进程内 Caffeine | **可做**，仅限低基数、变更稀少数据 | 枚举字典、`logistics_channels` 报价规则、租户配置；`@Cacheable` + `tenantId` key，TTL 5min，**必须带失效事件**（渠道价卡变更 → `evict`） |
| 计数缓存 | 可做 | 列表 `total`、首页统计卡（现状 `Home.jsx:37,49` 是随机数，接真实数据后即为聚合查询，代价高） |

**禁止缓存**：库存可用量、订单状态、支付信息（一致性敏感）。

引入方式：`spring-boot-starter-cache` + Caffeine，`@EnableCaching`（现状全仓 `@Enable` 只有 `@EnableWebSecurity`，缓存注解 0 处）。缓存 key 规范 `erp:{tenant}:{entity}:{id}`，并保证 P-2 修复前不要缓存整个列表（会把无界返回变成无界驻留）。

## 8. 连接池与容器

| 项 | 现状 | 建议起步 |
|---|---|---|
| HikariCP | `application.yml` 无任何参数（全默认，`maximum-pool-size=10`） | `maximum-pool-size: 20`、`connection-timeout: 3000`、`leak-detection-threshold: 5000`（配合 [observability.md §5](observability.md) 的 `hikaricp_connections_pending`） |
| Tomcat 线程 | 默认 200 | 保持；单实例下瓶颈在 DB 池，不在线程 |
| 实例数 | 单实例，无编排 | 目标 ≥2 副本，见 [deployment-architecture.md §4](deployment-architecture.md)；注意 `TenantContext`/MDC 的 ThreadLocal 必须 `finally` 清理才能安全跨副本 |
| JVM | 未定义（`spring-boot-devtools` 仍启用，属 dev-only） | 容器化后显式 `-XX:MaxRAMPercentage=75`，JDK 17 G1 默认即可 |

## 9. 接口级性能预算

| 端点类型 | P95 预算 | 手段 |
|---|---|---|
| 列表（分页） | < 500ms | 索引 + 投影 + `size ≤ 200` |
| 详情（含子集合） | < 300ms | `@EntityGraph` 一次取全 |
| 创建订单（多表写 + 事件） | < 800ms | 单事务批量 insert + `AFTER_COMMIT` 异步事件 |
| 库存移动 | < 500ms | 行级锁 + `@Version` 冲突重试（≤3 次） |
| 报表/统计 | < 2s（可异步） | 计数缓存或预聚合表 |
| 平台同步（外部调用） | 不计入在线 SLO | 异步任务 + 队列，见 [integration-design.md §6](integration-design.md) |

## 10. 压测与验收

**基线优先**：任何优化前先拿到一条基线曲线，否则「变快了」不可证。

| 阶段 | 工具 | 场景 |
|---|---|---|
| 契约冒烟 | `curl`（[dev-guide.md §3](dev-guide.md)） | 单接口可用性 |
| 基准 | JMeter / k6 | ① `GET /api/orders` **不带分页**（复现 P-2，观察堆与耗时随行数增长）；② 加索引前后同条件对比；③ 5 TPS 下单持续 10min（验证事务 + 事件链路） |
| 容量 | k6 阶梯 | 找到 QPS 拐点与首个瓶颈（预计是 MySQL 全表扫描或 Hikari 池） |
| 数据准备 | 造数脚本 | 按 §1 假设灌 100 万 orders / 300 万 order_items；`ddl-auto: update` 环境可直接灌 |
| 回归门禁 | CI（[ci-cd.md §5](ci-cd.md)） | 列表 P95 超预算 → 阻断；避免「加了功能慢三倍」 |

验收对齐 roadmap v1.0：**1 万单量级列表响应 < 1s**；在 §5 索引与 §4 分页落地前，该目标无法达成（当前是无界全表扫描）。
