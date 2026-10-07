# 06 · 术语表

> 统一团队口径，避免"他说的是 SKU，我以为是 Listing"这类问题。
> 分三类：**A 跨境业务术语**、**B 系统/领域术语**、**C 状态与枚举取值**。

---

## A · 跨境业务术语

| 术语 | 英文 / 缩写 | 定义 | 在本系统的体现 |
|---|---|---|---|
| SKU | Stock Keeping Unit | 最小库存单位，即"一个可售的最小规格组合"（如 T恤-红-M） | `skus.sku_code`，产品的级联子实体 |
| Listing | — | 商品在第三方平台上的售卖页面（同一 SKU 可在多个平台有多个 Listing） | ⚪ 未建模；只有产品/SKU 主数据，无 Listing 概念 |
| MSKU / 卖家自定义SKU | Merchant SKU | 卖家自己对 SKU 的编码，用于与平台 SKU 对应 | 模型里即 `sku_code` |
| 订单 | Order | 买家一次购买行为产生的单据，含收货地址、支付信息、行项 | `orders` 表 / `Order` 聚合 |
| 订单行项 | Order Item | 订单中的单个商品行（SKU + 数量 + 单价 + 小计） | `order_items` 表 |
| 物流单 / 运单 | Logistics Order / Shipment | 为一个订单派生的发货单据，是渠道、面单、运费、追踪号的容器 | `logistics_orders` |
| 物流渠道 | Logistics Channel | 承运方案/服务商线路，如中国邮政挂号小包、云途专线、DHL 国际快递 | `logistics_channels`（无维护端点） |
| 追踪号 | Tracking Number | 承运商给运单的唯一编号，用于查询轨迹 | `logistics_orders.tracking_number` |
| 面单 / 标签 | Shipping Label | 贴在包裹外的运单标签，含条码与收件信息 | `LogisticsLabel` / `logistics_labels.label_data` |
| 轨迹 | Tracking Trace | 物流节点流水的集合（何时揽收、清关、到达哪） | ⚪ 无轨迹明细表，仅存当前状态 |
| 头程 | First Leg | 货物从国内发往海外仓的运输段 | ⚪ 未建模 |
| 尾程 | Last Mile | 货物到达目的国后到买家手中的最后一段 | 即当前 LogisticsStatus 的后半段 |
| 海外仓 | Overseas Warehouse | 设在目的国的仓库，先备货后本地发货 | `warehouses`（无类型区分） |
| 自营仓 | Self-operated Warehouse | 卖家自有仓库 | 同上 |
| FBA | Fulfillment by Amazon | 亚马逊代发货仓储服务 | ⚪ 未建模 |
| 虚拟仓 / 中转仓 | — | 用于地址显示的虚拟节点 | ⚪ 未建模 |
| 运费试算 | Freight Calculation | 基于重量/体积/目的地/渠道计算运费 | `PriceRule` + `LogisticsFee`（基础费 + 重量费） |
| 计费重 | Chargeable Weight | 取实重与抛重（体积重）较大者计费 | ⚪ 未实现（无抛重公式） |
| 抛重 / 体积重 | Volumetric Weight | 长×宽×高÷系数 得到的虚拟重量 | ⚪ 未实现 |
| 挂号 / 平邮 | Registered / Ordinary Mail | 是否提供全程可追踪服务 | ⚪ 未建模（渠道属性缺失） |
| 清关 | Customs Clearance | 跨境货物进出口报关环节 | ⚪ 未建模 |
| 采购单 | Purchase Order | 向供应商采购的单据 | `purchase_orders` |
| 到货入库 | Receiving | 采购到货后验收并上架、增加库存 | ~~⚪ 未联动~~ → 🔄 **2026-03-22：已联动**——`PUT /api/purchases/{id}/delivered` 在同一事务里按明细行增加收货仓库库存并写一笔采购应付流水（`PurchaseApplicationService.java:256-262`）。⚠️ 但仍**没有「验收/上架」这一层**：不产生入库任务或收货单，不能分批收货、没有质检，库位（location）也不参与 ⇒ 「Receiving」在系统里等于「一步加库存」 |
| 盘点 | Stocktaking / Count | 核对系统库存与实物库存的过程 | `POST /warehouses/{id}/inventory/count` |
| 货位 / 库位 | Location / Bin | 仓库内的物理存放位置编码 | `locations` |
| 可用库存 / 锁定库存 | Available / Locked Stock | 可售数量与已被订单占用但未出库的数量 | ⚪ 未区分，只有一个 `quantity` |
| 安全库存 | Safety Stock | 触发补货的最低库存阈值 | ⚪ 未建模 |
| 在途库存 | In-transit Stock | 已下单采购但未入库的数量 | ⚪ 未建模 |
| 超卖 | Overselling | 卖出了没有库存的商品 | ⚪ 无防护 |
| 缺货 / 断货 | Out of Stock | 库存为零导致无法发货 | `InventoryStatus.OUT_OF_STOCK` |
| 滞销 | Dead Stock / Slow Moving | 长期不动销、占用资金的库存 | ⚪ 未建模 |
| 账期 | Payment Terms | 采购付款的延期天数约定 | ⚪ 未建模 |
| 对账 | Reconciliation | 将平台账单/回款与系统流水逐笔核对 | ⚪ 无对账功能 |
| 毛利 | Gross Margin | 收入 - 采购成本 - 运费 - 平台费用 | ⚪ 无核算引擎 |
| 退款 / 退货 | Refund / Return | 买家退回并退款的流程 | ⚪ 仅 `TransactionType.REFUND` 财务类型枚举 |
| 平台佣金 | Commission | 第三方平台按比例收取的费用 | ⚪ 未建模 |
| 回款 | Settlement / Payout | 平台把销售款结算给卖家 | ⚪ 未建模 |

