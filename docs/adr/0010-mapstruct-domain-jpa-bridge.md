# ADR-0010：领域模型与 JPA 持久化模型隔离（`*JpaEntity` + MapStruct 双仓储体系）

- 状态：Accepted
- 日期记录：2026-01-25
- 相关：[architecture.md §2](../architecture.md)、[db/data-dictionary.md](../db/data-dictionary.md)、[ADR-0012](./0012-ddd-single-module-layering.md)

## 背景

领域层希望保持框架无关（ADR-0012），同时 Spring Data JPA 是当下最省事的持久化方案。若把 `@Entity` 直接当聚合根用，会带来三个已知问题：懒加载序列化泄漏、JPA 规范约束领域建模（必须无参构造、字段可变、集合被 Hibernate 接管）、以及 API 契约与库结构耦合（见 [api.md §2](../api/api.md) 里"直接序列化领域聚合"造成的 `apiSecret` 外泄）。

## 决策

**两套模型 + 两套仓储 + 显式转换**：

```
domain/{ctx}/repository/XRepository          领域仓储接口（纯 POJO 语义）
      ↑
infrastructure/persistence/repository/XRepositoryImpl     适配器（~~13 个~~ ~~**26 个**~~ **30 个**）
      ↓ 委托
infrastructure/persistence/jpa/repository/XJpaRepository extends JpaRepository（~~13 个~~ ~~**26 个**~~ **29 个**）
      ↕ MapStruct（componentModel=spring，12 个 *Mapper）
infrastructure/persistence/jpa/entity/XJpaEntity  @Entity（~~21 个~~ ~~**40 个**~~ **43 个**）
```

🔎 **2026-01-25 实测计数**（`grep -rl` 于 `infrastructure/persistence/`）：~~`@Entity` = `@Table` = **40 个文件**；`*RepositoryImpl` **26**；`*JpaRepository` **26**；领域侧 `domain/**/*Repository` 接口 **26**；`*Mapper` **12**~~。与 [data-dictionary.md 头部](../db/data-dictionary.md) 的 40 + 2 张 `@ElementCollection` = 运行时 42 张表口径一致。旧记录的 13/13/21 是三个上下文（import/finance/logistics/shop-warehouse）尚未纳入时的数字，已全部过期。
> 🔄 **2026-10-21 复测**（同一口径重跑）：`@Entity` = `@Table` = **43**；`*RepositoryImpl` **30**；`*JpaRepository` **29**；领域侧 `domain/**/*Repository` **30**；`*Mapper` **仍 12**。
> 三点结构性变化：① **JPA 接口比 Impl 少 1**（30 vs 29）——`overview/OverviewStatisticsRepositoryImpl` 没有自己的 `*JpaRepository`，而是**同时注入 7 个别的上下文的 JPA 接口**（`order`/`product`/`warehouse`/`customer`/`purchase`/`platform`/`finance`，`:30-36`）做跨上下文统计读模型；它是"双仓储体系"里唯一不拥有单表适配器的 Impl，也是 [../architecture.md](../architecture.md) §7 第 4 条"应用层跨上下文直连他域仓储"在基础设施层的同一形状；② **Mapper 覆盖率从 12/26 降到 12/30**——**14 个 Impl** import `jpa.mapper.*`（`InventoryRecordRepositoryImpl:23` 借 `WarehouseMapper`、`OverviewStatisticsRepositoryImpl:37` 借 `OrderMapper`，所以 12 个 Mapper 文件服务 14 个 Impl），其余 **16 个 Impl 在类内手写 entity↔领域对象转换**：其中 **12 个**是导入辅助表（6 条导入线各一对 `*FieldMapping` / `*ImportJob`，字段少、无枚举桥接，手写代价可控），**4 个是业务实体**——`Shop`、`ShopWarehouse`、`AuditLog`、`SystemSetting`。新增的 `Shop` 直接跳过了 Mapper 约定，而它带 `shop:credential_write` 凭据语义字段，说明本 ADR 的"显式转换 + 枚举名称桥接"只在前 12 个实体上成立，后来者并没有按约定补 Mapper；③ `@ElementCollection` 仍是 2 张，运行时表数 **43 + 2 = 45**——本轮直连本机 MySQL 查 `information_schema.tables` 实测 `ecommerce_logistics_erp` **正好 45 张**，而 `docs/db/schema.sql` 只有 42 条 `CREATE TABLE`，缺的 3 张就是 `audit_logs`、`shops`、`system_configs`（上一档记录的"运行时 42 张"是 `@Table` 还是 40 时的口径，现已过期）。

