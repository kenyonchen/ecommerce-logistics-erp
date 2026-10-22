# ADR-0012：DDD 四层架构以单 Maven 模块 + 包约定实现

- 状态：Accepted（存在实现偏差）
- 日期记录：2026-01-25（依据现有代码回溯记录）
- 相关：[architecture.md §1/§7](../architecture.md)、[ADR-0010](./0010-mapstruct-domain-jpa-bridge.md)

## 背景

系统需要覆盖 9 个业务上下文（订单、物流、仓储、产品、采购、财务、客户、平台集成、租户），业务规则集中在状态流转与跨上下文协作（下单→物流→库存→财务）。团队规模小、交付节奏快，不希望一开始就引入多模块构建与分布式部署复杂度。

## 决策

采用经典 DDD 四层，**单 Maven 模块内以包分隔**：

```
interfaces  →  application  →  domain  ←  infrastructure
```

- 领域层持有一等模型（`Order`、`Warehouse` 等聚合 + 值对象 + 领域事件 + 仓储**接口**）。
- 基础设施层实现仓储接口（`*RepositoryImpl`）、承担 JPA/Spring Data 依赖。
- 根类 `EcommerceLogisticsApplication` 对四个顶层包统一 `@ComponentScan`。

## 后果

**正面**
- 上手成本低，一次 `mvn spring-boot:run` 即可跑通全链路；重构跨层时不需要改构建配置。
- 领域模型是纯 POJO：`domain/` 内 JPA 注解（`@Entity/@Table/@Column/@Enumerated`）**0 处**，聚合的状态机与不变式可脱离 Spring 单测。

**代价 / 已发生的偏差**
1. **分层只靠约定，没有编译期强制**，已出现 ~~3~~ **5** 类越界（🔎 **2026-01-25 逐条重测，数量均有变化**）：
   - ~~`domain/*/service/` 有 16 个类标 `@Service`，`PlatformIntegrationServiceImpl.java:22` 用 `@Autowired`~~ → 现为 **`domain/` 下 20 个类标 `@Service`**；`@Autowired` 在 domain 层**只剩 1 处**，位置漂到 `domain/platform/service/PlatformIntegrationServiceImpl.java:40`（该类另有 `:41` 起的构造器，两种注入方式并存）。领域层依赖 Spring 的结论不变。
   - ~~`infrastructure/persistence/jpa/mapper/OrderMapper.java:16-17` import `interfaces/dto/OrderDto`~~ → **不是孤例，实测 3 个 mapper 都反向依赖接口层**：`OrderMapper:18-19`（`OrderDto`、`OrderItemDto`）、**`ProductMapper:17-19`**（`ProductDto`、`ProductSkuDto`、`ProductAttributeDto`）、**`mapper/finance/FinancialTransactionMapper`**。原条目只列了 OrderMapper，低估了范围——这条越界是**基础设施 → 接口层**的普遍形态，不是单点失误。
   - `LogisticsApplicationService.java:18` 直接持有他上下文仓储 `domain/order/repository/OrderRepository`（本类自有的是 `:11-12` 的 `LogisticsChannelRepository`/`LogisticsOrderRepository`，另 `:13` 还直连 `domain/logistics/service/LogisticsTrackingService`）→ 跨上下文的隐式耦合。同类形态还有 `LogisticsOrderResolver -> order`、`{Order,Logistics,Product,Customer}ImportApplicationService -> platform`、`CustomerImportApplicationService -> customer+platform`（即**导入类应用服务普遍跨两个上下文**）。
   - 🔎 **2026-01-25 本轮新查出④：`application → interfaces`，而且是规模最大的一类越界**：`application/service/` 下 **9 个类共 69 处** `import ...interfaces.dto.*`（`OrderImportApplicationService` 10、`LogisticsImportApplicationService` 10、`ProductImportApplicationService` 9、`CustomerImportApplicationService` 9、`WarehouseImportApplicationService` 9、`finance/FinanceImportApplicationService` 9、`platform/PlatformIntegrationApplicationService` 5、`LogisticsApplicationService` 6、`ShopWarehouseApplicationService` 2）。根因是导入类应用服务把「上传预览 / 导入任务」这类 **Web 契约 DTO 当成业务返回类型**，于是 `interfaces.dto` 事实上变成跨层共享内核。后果比基础设施反向依赖更麻烦：**应用层不能脱离 Web 包被复用或测试**（改成定时任务或 MQ 触发就得依赖 `interfaces`），且 DTO 一旦改名会同时打断应用层。
   - 🔎 **本轮新查出⑤：`domain → infrastructure`，1 处**：`domain/platform/service/PlatformIntegrationServiceImpl.java:17` import `infrastructure.config.HttpClientConfig`，仅用于 `:44` 的 `@Qualifier(HttpClientConfig.PLATFORM_REQUEST_FACTORY)` 取一个 bean 名常量。为了一行字符串把领域层接到配置包（与①的 Spring 注解依赖叠加）。修法：常量下沉到 `domain` 侧，或直接按 `ClientHttpRequestFactory` 类型注入。
   - 📌 **两个方向同时存在 ⇒ 包环**：除上面③（`infrastructure → interfaces`，实测 ~~**6 处 / 3 个 mapper**~~ → 🔄 **2026-10-21：9 处 / 5 个文件** = 3 个 Mapper 6 处（`OrderMapper:18-19`、`ProductMapper:17-19`、`mapper/finance/FinancialTransactionMapper:10`）+ `config/SecurityConfig:23` 的 `Result` + `audit/AuditAspect:15-16` 的 `Result`/`UnfinishedRowReport`）外，反向的 `interfaces → infrastructure` 实测 ~~**11 处 / 10 个文件**~~ → 🔄 **2026-10-21：21 处 / 18 个文件**：4 个控制器直接注入 infrastructure 的 Mapper（`OrderController:22`、`ProductController:26`、`FinanceController:24`，新增 `OverviewController:15`）、6 个导入控制器使用 `infrastructure.importing.ExcelSourceParser`（`Customer/Finance/Warehouse/Product/Order/Logistics Import Controller`）、**9 个控制器**用 `TenantContext`（`RoleController:24`、`UserController:26`、`OrderController:23`、`PlatformIntegrationController:34`、`ShopController:25`、`PurchaseController:25`、`FinanceController:25`、`WarehouseController:25`、`OverviewController:16`）、`AuthController:24` 用 `JwtTokenProvider`、`SystemConfigController:16` 用 `AuditRequestContext`。⇒ `interfaces.controller ↔ infrastructure.persistence.jpa.mapper` 构成**双向依赖环**，这也是 ArchUnit 规则必须先于任何重构落地的原因（否则改一处就断另一处）。