---

## B · 系统 / 领域术语

| 术语 | 英文 | 定义 | 本系统中的位置 |
|---|---|---|---|
| 限界上下文 | Bounded Context | DDD 中一组内聚模型的边界，有自己独立的语言 | `domain/{order,logistics,warehouse,product,purchase,finance,customer,platform,tenant}` |
| 聚合根 | Aggregate Root | 一组强一致对象的入口，外部只能通过它操作内部对象 | `Order` / `LogisticsOrder` / `Warehouse` / `Product` / `PurchaseOrder` / `FinancialTransaction` / `Customer` / `PlatformIntegration` / `Tenant` |
| 实体 | Entity | 有唯一标识、靠 ID 区分的对象 | `OrderItem`、`Location`、`InventoryRecord`、`SKU`、`User`、`Role` 等 |
| 值对象 | Value Object | 无标识、靠属性值区分的对象 | `ShippingAddress`、`PaymentInfo`、`LogisticsFee`、`ProductPrice`、`ProductAttribute` 等 |
| 领域事件 | Domain Event | 领域中已发生的事实，驱动跨上下文协作 | ~~21 个 `*Event`，经 `DomainEventPublisher` 发布（当前无消费者）~~ → 🔄 **2026-03-22 复测**：**22 个具体 `*Event` + `BaseDomainEvent` 基类 = 23 个类**。消费者**不是没有**，而是**两端各自残缺**：进程内 `infrastructure/messaging/EventHandler.java` 有 4 个 `@EventListener`（`:45` 订单创建→建物流单、`:61` 订单状态变更→仅日志、`:76` 物流完成→仅日志、`:92` 库存不足→建采购单），MQ 侧有 4 个 `@RocketMQMessageListener`（`order-events`×2、`inventory-events`、`logistics-events`）。但**能被真正发出的那几类事件（采购 / 财务 / 商品）在本仓内一个消费者都没有**，而**有消费者的那几类（订单、库存）从来没被发出过**——详见 [../qa/known-issues.md](../qa/known-issues.md) ISS-062。**🔄 同日四测追加**：跨上下文协作只有「事件」这一种写法会被上面这句话误导——采购到货→仓储入库→财务应付这条链**已经交付**，但走的是应用服务里的**同事务同步编排**（`PurchaseApplicationService.java:256-262`，类注释 `:30-32` 明确事件只作对外订阅、仓内不重复消费）。⇒ 判断某个联动是否存在，要看编排调用，不能只看有没有消费者订阅 topic |
| 仓库（Repository） | Repository | 聚合的持久化抽象 | ~~15 个领域接口 + 15 个 `*RepositoryImpl`~~ → 🔄 **2026-03-22 复测 = 26 : 26**（26 个 `domain/**/repository/*Repository` 接口 + 26 个 `*RepositoryImpl`，另有 26 个 `*JpaRepository` Spring Data 接口）。`@Repository` 只标注在 42 个类上 = 21 个 Impl + 21 个 JpaRepository，**剩 5 个 Impl 与 5 个 JpaRepository 不带注解**、要靠 `PersistenceConfig` 的 9 个 `@Bean` 手工注册（双注册面见 [../tech/architecture.md](../tech/architecture.md) §7） |
| 应用层服务 | Application Service | 编排用例、管理事务，不含业务规则 | `application/service/*ApplicationService` |
| 领域服务 | Domain Service | 承载跨聚合的纯业务规则 | `domain/*/service/*` |
| 租户 | Tenant | 一个独立的客户组织（公司/店铺集团），数据逻辑隔离单位 | `tenants` 表；⚠️ 业务表未带 `tenant_id` |
| 角色 | Role | 权限的集合 | `roles` + `role_jpa_entity_permissions` |
| 权限点 | Permission | 最小的操作授权单位（当前为自由字符串，无字典） | `role_jpa_entity_permissions.permission` |
| 平台管理员 | Platform Admin | SaaS 平台方内部运营人员，负责开户与运营参数 | admin-web 的使用者 |
| 卖家 / 商家 | Merchant / Seller | 使用本系统处理自身业务的客户组织 | 对应一个 `tenant` |
| 默认租户 | Default Tenant | 系统启动时自动写入的初始化租户（`DataInitializationConfig`） | 默认账号 `admin` / `123456` |