- 值对象按「独立表」落库：`ShippingAddress→shipping_addresses`、`PaymentInfo→payment_infos`、`LogisticsLabel→logistics_labels`、`LogisticsFee→logistics_fees`（由聚合根单向 @OneToOne + cascade/orphanRemoval 持有）。
- 子聚合实体（`OrderItem`、`Sku`、`Location`、`PurchaseOrderItem`）不暴露独立 JPA 接口，随聚合根级联读写。
- ✅ 结论仍然成立（2026-01-25 复核）：`application/` 与 `interfaces/` **没有任何一处** import JPA 实体或 Spring Data 仓储——对 `persistence.jpa.entity` / `persistence.jpa.repository` / `jakarta.persistence` / `org.springframework.data.jpa` 四类 import 的 grep 命中数 = **0**。这条边界守住了。
  🔄 **2026-10-21 复测：仍然 0 处**（同一四类 pattern 重跑），且子聚合约定（`OrderItem`/`Sku`/`Location`/`PurchaseOrderItem` 无独立 `*JpaRepository`，29 个 JPA 接口清单里确实没有它们）与四个值对象独立表（`shipping_addresses`/`payment_infos`/`logistics_labels`/`logistics_fees`，本机库实测 4 张都在）也都成立。
  ⚠️ 但**相邻的另一条边界没守住**：`interfaces → infrastructure` 有 ~~**11 处**~~ → 🔄 **21 处** import，其中 3 处正是本 ADR 想隔离的持久化 Mapper（见后果 3；消费侧现在 **4 个**控制器），构成 `interfaces` ↔ `infrastructure` 的**双向包依赖**。

## 后果

**正面**
- 领域聚合保持 POJO（`domain/` 内 JPA 注解 0 处），状态机与不变式可独立测试。
- 库结构与领域模型可各自演化；`docs/db/schema.sql` 与实体 diff 可分离评审。

**代价 / 已发现的问题**
1. **样板翻倍**：~~21 实体 + 13 领域接口 + 13 JpaRepository + 13 Impl + 12 Mapper~~ → ~~**2026-01-25 实测：40 实体 + 26 领域接口 + 26 JpaRepository + 26 Impl + 12 Mapper = 130 个持久化侧文件**承载 9 个上下文~~ → 🔄 **2026-10-21 复测：43 实体 + 30 领域接口 + 29 JpaRepository + 30 Impl + 12 Mapper = 144 个持久化侧文件**，承载 **13 个限界上下文 + `common`**（原记录的 9 见 [ADR-0001](./0001-domain-driven-design.md) 的状态补充）。一个字段变更要动 3~4 处，且**编译期不会提醒漏改**。
2. **枚举名称桥接**：领域与 JPA 枚举常量名不同，靠 `*Mapper` 硬编码 switch/循环转换（`CON_FIRMED↔CONFIRMED`、`COLLECTED↔PICKED_UP`、`DELIVERING↔OUT_FOR_DELIVERY`、`FAILED↔EXCEPTION`）；`OrderMapper`/~~`LogisticsOrderMapper`~~ **`WarehouseMapper`** 甚至用**反射**注入 status 字段。新增枚举常量极易漏改且无编译期保护 ⇒ 数据字典 R-02。
   🔎 **2026-01-25 复核反射点（`grep -rn "java.lang.reflect\|setAccessible\|getDeclaredField" jpa/mapper/`）**：命中文件只有 **2 个**——`OrderMapper.java:72-73`（`getDeclaredConstructor` + `setAccessible` 绕过聚合无参构造限制）、`OrderMapper.java:100-101`（`getDeclaredField(fieldName)` + `setAccessible`，即「反射注入 status」的通用写值器）与 `WarehouseMapper.java:48-71`（同样的构造绕过，再对 `warehouseId`/`tenantId`/`warehouseName`/`warehouseCode`/`address` 等**逐字段反射赋值**（`getDeclaredField` 共 **12** 处、`setAccessible` 13 处，含 `capacity`、`active` 与集合字段）。**`LogisticsOrderMapper` 内无任何反射**（旧记录把 `WarehouseMapper` 误写成它，且 `WarehouseMapper` 的反射面远大于 Order：12 个字段 vs 1 个）。⇒ 反射越界的真实范围是「**2 个 Mapper / 13 处字段级反射**（`WarehouseMapper` 12 + `OrderMapper` 1，另有 2 处 `getDeclaredConstructor` 绕过无参构造）」，且**绕开的是聚合根的不变式封装**（直接写字段而非经领域方法重建，`Warehouse` 聚合等于**没有构造期校验**），比"样板多"更值得关注。