2. 无模块边界 ⇒ 未来要把某个上下文拆成独立服务时，需要先补事件/接口契约，迁移成本被推迟而非消除。
3. `tenant` 上下文的领域服务实现在 `application/service`（`UserServiceImpl/RoleServiceImpl/TenantServiceImpl`），与其余 8 个上下文的组织方式不一致。<br>🔎 **2026-01-25 补充同类不一致**：`domain/finance/service/FinancialTransactionServiceImpl` 是**唯一把领域服务实现放在 domain 层**的（其余上下文的 `*ServiceImpl` 在 application 层），而且它里面还声明了 `DEFAULT_TENANT_ID`（`:26`）——**租户兜底逻辑下沉到了领域层**，见 [security-findings.md M-05](../security-findings.md)。

## 后续可选动作

- 轻量约束：引入 ArchUnit 测试固化依赖方向（禁止 `domain → org.springframework.*`、禁止 `infrastructure → interfaces`），成本低、收益直接。
- 严格约束：改为 Maven 多模块（`domain` 独立 artifact，不依赖 Spring）。仅当出现第二支团队并行开发或需要独立部署时再做。

> 🔎 **2026-01-25 复测**：① 本 ADR 的「领域层是纯 POJO」结论**依然成立**——`domain/` 下 `@Entity/@Table/@Column/@Enumerated` 实测 **0 处**；② **ArchUnit 至今未引入**（`pom.xml` 无 `archunit` 依赖、`src/test` 无 `ArchTest`/`GivenClasses` 引用，0 命中），所以上面 ~~3~~ **5** 类越界**没有任何自动化手段能防止继续增加**——本轮新查出的 `ProductMapper`、`FinancialTransactionMapper` 两处 `infrastructure → interfaces` 就是明证（原先只记录了 OrderMapper 一处），而 ④⑤ 两条（**69 处 application→interfaces**、**1 处 domain→infrastructure**）在此之前**完全没有被本文意识到**。
> 建议把 ArchUnit 作为**下一条 CI 规则**落地，因为它是纯测试依赖、不改运行时；起始规则建议只锁两条最容易验证的：禁止 `infrastructure.. → interfaces..`、禁止 `domain.. → org.springframework.orm/javax.persistence`（注意 `domain` 现在合法依赖 `spring-context` 的 `@Service`，规则不能一刀切禁 `org.springframework.*`）。相关缺口登记见 [ci-cd.md](../tech/ci-cd.md) 与 [testing.md](../testing.md)。

> 🔎 **2026-01-25 追加：规则清单要按 5 类越界逐条对应，而且不能一次全锁**
>
> | 规则 | 现有违规 | 落地方式 |
> |---|---|---|
> | 禁止 `infrastructure.. → interfaces..` | ~~6 处 / 3 个 mapper~~ → 🔄 **2026-10-21：9 处 / 5 个文件**（3 个 Mapper + `SecurityConfig` + `AuditAspect`） | **可直接锁**（先把 DTO 组装移回 controller/assembler；`Result`/`UnfinishedRowReport` 需先下沉到公共契约包） |
> | 禁止 `interfaces.. → infrastructure..`（除 `TenantContext`/`JwtTokenProvider` 这类安全设施） | ~~11 处 / 10 个文件~~ → 🔄 **2026-10-21：21 处 / 18 个文件** | 可锁，但需先给 6 个导入控制器包一层 application 侧门面，并把 `TenantContext` 读接口上移为应用层门面 |
> | 禁止 `domain.. → infrastructure..` | **1 处**（`PlatformIntegrationServiceImpl.java:17`） | 先移常量再锁，成本极低 |
> | 禁止 `application.. → interfaces..` | **69 处 / 9 个类** | 🔴 **不要直接锁**，否则测试立刻红。要么先做 DTO 下沉迁移，要么用 ArchUnit 的 `FreezingArchRule` 把现有 69 处冻结成基线，只拦新增 |
> | 允许 `domain.. → org.springframework.context`（`@Service`） | 20 个类 | 作为豁免写进规则，避免一刀切误报 |
>
> 一句话：**先冻结、再收敛**——用 `FreezingArchRule` 固化当前违规集作为基线，能保证"不再变差"，而不必在本轮承担 69 处迁移的成本。