---

## C · 状态与枚举取值（口径基准）

> 这些是"真相"，前后端、文档、数据库都应以此为基准。完整列级说明见 [`../db/data-dictionary.md`](../db/data-dictionary.md) 第 4 节。

| 对象 | 取值（领域枚举名 → 存储值 → 中文） |
|---|---|
| **订单状态** OrderStatus | `PENDING→pending→待处理`｜`PROCESSING→processing→处理中`｜`CON_FIRMED→confirmed→已确认`｜`SHIPPED→shipped→已发货`｜`COMPLETED→completed→已完成`｜`CANCELLED→cancelled→已取消` |
| **物流状态** LogisticsStatus | `PENDING→pending→待处理`｜`COLLECTED→collected→已揽收`｜`LABEL_GENERATED→generated→已打单`｜`IN_TRANSIT→in_transit→运输中`｜`DELIVERING→delivering→派送中`｜`DELIVERED→delivered→已送达`｜`FAILED→failed→配送失败`｜`CANCELLED→cancelled→已取消` |
| **采购状态** PurchaseStatus | `PENDING→pending→待处理`｜`APPROVED→approved→已批准`｜`REJECTED→rejected→已拒绝`｜`IN_TRANSIT→in_transit→运输中`｜`DELIVERED→delivered→已送达`｜`CANCELLED→cancelled→已取消` |
| **交易类型** TransactionType | `INCOME→income→收入`｜`EXPENSE→expense→支出`｜`REFUND→refund→退款`｜`ADJUSTMENT→adjustment→调整` |
| **交易状态** TransactionStatus | `PENDING→pending`｜`SUCCESS→success`｜`FAILED→failed`｜`CANCELLED→cancelled` |
| **客户状态** CustomerStatus | `ACTIVE→active→活跃`｜`INACTIVE→inactive→非活跃`｜`SUSPENDED→suspended→已暂停`｜`DELETED→deleted→已删除` |
| **库存状态** InventoryStatus | `NORMAL→normal→正常`｜`SHORTAGE→shortage→不足`｜`EXCESS→excess→过剩`｜`OUT_OF_STOCK→out_of_stock→缺货` |
| **库存变动类型** MovementType | `INBOUND→inbound→入库`｜`OUTBOUND→outbound→出库`｜`ADJUSTMENT→adjustment→调整`｜`TRANSFER→transfer→调拨` |
| **平台标识** PlatformName | `AMAZON`｜`EBAY`｜`SHOPIFY`｜`ALIEXPRESS`｜`WISH`｜`MAGENTO`｜`OTHER`（大写名称，无 code 字段） |
| **平台状态** PlatformStatus | `ACTIVE`｜`INACTIVE`｜`DISCONNECTED`｜`ERROR` |
| **租户状态** TenantStatus | `ACTIVE`｜`INACTIVE`｜`SUSPENDED`｜`DELETED` |
| **用户状态** UserStatus | `ACTIVE`｜`INACTIVE`｜`LOCKED`｜`PENDING` |

### 已知口径风险（写文档/做需求时必须注意）

1. **订单状态枚举命名不一致**：领域层常量名是 `CON_FIRMED`（下划线位置异常），持久化层同名枚举写的是 `CONFIRMED`。二者存储值都是 `confirmed`，但**按枚举名做转换的代码会失败**。建议按 [#ISS-00x](../qa/known-issues.md) 统一为 `CONFIRMED`。
2. **存储口径三套并存**：订单/物流/库存/客户用**枚举名大写**，采购/财务用**小写 code**，租户/用户用**手写字符串**（无映射）。前端展示需要三套翻译逻辑。
3. **产品缺少多语言/多币种字段**：`financial_transactions.currency` 是唯一的币种字段，订单与 SKU 无币种 → 跨境多币种暂无法支撑。