3. **反向依赖**：~~`jpa/mapper/OrderMapper.java:16-17` import `interfaces/dto/OrderDto`、`OrderItemDto` —— infrastructure 依赖 interfaces，破坏了本 ADR 想建立的边界；`OrderController` 也直接注入该 infrastructure Mapper。~~
   🔴 **2026-01-25 复测：这类越界是 3 个 Mapper 而不是 1 个，且已形成双向环**。`infrastructure/persistence/jpa/mapper/` 下 import `interfaces.dto.*` 的文件：
   - `OrderMapper.java:18-19` → `interfaces.dto.order.OrderDto` / `OrderItemDto`；
   - `ProductMapper.java:17-19` → `interfaces.dto.product.ProductAttributeDto` / `ProductDto` / `ProductSkuDto`；
   - `finance/FinancialTransactionMapper.java:10` → `interfaces.dto.finance.FinancialTransactionDto`。

   对应的 `interfaces → infrastructure` 反向 import 也正是这三个 Mapper：~~`OrderController.java:19`、`ProductController.java:23`、`FinanceController.java:21`~~ ⇒ **`interfaces.controller` ↔ `infrastructure.persistence.jpa.mapper` 双向依赖**（DTO 侧 `interfaces.dto.*` 也被基础设施消费）。
   ⚠️ 全量口径：~~`interfaces → infrastructure` 共 **11 处** import = 上述 3 个 Mapper + `OrderController.java:20` `infrastructure.security.TenantContext` + `AuthController.java:19` `infrastructure.security.JwtTokenProvider` + 6 个 `*ImportController` 各 1 处 `infrastructure.importing.ExcelSourceParser`（`OrderImport:27`/`LogisticsImport:27`/`FinanceImport:27`/`ProductImport:26`/`WarehouseImport:25`/`CustomerImport:27`）~~。
   > 🔄 **2026-10-21 复测**：越界的**源头没变**（`infrastructure/persistence/jpa/mapper/` 下 import `interfaces.dto.*` 仍只有 `OrderMapper:18-19`、`ProductMapper:17-19`、`finance/FinancialTransactionMapper:10` 三个文件），但**消费侧从 3 个控制器涨到 4 个**——新增 `OverviewController.java:15` 也 import `mapper.order.OrderMapper`（借订单 Mapper 做统计读模型）。行号全部下移：`OrderController:22`、`ProductController:26`、`FinanceController:24`。
   > **`interfaces → infrastructure` 全量从 11 处涨到 21 处**，构成变了：4 个 Mapper（`OrderController:22`、`ProductController:26`、`FinanceController:24`、`OverviewController:15`）＋ **9 处** `infrastructure.security.TenantContext`（`RoleController:24`、`UserController:26`、`OrderController:23`、`PlatformIntegrationController:34`、`ShopController:25`、`PurchaseController:25`、`FinanceController:25`、`WarehouseController:25`、`OverviewController:16`）＋ 1 处 `JwtTokenProvider`（`AuthController:24`）＋ 6 处 `ExcelSourceParser`（六个 `*ImportController`：`OrderImport:29`、`CustomerImport:29`、`LogisticsImport:29`、`FinanceImport:29`、`ProductImport:28`、`WarehouseImport:28`）＋ **1 处新族**`infrastructure.audit.AuditRequestContext`（`SystemConfigController:16`）。
   > 结论要分开看：本 ADR 最想守的那条边界**仍然守住了**——`application/` 与 `interfaces/` 里 import `persistence.jpa.entity` / `persistence.jpa.repository` / `jakarta.persistence` / `org.springframework.data.jpa` 命中数**仍是 0**（本轮复测）。烂掉的是相邻那条：**`TenantContext` 被 9 个控制器直接引用**，等于把"基础设施层的线程上下文"变成接口层的公共 API；它比 3 个 Mapper 的越界更难撤，因为它承载的是每个控制器都要用的租户解析。建议按 `../architecture.md` §7 第 3 条收口：把 `TenantContext` 的读接口上移为 `application/service/support/` 的门面，控制器只依赖应用层。
