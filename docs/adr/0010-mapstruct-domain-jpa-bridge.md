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
infrastructure/persistence/repository/XRepositoryImpl     适配器（~~13 个~~ **26 个**）
      ↓ 委托
infrastructure/persistence/jpa/repository/XJpaRepository extends JpaRepository（~~13 个~~ **26 个**）
      ↕ MapStruct（componentModel=spring，12 个 *Mapper）
infrastructure/persistence/jpa/entity/XJpaEntity  @Entity（~~21 个~~ **40 个**）
```

🔎 **2026-01-25 实测计数**（`grep -rl` 于 `infrastructure/persistence/`）：`@Entity` = `@Table` = **40 个文件**；`*RepositoryImpl` **26**；`*JpaRepository` **26**；领域侧 `domain/**/*Repository` 接口 **26**；`*Mapper` **12**。与 [data-dictionary.md 头部](../db/data-dictionary.md) 的 40 + 2 张 `@ElementCollection` = 运行时 42 张表口径一致。旧记录的 13/13/21 是三个上下文（import/finance/logistics/shop-warehouse）尚未纳入时的数字，已全部过期。

- 值对象按「独立表」落库：`ShippingAddress→shipping_addresses`、`PaymentInfo→payment_infos`、`LogisticsLabel→logistics_labels`、`LogisticsFee→logistics_fees`（由聚合根单向 @OneToOne + cascade/orphanRemoval 持有）。
- 子聚合实体（`OrderItem`、`Sku`、`Location`、`PurchaseOrderItem`）不暴露独立 JPA 接口，随聚合根级联读写。
- ✅ 结论仍然成立（2026-01-25 复核）：`application/` 与 `interfaces/` **没有任何一处** import JPA 实体或 Spring Data 仓储——对 `persistence.jpa.entity` / `persistence.jpa.repository` / `jakarta.persistence` / `org.springframework.data.jpa` 四类 import 的 grep 命中数 = **0**。这条边界守住了。
  ⚠️ 但**相邻的另一条边界没守住**：`interfaces → infrastructure` 有 **11 处** import，其中 3 处正是本 ADR 想隔离的持久化 Mapper（见后果 3），构成 `interfaces` ↔ `infrastructure` 的**双向包依赖**。

## 后果

**正面**
- 领域聚合保持 POJO（`domain/` 内 JPA 注解 0 处），状态机与不变式可独立测试。
- 库结构与领域模型可各自演化；`docs/db/schema.sql` 与实体 diff 可分离评审。

**代价 / 已发现的问题**
1. **样板翻倍**：~~21 实体 + 13 领域接口 + 13 JpaRepository + 13 Impl + 12 Mapper~~ → **2026-01-25 实测：40 实体 + 26 领域接口 + 26 JpaRepository + 26 Impl + 12 Mapper = 130 个持久化侧文件**承载 9 个上下文，一个字段变更要动 3~4 处，且**编译期不会提醒漏改**。
2. **枚举名称桥接**：领域与 JPA 枚举常量名不同，靠 `*Mapper` 硬编码 switch/循环转换（`CON_FIRMED↔CONFIRMED`、`COLLECTED↔PICKED_UP`、`DELIVERING↔OUT_FOR_DELIVERY`、`FAILED↔EXCEPTION`）；`OrderMapper`/~~`LogisticsOrderMapper`~~ **`WarehouseMapper`** 甚至用**反射**注入 status 字段。新增枚举常量极易漏改且无编译期保护 ⇒ 数据字典 R-02。
   🔎 **2026-01-25 复核反射点（`grep -rn "java.lang.reflect\|setAccessible\|getDeclaredField" jpa/mapper/`）**：命中文件只有 **2 个**——`OrderMapper.java:72-73`（`getDeclaredConstructor` + `setAccessible` 绕过聚合无参构造限制）、`OrderMapper.java:100-101`（`getDeclaredField(fieldName)` + `setAccessible`，即「反射注入 status」的通用写值器）与 `WarehouseMapper.java:48-71`（同样的构造绕过，再对 `warehouseId`/`tenantId`/`warehouseName`/`warehouseCode`/`address` 等**逐字段反射赋值**（`getDeclaredField` 共 **12** 处、`setAccessible` 13 处，含 `capacity`、`active` 与集合字段）。**`LogisticsOrderMapper` 内无任何反射**（旧记录把 `WarehouseMapper` 误写成它，且 `WarehouseMapper` 的反射面远大于 Order：12 个字段 vs 1 个）。⇒ 反射越界的真实范围是「**2 个 Mapper / 13 处字段级反射**（`WarehouseMapper` 12 + `OrderMapper` 1，另有 2 处 `getDeclaredConstructor` 绕过无参构造）」，且**绕开的是聚合根的不变式封装**（直接写字段而非经领域方法重建，`Warehouse` 聚合等于**没有构造期校验**），比"样板多"更值得关注。
3. **反向依赖**：~~`jpa/mapper/OrderMapper.java:16-17` import `interfaces/dto/OrderDto`、`OrderItemDto` —— infrastructure 依赖 interfaces，破坏了本 ADR 想建立的边界；`OrderController` 也直接注入该 infrastructure Mapper。~~
   🔴 **2026-01-25 复测：这类越界是 3 个 Mapper 而不是 1 个，且已形成双向环**。`infrastructure/persistence/jpa/mapper/` 下 import `interfaces.dto.*` 的文件：
   - `OrderMapper.java:18-19` → `interfaces.dto.order.OrderDto` / `OrderItemDto`；
   - `ProductMapper.java:17-19` → `interfaces.dto.product.ProductAttributeDto` / `ProductDto` / `ProductSkuDto`；
   - `finance/FinancialTransactionMapper.java:10` → `interfaces.dto.finance.FinancialTransactionDto`。

   对应的 `interfaces → infrastructure` 反向 import 也正是这三个 Mapper：`OrderController.java:19`、`ProductController.java:23`、`FinanceController.java:21` ⇒ **`interfaces.controller` ↔ `infrastructure.persistence.jpa.mapper` 双向依赖**（DTO 侧 `interfaces.dto.*` 也被基础设施消费）。
   ⚠️ 全量口径：`interfaces → infrastructure` 共 **11 处** import = 上述 3 个 Mapper + `OrderController.java:20` `infrastructure.security.TenantContext` + `AuthController.java:19` `infrastructure.security.JwtTokenProvider` + 6 个 `*ImportController` 各 1 处 `infrastructure.importing.ExcelSourceParser`（`OrderImport:27`/`LogisticsImport:27`/`FinanceImport:27`/`ProductImport:26`/`WarehouseImport:25`/`CustomerImport:27`）。
4. **注册方式不一致**：~~`Customer/FinancialTransaction/PurchaseOrder/PlatformIntegration` 4 个 Impl 靠 `@Repository` 扫描注册；`Role/User/Tenant/Product` 4 个**既有 `@Repository` 又在 `PersistenceConfig` 里 `@Bean`** → 同类型双注册风险。~~
   🔎 **2026-01-25 全量清点（26 个 Impl）**：**21 个**标 `@Repository`；`PersistenceConfig` 提供 **9 个 `@Bean`**（其中 **8 个是 `new *RepositoryImpl`**：`Order:48`、`LogisticsOrder:54`、`LogisticsChannel:62`、`Warehouse:70`、`InventoryRecord:78`、`User:92`、`Role:100`、`Tenant:106`，外加 1 个别名 `productRepository:86-88`）。⇒ 只靠 `@Bean` 注册（无 `@Repository`）的是 **5 个**：`Order`、`LogisticsOrder`、`LogisticsChannel`、`Warehouse`、`InventoryRecord`；**双注册的是 4 个**：`User`、`Role`、`Tenant`、`Product`（扫描名 + `@Bean` 名各一份实例，注入点取的是 `@Bean` 方法名那份）。其余 17 个仅 `@Repository`。
   ⚠️ 双注册的直接代价：给 `UserRepositoryImpl`/`RoleRepositoryImpl` 加的 `@Transactional(readOnly = true)` 是否落在**真正被注入的那一份实例**上，**当前无证据可证**（没有 Actuator / `@SpringBootTest` 上下文探针）——详见 [backend-standards.md §8](../tech/backend-standards.md) 的未验证项与 ISS-061。
5. 命名瑕疵：`@ElementCollection` 未标 `@CollectionTable`，生成隐式表名 `role_jpa_entity_permissions`（含实体类名前缀）。
6. 🆕 **2026-01-25 新发现（写这里因为它是"双模型 + 反射桥接"的直接产物）**：`WarehouseMapper.toDomainAggregate`（`jpa/mapper/WarehouseMapper.java:46-89`）在**持久化桥接层替领域对象发明租户**——`:59-60` 为 `warehouseJpaEntity.getTenantId() != null ? … : "default-tenant"`。也就是说**库里 `tenant_id` 为 NULL 的仓库，读出后被归入默认租户**，与领域侧 `FinancialTransactionServiceImpl.java:61`、应用侧 10 个 `DEFAULT_TENANT_ID` 常量构成同一套"多层兜底"。这类兜底让"缺租户"这个数据问题在读取路径上**不可见**，是 ISS-050/ISS-051 归因链上的一环（口径见 [security-findings.md M-05](../security-findings.md)）。

7. 🆕 **2026-01-25 二次实测新增（同一条"双模型 + 桥接"代价，且比第 6 条严重）**：桥接层在重建聚合时**无条件清空领域事件**，把"事件驱动"这件事从结构上掏空了。
   - 机制：`OrderMapper.java:90-91` 与 `WarehouseMapper.java:98-101` 用反射 `setField(agg, "domainEvents", new ArrayList<>())`；`ProductMapper.java:82` 用 `@Mapping(target = "domainEvents", ignore = true)`；其余聚合靠构造器/Builder 初始化为空列表（`Order.java:63,98`、`Warehouse.java:43,73`、`LogisticsOrder.java:31`）。⇒ **凡经 `toDomainAggregate(...)` 返回的对象，`domainEvents` 恒空**。
   - 触发面：仓储实现 `save()` 返回映射副本，而调用方普遍写成 `return xxxRepository.save(x)`（实测 **57 处 / 14 个类**）。此时"注册在 x 上的事件"与"最终被交给发布器的那个对象"是两个实例；`DomainEventPublisher.java:40` 见空列表静默 return，**没有任何日志线索**。
   - 实测后果：整个探测会话只有 4 个事件类型真正发布过（采购/财务/商品/`SKUCreated`），订单、客户、物流、仓储、平台五个聚合**一次也没发出去**；而反例正好证明机制——`PurchaseOrderRepositoryImpl.save():42-49` 与 `FinancialTransactionRepositoryImpl.save():48-51` 返回 **void**，迫使服务写 `save(x); return x;`，事件因此活了下来。详见 **ISS-062**（[../qa/known-issues.md](../qa/known-issues.md)）与 [ADR-0006](./0006-rocketmq-domain-events.md) 的"更新"段。
   - 与本篇建议的关系：这是"桥接层用反射装配领域对象"这类隐式约定的必然产物——**没有编译期保护，也没有测试保护**（`DomainEventPublisherTest` 用的是测试自造的 `EventCarrier` 替身，从不经过真实 Mapper）。建议在 Mapper 契约上显式化：要么仓储 `save()` 统一返回 void/ID 并由调用方持有聚合，要么让映射保留 `domainEvents` 引用，并用一条"真实应用服务调用后事件确实发布"的集成测试钉住。

## 建议

- 用 ArchUnit 固化「infrastructure 不得依赖 interfaces」，并把 `OrderDto` 的映射职责移回 interfaces 层（controller/assembler 负责 DTO，Mapper 只做 domain↔entity）。
- 统一 `*RepositoryImpl` 的注册方式（推荐只保留 `@Repository`，删除 `PersistenceConfig` 中重复的 `@Bean`）。
- 枚举桥接改为**单一映射表**（如 `OrderStatus` 上定义 `toJpa()/fromJpa()` 或用 `@MappedSuperclass` 转换器），并补往返单测（见 [testing.md §5](../testing.md) P2 项）。
- 给 `@ElementCollection` 显式命名 `@CollectionTable(name="role_permissions", joinColumns=@JoinColumn(name="role_id"))`。