4. **注册方式不一致**：~~`Customer/FinancialTransaction/PurchaseOrder/PlatformIntegration` 4 个 Impl 靠 `@Repository` 扫描注册；`Role/User/Tenant/Product` 4 个**既有 `@Repository` 又在 `PersistenceConfig` 里 `@Bean`** → 同类型双注册风险。~~
   🔎 **2026-01-25 全量清点（26 个 Impl）**：**21 个**标 `@Repository`；`PersistenceConfig` 提供 **9 个 `@Bean`**（其中 **8 个是 `new *RepositoryImpl`**：`Order:48`、`LogisticsOrder:54`、`LogisticsChannel:62`、`Warehouse:70`、`InventoryRecord:78`、`User:92`、`Role:100`、`Tenant:106`，外加 1 个别名 `productRepository:86-88`）。⇒ 只靠 `@Bean` 注册（无 `@Repository`）的是 **5 个**：`Order`、`LogisticsOrder`、`LogisticsChannel`、`Warehouse`、`InventoryRecord`；**双注册的是 4 个**：`User`、`Role`、`Tenant`、`Product`（扫描名 + `@Bean` 名各一份实例，注入点取的是 `@Bean` 方法名那份）。其余 17 个仅 `@Repository`。
   ⚠️ 双注册的直接代价：给 `UserRepositoryImpl`/`RoleRepositoryImpl` 加的 `@Transactional(readOnly = true)` 是否落在**真正被注入的那一份实例**上，**当前无证据可证**（没有 Actuator / `@SpringBootTest` 上下文探针）——详见 [backend-standards.md §8](../tech/backend-standards.md) 的未验证项与 ISS-061。
   > 🔄 **2026-10-21 全量复测（30 个 Impl，`PersistenceConfig` 已迁到 `infrastructure/config/`）**：标 `@Repository` 的 **24 个**；`PersistenceConfig` 现有 **10 个 `@Bean`**（`:54,60,68,76,84,92,98,106,112,118`），其中 **9 个是 `new *RepositoryImpl`**（`Order:56`、`LogisticsOrder:64`、`LogisticsChannel:72`、`Warehouse:80`、`InventoryRecord:88`、`User:102`、`Role:108`、`Tenant:114`、`OverviewStatistics:128`），外加 1 个别名 `productRepository:93-94`。
   > ⇒ 口径变化三处：**只靠 `@Bean` 注册（无 `@Repository`）从 5 个变 6 个**（新增 `OverviewStatistics`）；**双注册从 4 个减到 3 个**——`Product` 的 `@Bean` 现在是 `productRepository(ProductRepositoryImpl)`，注入并返回扫描到的那一份实例，**不再新建第二个对象**，所以 `ProductRepositoryImpl` 只有一份；真正仍双注册的只剩 `User`、`Role`、`Tenant`；**其余 21 个仅 `@Repository`**。
   > ⚠️ 新增的 `Shop`、`AuditLog`、`SystemSetting` 三个 Impl **没有进 `PersistenceConfig`**（沿用 `@Repository`），所以"两种风格并存"这条偏离仍然成立，且第 4 条建议（只保留 `@Repository`、删掉重复 `@Bean`）至今未执行。
   > ⚠️ **但"无证据可证"这一半仍然成立**（本轮复测）：`src/test/java` 下 `@SpringBootTest` 命中数 = **0**，全仓 102 个测试类都是单测/MockMvc 切片，没有任何装配上下文探针；`UserRepositoryImpl` 的 `readOnly` 行号（`:54,60,66,72,81`）与 `RoleRepositoryImpl`（`:39,46,53`）只能证明"注解写在类里"，证不了它落在**被注入的那一份实例**上。ISS-061 的探针缺口未修。
5. 命名瑕疵：~~`@ElementCollection` 未标 `@CollectionTable`，生成隐式表名 `role_jpa_entity_permissions`（含实体类名前缀）~~。
   > 🔄 **2026-10-21 复测：一半已修**。`UserJpaEntity:64-67` 现在显式标了 `@CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))`；**`RoleJpaEntity:39-41` 仍未标**，隐式表名 `role_jpa_entity_permissions` 照旧存在于运行时库（本机 `information_schema` 实测有这张表）。下面第 4 条建议只剩 Role 一处。
6. 🆕 **2026-01-25 新发现（写这里因为它是"双模型 + 反射桥接"的直接产物）**：`WarehouseMapper.toDomainAggregate`（`jpa/mapper/WarehouseMapper.java:46-89`）在**持久化桥接层替领域对象发明租户**——`:59-60` 为 `warehouseJpaEntity.getTenantId() != null ? … : "default-tenant"`。也就是说**库里 `tenant_id` 为 NULL 的仓库，读出后被归入默认租户**，与领域侧 `FinancialTransactionServiceImpl.java:61`、应用侧 ~~10 个~~ `DEFAULT_TENANT_ID` 常量构成同一套"多层兜底"。这类兜底让"缺租户"这个数据问题在读取路径上**不可见**，是 ISS-050/ISS-051 归因链上的一环（口径见 [security-findings.md M-05](../security-findings.md)）。
   > 🔄 **2026-10-21 复测**：**读路径这一半没修**——`WarehouseMapper.java:60` 的 `: "default-tenant"` 原样还在，NULL 租户的仓库读出后仍归默认租户。
   > **写路径这一半已收口**：缺省归属统一走 `application/service/support/TenantAccessGuard.resolveWriteTenant(requested, legacyFallback)`（`:77-87`，先 `normalizeRequested` → 再取登录租户 → 最后才落到 `legacyFallback`），导入落默认桶的问题（ISS-093）由它挡掉。
   > **但兜底的"份数"变多了**：自己声明 `String DEFAULT_TENANT_ID = "default-tenant"` 的类从 10 个涨到 **13 个**（`application/service/**` 12 个 + `domain/finance/service/FinancialTransactionServiceImpl:26`），另有 3 处不走常量的裸字面量（`WarehouseMapper:60`、`OrderCreatedEventConsumer:41`、`FinanceController:125`）。矛盾点：`infrastructure/security/TenantContext:14` 已经把这个值定义成唯一事实源 `LEGACY_TENANT_ID`，`TenantAccessGuard:39` 也确实把它转别了一次名（`public static final String LEGACY_TENANT_ID = TenantContext.LEGACY_TENANT_ID;`），注释（`TenantAccessGuard:90-97`）还写明了"两边口径不一致就把客户端判成 400"——**可 13 个调用方一律传自己那份 `DEFAULT_TENANT_ID`，没有一个用守卫暴露的别名**。收口方向是让 `legacyFallback` 参数默认取 `TenantContext.LEGACY_TENANT_ID`，删掉 13 份重复声明。

7. 🆕 **2026-01-25 二次实测新增（同一条"双模型 + 桥接"代价，且比第 6 条严重）**：桥接层在重建聚合时**无条件清空领域事件**，把"事件驱动"这件事从结构上掏空了。
   - 机制：`OrderMapper.java:90-91` 与 `WarehouseMapper.java:98-101` 用反射 `setField(agg, "domainEvents", new ArrayList<>())`；`ProductMapper.java:82` 用 `@Mapping(target = "domainEvents", ignore = true)`；其余聚合靠构造器/Builder 初始化为空列表（`Order.java:63,98`、`Warehouse.java:43,73`、`LogisticsOrder.java:31`）。⇒ **凡经 `toDomainAggregate(...)` 返回的对象，`domainEvents` 恒空**。
   - 触发面：仓储实现 `save()` 返回映射副本，而调用方普遍写成 `return xxxRepository.save(x)`（实测 ~~**57 处 / 14 个类**~~ → 🔄 2026-10-21 **56 处 / 15 个类**：5 个应用服务 + 10 个领域服务，领域侧集中在 `domain/{order,platform,product,warehouse,logistics,customer}/service/`）。此时"注册在 x 上的事件"与"最终被交给发布器的那个对象"是两个实例；`DomainEventPublisher.java:40`（~~`:40`~~ 现同一行仍是 `if (events != null && !events.isEmpty())`）见空列表静默 return，**没有任何日志线索**。
   - 实测后果：~~整个探测会话只有 4 个事件类型真正发布过（采购/财务/商品/`SKUCreated`），订单、客户、物流、仓储、平台五个聚合**一次也没发出去**~~；而反例正好证明机制——~~`PurchaseOrderRepositoryImpl.save():42-49`~~ 与 `FinancialTransactionRepositoryImpl.save():48-51` 返回 **void**（🔄 现分别是 `:51` 与 `:48`，全会话**只有这 2 个** `void save()`），迫使服务写 `save(x); return x;`，事件因此活了下来。详见 **ISS-062**（[../qa/known-issues.md](../qa/known-issues.md)）与 [ADR-0006](./0006-rocketmq-domain-events.md) 的"更新"段。
   > 🔄 **2026-10-21 复测（ISS-062 / ISS-097 已闭环，但闭环方式是补偿而不是拆除）**：
   > **成因仍在原处**——`OrderMapper.java:91`、`WarehouseMapper.java:99-101` 的反射置空、`ProductMapper.java:82` 的 `ignore = true` **一行都没改**，"凡经 `toDomainAggregate(...)` 返回的对象 `domainEvents` 恒空"这条机制今天依然成立。
   > **收口靠新增的搬运桥**：`domain/common/BaseAggregateRoot.java:48-63` 加了 `static carryOverEvents(source, target)`（反射取双方的 `domainEvents` 列表再 `addAll`），在 **5 个仓储实现**的 `save()` 里调用：`OrderRepositoryImpl:44`、`PlatformIntegrationRepositoryImpl:69`、`WarehouseRepositoryImpl:36`、`LogisticsOrderRepositoryImpl:47`、`CustomerRepositoryImpl:50`；加上本来就返回 void 的采购/财务，与商品那条由 `ProductApplicationService:104` 直接往聚合列表里 `add` 的路径，**8 个注册事件的聚合现在都有事件能到发布器**（进程内「下单→生成物流单」「缺货→生成采购单」实测各执行一次）。
   > **对本 ADR 建议的影响**：第 7 条原本提的两个收口方案（`save()` 统一返回 void/ID，或映射保留 `domainEvents` 引用）**都没选**，选的是第三条——"副本生成后再把事件搬过去"。它能工作，但把隐式约定加厚了一层：**新写一个返回映射副本的 `save()` 时必须记得手调 `carryOverEvents`**，漏调不报错、不失败，只是事件静默消失（正是 ISS-062 的症状形状）。所以原来那条"用一条真实应用服务集成测试钉住事件确实发布"的建议**仍未落地**：`@SpringBootTest` 命中数 0，现有测试没有一条经过真实 Mapper。
   - 与本篇建议的关系：这是"桥接层用反射装配领域对象"这类隐式约定的必然产物——**没有编译期保护，也没有测试保护**（`DomainEventPublisherTest` 用的是测试自造的 `EventCarrier` 替身，从不经过真实 Mapper）。建议在 Mapper 契约上显式化：要么仓储 `save()` 统一返回 void/ID 并由调用方持有聚合，要么让映射保留 `domainEvents` 引用，并用一条"真实应用服务调用后事件确实发布"的集成测试钉住。

## 建议

> 🔄 **2026-10-21 逐条对账（4 条建议：1 条半落地，3 条未动）**
> ① ArchUnit：**未引入**（`pom.xml` 与 `src/test/java` 全仓 grep `archunit` = 0 命中），所以「infrastructure 不得依赖 interfaces」目前**没有任何工具保护**，越界从 3 个 Mapper 涨到 4 个控制器、`interfaces → infrastructure` 从 11 处涨到 21 处时不会有人被拦下——这条建议的代价已经按预测的方式发生了。
> ② 统一注册方式：**未执行且方向相反**（`PersistenceConfig` 的 `@Bean` 从 9 个增到 10 个，新增了 `OverviewStatistics`）；唯一的改善是 `Product` 从双注册变成了别名注入，双注册 4 → 3。
> ③ 枚举桥接改单一映射表：**未执行**，且反射面已量化固定在 2 个 Mapper / 15 处 `setAccessible` + 13 处 `getDeclaredField` + 2 处 `getDeclaredConstructor`（本轮复测与 2026-01-25 数字一致，没有继续扩散，也没有收敛）。
> ④ `@CollectionTable` 显式命名：**半落地**——`UserJpaEntity:65` 已写 `name = "user_roles"`，`RoleJpaEntity:39` 仍未写。

- 用 ArchUnit 固化「infrastructure 不得依赖 interfaces」，并把 `OrderDto` 的映射职责移回 interfaces 层（controller/assembler 负责 DTO，Mapper 只做 domain↔entity）。
- 统一 `*RepositoryImpl` 的注册方式（推荐只保留 `@Repository`，删除 `PersistenceConfig` 中重复的 `@Bean`）。
- 枚举桥接改为**单一映射表**（如 `OrderStatus` 上定义 `toJpa()/fromJpa()` 或用 `@MappedSuperclass` 转换器），并补往返单测（见 [testing.md §5](../testing.md) P2 项）。
- 给 `@ElementCollection` 显式命名 `@CollectionTable(name="role_permissions", joinColumns=@JoinColumn(name="role_id"))`。
