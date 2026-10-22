# 技术架构设计

> 全部结论核对自代码。**计量时点：2026-10-10 工作树 = 568 个主源码 Java 文件 / 102 个测试文件**（上一档 2026-03-08 = 463 / 31；2026-01-25 = 444 / 26；`bfbce09` 为 220 / 9）。单 Maven 模块。重算命令见 [tech/traceability.md §5](tech/traceability.md)。
> 本文描述**当前实际架构（As-Is）**，并在末尾列出与 DDD 理想分层的偏离项。
> 🔄 **2026-10-21 复测**：四层文件数（110 / 43 / 205 / 209）与总数 568 未变；变的是**上下文数量**（9 → 13 + `common`）、**持久化对象数量级**（`@Entity` 40 → 43、领域仓储接口 26 → 30）、**鉴权覆盖**（`@PreAuthorize` 29 → **130 处真实注解**；另有 6 处只是 Javadoc 文本，`grep` 原文命中 136）与**注解计数**（`@Transactional` 116 → 145、`readOnly = true` 9 → 20）。以下按节原地订正。

## 1. 总览

```mermaid
graph LR
    subgraph 客户端
        A[user-web<br/>:5120 React19+AntD6]
        B[admin-web<br/>:5110 React19+AntD6<br/>已接真实接口：6 页 / 全真实数据]
    end
    subgraph 服务端
        C[interfaces<br/>24 Controller · 214 端点<br/>Result 信封 / GlobalExceptionHandler]
        D[application<br/>43 文件（应用服务 + command/query）· 编排]
        E[domain<br/>13 限界上下文 + common<br/>聚合/值对象/领域服务/领域事件/仓储接口]
        F[infrastructure<br/>JPA · 安全 · 消息 · AOP · 配置]
    end
    G[(MySQL 8<br/>ecommerce_logistics_erp)]
    H[[RocketMQ 5.3.2<br/>namesrv + broker + proxy]]
    I[/外部电商平台<br/>Amazon / eBay / Shopify/]

    A -->|HTTP + JWT| C
    B -->|HTTP + JWT<br/>2026-01-25 已接入| C
    C --> D --> E
    D --> F
    F -->|Spring Data JPA / Hibernate| G
    F -->|RocketMQTemplate| H
    H -->|@RocketMQMessageListener| F
    E -.->|真实外呼已实现：8 家平台适配器 + Http*/Sample* 通道<br/>useSampleData=false 时走它| I
```

- 前端 `baseURL` 硬编码 `http://localhost:8082/api`（**2026-10-21 三测两处都还在**：`admin-web/src/services/api.js:6`、~~`user-web/src/services/api.js:6`~~ → 现 `user-web/src/services/api.js:48`，该文件在 2026-01-25 后重构过、行号已下移），**没有 Vite proxy**（~~两个 `vite.config.js` 都只有 `plugins:[react()]`~~ → 🔄 2026-10-10：两份 `vite.config.js:9` 现有 `server.port`（5120 / 5110），但**依然没有 proxy**）→ 跨域直连，CORS 是硬约束。~~后端 `SecurityConfig` 只放行 `http://localhost:5173`，admin-web 的 5174 不在白名单~~ → **M-09 已修**：~~`SecurityConfig.java:50` 现为 `List.of("http://localhost:5173", "http://localhost:5174")`~~ → 🔄 2026-10-21 复测 `SecurityConfig.java:63` 为 `List.of("http://localhost:5110", "http://localhost:5120")`，**来源与 §1 末条的端口订正已对齐**；⚠️ 但这四个来源仍是**硬编码在源码里的**，改端口就得改 Java（ISS-021 的配置外部化没覆盖到 CORS）。admin-web 已能跨域访问并带令牌（6 页 / 全真实数据 + Login + 路由守卫）。
- ~~端口 5173/5174 的分工来自 Vite 默认端口自动递增，并非配置声明。~~ → 🔄 **2026-10-10 订正**：端口现已写进配置——`user-web/vite.config.js:9` `port: 5120`、`admin-web/vite.config.js:9` `port: 5110`（卖家端与平台管理端各自固定，不再依赖占用递增），后端 CORS 白名单同步为这两个来源（`SecurityConfig:63`）。旧文所有 5173/5174 均按此替换。

## 2. 分层与依赖方向

| 层 | 主源码文件数 | 内容 |
|---|---|---|
| `interfaces` | 110 | 24 `@RestController`、`Result`（`interfaces/common`）、`GlobalExceptionHandler`（`interfaces/handler`）、`interfaces/dto/{order,product,finance}` |
| `application` | 43 | 应用服务 ~~（含 `BusinessProcessOrchestrationService`、`CustomUserDetailsService`）~~ → 🔄 2026-10-21：**20 个 `*ApplicationService` + `CustomUserDetailsService`，全部按上下文装进 14 个 `application/service/*` 子包（顶层 0 个类）**；`BusinessProcessOrchestrationService` **已从代码中删除**（全仓 0 处引用）；`application/config/ApplicationConfig`、`application/command/`、`application/query/` |
| `domain` | 205 | ~~9 个上下文（order/product/customer/logistics/warehouse/purchase/finance/tenant/platform…）~~ → 🔄 2026-10-21：**13 个上下文 + `common` 共享内核**，实测目录为 `order, logistics, warehouse, product, customer, purchase, finance, tenant, platform, shop, audit, config, overview` + `common`（新增的 `shop` 见 [ADR-0013](adr/0013-shop-entity-and-platform-connection-split.md)，`audit`/`config`/`overview` 由提交 `6927f01` 落地）；含领域服务 `@Service` **20 个文件** |
| `infrastructure` | 209 | `*JpaEntity`、`jpa/mapper` 桥接、`repository` 自定义实现、`security`、`messaging`、`config`、`aop`、`platform`（61 类第三方外呼适配器）、`importing` |
<!-- 2026-10-10 复测：按 find 实测 interfaces 110 / application 43 / domain 205 / infrastructure 209，控制器 24 个。四层合计 567 个主源码文件（另 1 个启动类）。 -->
<!-- 2026-10-21 复测：四层文件数与 568 总数未变；domain 顶层包由 10 个涨到 14 个（13 上下文 + common）。 -->

根包 `EcommerceLogisticsApplication` 对四个顶层包做 `@ComponentScan`；**分层只靠包约定，没有 Maven 多模块隔离**。

### 每个上下文的内部结构

`domain/{module}/{aggregate, entity?, event?, repository, service, valueobject}`
注意用的是 `aggregate`（不是 `model`）；~~`customer/finance/platform` 无 `entity` 目录，`tenant` 无 `event` 目录~~ → 🔄 2026-10-21 实测：`customer`/`finance` **已有 `entity` 目录**，仍无 `entity` 的是 `platform`、`shop`、`config`、`overview`；无 `event` 目录的是 `tenant`、`audit`、`config`、`overview`。`audit`/`config` 只有 `entity + repository`（`audit` 另有 `valueobject`），`overview` 只有一个 `repository` 加 `statistics/SystemOverviewStatistics`。九个上下文有 `service`（`order, logistics, warehouse, product, customer, purchase, finance, tenant, platform`），`shop/audit/config/overview` 没有领域服务。

| order | `Order` | `OrderItem`,`OrderFieldMapping`,`OrderImportError` | `OrderStatus`,`PaymentInfo`,`ShippingAddress` | `OrderProcessingService`（~~`OrderSyncService`~~ 该类已删除） | 4 | `OrderRepository` |
| logistics | `LogisticsOrder` | `LogisticsChannel`,`LogisticsFieldMapping`,`LogisticsImportError` | `LogisticsStatus`,`LogisticsFee`,`LogisticsLabel`,`PriceRule` | `LogisticsOrderProcessingService`,`LogisticsTrackingService`,`LogisticsChannelService` | 4 | `LogisticsOrderRepository`,`LogisticsChannelRepository` |
| warehouse | `Warehouse` | `InventoryRecord`,`Location`,`ShopWarehouseFieldMapping`,`ShopWarehouseImportError` | `InventoryStatus`,`InventoryMovement`(内嵌 `MovementType`) | `WarehouseManagementService`,`InventoryManagementService`,`InventoryCountService` | 3 | `WarehouseRepository`,`InventoryRecordRepository` |
| product | `Product` | `ProductCategory`,`SKU`,`ProductFieldMapping`,`ProductImportError` | `ProductPrice`,`ProductAttribute` | `ProductManagementService`,`SKUManagementService`,`ProductCategoryService`,`ProductFieldMappingService` | 3 | `ProductRepository` |
| tenant | `Tenant` | `User`,`Role` | `TenantStatus`,`UserStatus` | 接口 `UserService/RoleService/TenantService`（**实现落在 application 层**） | 无 | `TenantRepository`,`UserRepository`,`RoleRepository` |
| finance | `FinancialTransaction` | ~~—~~ `FinanceFieldMapping`,`FinanceImportError` | `TransactionStatus`,`TransactionType` | `FinancialTransactionService`+`Impl` | 2 | `FinancialTransactionRepository` |
| platform | `PlatformIntegration` | — | `PlatformName`,`PlatformStatus`,`PlatformAuthType`（~~协议：`CUSTOM_SHOP`/`SHOPIFY`~~ → 🔄 2026-10-21 **11 个协议键**：`CUSTOM_SHOP`,`SHOPIFY`,`MAGENTO`,`AMAZON_SP_API`,`EBAY_SELL`,`TEMU`,`TIKTOK_SHOP`,`ALIEXPRESS`,`SHOPEE`,`MERCADO_LIBRE`,`OZON`，其中 `TEMU`/`TIKTOK_SHOP` 只有枚举、无适配器） | `PlatformIntegrationService`+`Impl`；**出站 SPI 在 `domain/platform/client/`（23 个类型：6 个 `Platform*Fetcher` + `PlatformFulfillmentPusher` + `PlatformConnectionTester` + `PlatformCredential` 等）**，~~实现在 `infrastructure/platform/{,shopify}/`~~ → 实现在 `infrastructure/platform/`（61 类 / 8 个平台适配器目录 + `common`/`oauth`），由 `PlatformFetcherRegistry` 按协议键分派 | 2 | `PlatformIntegrationRepository` |
| purchase | `PurchaseOrder` | `PurchaseOrderItem` | `PurchaseStatus` | `PurchaseOrderService`+`Impl` | 2 | `PurchaseOrderRepository` |
| customer | `Customer` | ~~—~~ `CustomerFieldMapping`,`CustomerImportError` | `CustomerStatus` | `CustomerService`+`Impl` | 2 | `CustomerRepository` |
| **shop**（🔄 2026-10-21 新增，[ADR-0013](adr/0013-shop-entity-and-platform-connection-split.md)） | `Shop` | 无 `entity` 目录 | `ShopAuthStatus`,`PushBackPolicy` | **无领域服务** | 2 | `ShopRepository` |
| **audit**（🔄 新增） | 无聚合根 | `AuditLog` | `AuditResult` | 无（写入靠 `@Auditable` + AOP，常量在 `AuditActions`） | 无 | `AuditLogRepository` |
| **config**（🔄 新增） | 无聚合根 | `SystemSetting` | — | 无 | 无 | `SystemSettingRepository` |
| **overview**（🔄 新增） | 无聚合根 | —（`statistics/SystemOverviewStatistics` 是读模型，不在 `entity/`） | — | 无 | 无 | `OverviewStatisticsRepository` |
| common（共享内核） | `BaseAggregateRoot`,`BaseEntity`,`BaseDomainEvent`,`BaseTenantEntity` | | | `exception/`：`BusinessException`,`EntityNotFoundException`,`InvalidOperationException`,`ValidationException` | | |

> ⚠️ 上表只列**业务聚合**的仓储。每个导入产品线还各有 2 个非聚合仓储（`*ImportJobRepository` + `*FieldMappingRepository`，共 12 个）加 `OverviewStatisticsRepository` 等，全量实测见 §9：**30 个 `*Repository` 接口 ↔ 30 个 `*RepositoryImpl` ↔ 29 个 `*JpaRepository` ↔ 只有 12 个 `*Mapper`**。

聚合根与领域仓储接口的完整清单另见 [docs/api/api.md](./api/api.md) 与 [docs/db/data-dictionary.md](db/data-dictionary.md)。

### 持久化桥接（双仓储体系）

```
domain/{ctx}/repository/XRepository            (接口，纯 POJO)
        ↑ 实现
infrastructure/persistence/repository/XRepositoryImpl   ← @Repository 或 PersistenceConfig @Bean
        ↓ 委托
infrastructure/persistence/jpa/repository/XJpaRepository (extends JpaRepository)
        ↕ 转换
infrastructure/persistence/jpa/mapper/XMapper  (MapStruct, componentModel=spring)
        ↕
infrastructure/persistence/jpa/entity/XJpaEntity (@Entity)
```

~~15 个领域仓储接口 ↔ 15 个 `*RepositoryImpl` ↔ 15 个 `*JpaRepository` ↔ 12 个 `*Mapper`；24 个 `@Entity`~~ → 🔄 **2026-10-21 复测：30 个领域仓储接口 ↔ 30 个 `*RepositoryImpl` ↔ 29 个 `*JpaRepository` ↔ 仍是 12 个 `*Mapper`；`@Entity` 43 个**（子聚合/值对象表无独立 JPA 接口，随聚合根级联读写）。
⚠️ ~~**3 个仓储无独立 Mapper**，领域↔实体转换写在 Impl 内：`InventoryRecordRepositoryImpl`、`ProductImportJobRepositoryImpl`、`ProductFieldMappingRepositoryImpl`~~ → 🔄 **2026-10-21：无独立 Mapper 的仓储已增至 18 个**。12 个 MapStruct Mapper 只覆盖 `Customer`,`FinancialTransaction`,`LogisticsChannel`,`LogisticsOrder`,`Order`,`PlatformIntegration`,`Product`,`PurchaseOrder`,`Role`,`Tenant`,`User`,`Warehouse`；剩下 18 个仓储把领域↔实体转换内联在 Impl 里，其中**只有 `InventoryRecord`、`Shop`、`ShopWarehouse`、`AuditLog`、`SystemSetting`、`OverviewStatistics` 是业务聚合/记录**，其余 12 个是导入产品线的 `*ImportJob`/`*FieldMapping` 脚手架。**特别是新落地的 `Shop` 聚合（ADR-0013）没有沿用 Mapper 约定**，说明该约定已不是「少数新增遗漏」而是默认被绕过——规范漂移，见 [tech/backend-standards.md §7](tech/backend-standards.md)。

## 3. 领域事件与消息

### 富聚合（行为 + 事件注册在聚合内）
`Order` / `PurchaseOrder` / `Customer` / `FinancialTransaction` / `PlatformIntegration` / `Warehouse` / `LogisticsOrder` 在 `create()`、`updateStatus()`、`activate()` 等方法内调用 `registerDomainEvent(...)`；🔄 2026-10-21 新增 `shop/aggregate/Shop`，共 **8 个聚合发事件**。
⚠️ `BaseAggregateRoot` 使用不一致：~~5 个聚合继承它~~ → 🔄 **6 个继承**（`Tenant`,`PlatformIntegration`,`Shop`,`PurchaseOrder`,`FinancialTransaction`,`Customer`），`Order`/`LogisticsOrder`/`Product`/`Warehouse` **未继承**，各自重复实现 `domainEvents` + `registerDomainEvent`。
⚠️ 🔄 2026-10-21 两处新发现的口径破口：① `domain/product/aggregate/Product` **不调用 `registerDomainEvent`，而是直接 `this.domainEvents.add(...)`**（`:112,:130,:147,:177` 四处发 3 个 product 事件），绕过了基类那套「注册入口」；② 应用层直接改聚合的事件列表——`ProductApplicationService.java:104` 写 `product.getDomainEvents().add(ProductCreatedEvent...)`，等于把「聚合发什么事件」的决策权拿到了应用服务里。

### 双通道发布（`DomainEventPublisher`）
`publishEventsFrom(aggregate)`（`infrastructure/messaging/DomainEventPublisher.java:35-53`）读聚合的事件列表，对每个事件**同时**：
1. `applicationEventPublisher.publishEvent(event)` —— 进程内；
2. `rocketMQTemplate.convertAndSend(topic, event)` —— MQ。

~~反射读取聚合的 `domainEvents` 字段~~ → 🔄 2026-10-21：改为 `resolveDomainEvents(aggregate)`（`:37`）**优先走基类访问器** `BaseAggregateRoot#getDomainEvents()`，取不到才沿继承链反射，避免「反射取不到父类字段导致事件永远发不出去」；发布后 `events.clear()`（`:50`）。

topic 由事件类全名推断（无 tag、无 `selectorExpression`）：

| 领域包 | topic |
|---|---|
| `order.event` | `order-events` |
| `customer.event` | `customer-events` |
| `logistics.event` | `logistics-events` |
| `warehouse.event` | `inventory-events` |
| `finance.event` | `finance-events` |
| `purchase.event` | `purchase-events` |
| `product.event` | `product-events` |
| `platform.event` | `platform-events` |
| `shop.event` | `shop-events`（🔄 2026-10-21 新增，`:129-130`） |

> 🔄 2026-01-25 更正（ISS-039 已修）：本表原只列 5 个 topic，`product`/`customer`/`platform` 三个包在 `DomainEventPublisher.getTopicForEvent` 返回 `null`、MQ 侧 `log.warn` 后静默丢弃。现已补齐为 ~~**8 个 topic**~~ → 🔄 2026-10-21：**9 个 topic**（`DomainEventPublisher.java:110-131`），~~22 个领域事件~~ → **24 个领域事件**（9 个 `*/event/` 包：order 4、logistics 4、warehouse 3、product 3、customer 2、finance 2、purchase 2、platform 2、shop 2）按其包名全部有归属，不再有返回 null 的分支。
> ⚠️ 新增的 `audit`/`config`/`overview` 三个上下文**没有 `event` 包**，所以它们不产生领域事件——`getTopicForEvent` 的分支是按包名字符串匹配的，将来若给这三个上下文补事件，必须同步加分支，否则会退回 ISS-039 的静默丢弃。

调用点：~~`OrderApplicationService:42,58,68`、`ProductApplicationService:51`、`LogisticsApplicationService:55,69`、`CustomerApplicationService:44`、`FinanceApplicationService:60`、`PlatformIntegrationApplicationService:32`、`PurchaseApplicationService:45,83,96`~~ → 🔄 2026-10-21 复测：**48 处调用、分布在 12 个应用服务**（新增 `ShopApplicationService`、`OrderImportApplicationService`、`FinanceImportApplicationService`、`LogisticsImportApplicationService`、`WarehouseApplicationService`）。按行号最多的是 `PurchaseApplicationService`（`:96,157,177,192,209,225,239,253,269` 共 9 处）与 `LogisticsApplicationService`（`:140,153,214,236,257,273,290,414,442` 共 9 处）、`ProductApplicationService`（`:73,126,173,275,291,352` 共 6 处）、`CustomerApplicationService`（`:58,84,145,170,184,198,212` 共 7 处）。

### 消费者（4 个独立顶层类，`infrastructure/messaging/consumer/`）

| topic | consumerGroup | 处理器 | 动作 |
|---|---|---|---|
| `order-events` | `logistics-group` | `OrderCreatedEventConsumer` | 建物流单（tenantId 取 `event.getTenantId()`，为空才回退 `"default-tenant"`，`:39-41`） |
| `order-events` | `warehouse-group` | `OrderStatusChangedEventConsumer` | 仅打印（预留库存是注释占位，`:31`） |
| `logistics-events` | `finance-group` | `LogisticsCompletedEventConsumer` | 仅打印（记账规则未定义，`:30`） |
| `inventory-events` | `purchase-group` | `InventoryShortageEventConsumer` | ~~建采购单（tenantId **仍硬编码** `"default-tenant"`，`:37`）~~ → 🔄 2026-10-21 **已改**：委托 `ReplenishmentApplicationService.replenishForShortage(event)`（`:40`），与进程内 `EventHandler` 走**同一个补货方法**（`:38` 注释点名 ISS-143「两条通道各写一套语义会建出没有明细行的空单」），硬编码租户已消失 |

> 🔄 2026-01-25 更正（ISS-034 已修）：本节原文描述的是 `RocketMQEventConsumer` 内部 4 个**非 static 内部类**监听的旧结构，该结构已不存在——4 个监听器已拆为 `infrastructure/messaging/consumer/` 下的 4 个**顶层 `@Component` 类**，~~每个都带 `@ConditionalOnProperty(prefix="rocketmq", name="enabled", havingValue="true")`~~ → 🔄 2026-10-21：**门禁条件换了**，四个消费者现在统一挂 `@ConditionalOnProperty(prefix="rocketmq", name="internal-consumers.enabled", havingValue="true")`（`OrderCreatedEventConsumer:22`、`OrderStatusChangedEventConsumer:20`、`LogisticsCompletedEventConsumer:20`、`InventoryShortageEventConsumer:21`）。

⚠️ **四条问题的当前状态**（~~2026-01-25 复核~~ → 🔄 2026-10-21 重新逐条对照代码）：
1. **副作用重复** — ~~❌ **仍未修**~~ → ✅ **ISS-054 已用互斥开关闭合**：`EventHandler`（`infrastructure/messaging/EventHandler.java:23`，`@Component` + 4 个 `@EventListener`，`:45,:61,:76,:92`，**本身无 `@ConditionalOnProperty`，恒装配**）与 MQ 消费者做的仍是同一批动作，但 `application.yml:46` 新增 `rocketmq.internal-consumers.enabled: false`，注释写明「单体内跨聚合编排由进程内 EventHandler 唯一执行，打开会让同一事件被"进程内 + MQ"各跑一遍（重复物流单/重复采购单，ISS-054）」⇒ **默认配置下不再双写**。⚠️ 残余风险从「代码没去重」降级为「配置写错就双写」：这个互斥**只靠一个布尔量维持**，`enabled=true` 与 `internal-consumers.enabled=true` 同时打开即恢复重复副作用，且没有任何启动期校验或断言拦住这个组合。
2. ~~`spring.rocketmq.*` 是死配置~~ — ✅ **已修**：配置已挪到顶层 `rocketmq.`（~~`application.yml:39-45`~~ → 🔄 现 `:41-52`：`enabled: true`、`internal-consumers.enabled: false`、`name-server: localhost:9876`、`producer.group`、`send-message-timeout: 3000`、`retry-times-when-send-failed: 2`），由 starter 直接消费。
3. ~~`RocketMQTemplate` 被手工裸 new~~ — ✅ **已修**：`RocketMQConfig` 不再 `return new RocketMQTemplate()`。它改为 `@ConditionalOnProperty(prefix="rocketmq", name="enabled")` + `@Import(RocketMQAutoConfiguration.class)`——`rocketmq-spring-boot-starter 2.2.3` 只在 `META-INF/spring.factories` 注册自动配置，而 Spring Boot 3.x 已不读该文件（改读 `META-INF/spring/…AutoConfiguration.imports`），不显式 `@Import` 时模板与监听器容器一律不装配。模板/生产者改由 starter 依顶层 `rocketmq.*` 构建，nameServer 因此真正生效。
4. ~~消费者很可能未注册~~ — ✅ **结构问题已修**（见上方更正），~~但换来两个新的前置条件：① `rocketmq.enabled=true` 时没有 broker 就起不来~~ → 🔄 **前置条件①已被 `internal-consumers.enabled=false` 解掉**：`rocketmq.enabled` 现在只装配模板与生产者（发送侧），监听器容器不再启动，本机无需 broker 也能起；② ~~消费侧反序列化仍会失败——22 个事件类是「全 `final` 字段 + 私有 builder 构造 + 无 `@JsonCreator`/无空参构造」~~ → 🔄 **ISS-052 只对「有消费者的那批」修了一半**：24 个领域事件里**只有 4 个补了 `@JsonCreator`**——恰好就是 4 个消费者要收的那 4 个（`OrderCreatedEvent:33`、`OrderStatusChangedEvent`、`InventoryShortageEvent`、`LogisticsCompletedEvent`），且其嵌套值对象一并补上（`ShippingAddress`、`PaymentInfo`、`OrderItem:24`、`InventoryMovement`）。其余 **20 个事件仍是「私有 builder 构造、无 `@JsonCreator`」**（如 `ProductCreatedEvent`），Jackson 无法还原⇒ 只要将来给它们加消费者就会当场 `MessageConversionException`。⇒ 结论从「装配通了但消费不通」更新为：**MQ 通道默认关闭、开启后 4 类事件在结构上可反序列化，但端到端从未在 broker 上验证过**（ISS-052 保持未闭合，另见 [qa/known-issues.md](qa/known-issues.md)）。

详见 [ADR-0006](adr/0006-rocketmq-domain-events.md)。

### 3.1 事件发布失败的处理

`DomainEventPublisher.publishEventsFrom` 整体包在 `try { ... } catch (Exception e) { log.error("发布领域事件时出错", e); }`（`:35-53`）里。⇒ **事件发布失败不会让业务事务回滚，也不会向上游暴露**，调用方拿到的仍是成功响应；`publishToRocketMQ` 内部同样自行 catch（`:88-100`）。这是有意的「事件不影响主流程」取舍，但配合下面 §4 的 AFTER_ROLLBACK 语义，等于**没有任何机制保证「落库」与「发事件」的一致性**（ISS-053）。

## 4. 事务边界

> ✅ 2026-01-25 更正（ISS-036/042 已修）：本节原文「全仓 `@Transactional` 出现次数：0」**已过期**。实测 ~~`@Transactional` 共 **98** 处~~ → ~~**110 处**（application 90 / infrastructure **20** / domain 0 / interfaces 0）~~ → 🔄 2026-10-21 **145 处 / 30 文件**，另有 `@TransactionalEventListener` 1 处（仍只在 `DomainEventPublisher`）。

**当前分布（按层）**：~~`application/` **90** ｜ `infrastructure/` **8** ｜ `domain/` **0** ｜ `interfaces/` **0**~~ → 🔄 **2026-10-21 实测：`application/` 124 ｜ `infrastructure/` 21 ｜ `domain/` 0 ｜ `interfaces/` 0，共 145 处 / 30 个文件**。
即事务边界统一画在应用服务方法上（`application/` 一层就占 124 处），领域层保持无框架注解——与 [ADR-0012](adr/0012-ddd-single-module-layering.md) 的分层约定一致。

**仍存在的三点不足**（~~2026-01-25 复核~~ → 🔄 2026-10-21 复核）：
1. ~~**98 处全是裸 `@Transactional`**：`readOnly` 0 处、`rollbackFor` 0 处~~ → ~~**110 处，`readOnly` 已有 7 处、`rollbackFor` 仍 0 处**~~ → ~~🔄 **2026-03-22 复测：116 处 / 27 个文件，`readOnly = true` 9 处、`rollbackFor` 仍 0 处**（`readOnly` 新增的 2 处在 `PurchaseApplicationService:115,136`，即采购列表与详情）~~ → 🔄 **2026-10-21：145 处 / 30 个文件，`readOnly = true` 已升到 20 处，`rollbackFor` 仍是 0 处**。`readOnly` 的分布已经**从仓储层挪到了应用服务层为主**（15/20）：`SystemConfigApplicationService:50,69,77,82`、`AuditApplicationService:50,59,67,75`、`OverviewApplicationService:52,64`、`PurchaseApplicationService:108,129`（行号已因该类扩容而下移），仓储层剩 `UserRepositoryImpl:54,60,66,72,81`、`RoleRepositoryImpl:39,46,53`。**默认只对 `RuntimeException`/`Error` 回滚**，方法签名上声明 checked 异常的路径不会回滚；读方法仍只有 20/N 标了 `readOnly=true`，其余拿不到连接池/Hibernate 侧的只读优化。
   ⚠️ 教训仍在：**事务不能只补在应用服务层**。`UserRepositoryImpl`/`RoleRepositoryImpl` 的读方法原先无事务，映射时触碰 `@ElementCollection` 懒集合（`UserJpaEntity.roleIds`、`RoleJpaEntity.permissions`）——`spring.mvc.open-session-in-view` 只覆盖 Web 请求线程，`CommandLineRunner` 里调用就是 `LazyInitializationException`（**ISS-061**）。仓储层那 8 处 `readOnly = true` 正是为此而加。同因还有一个隐藏缺陷：`RoleMapper.toDomainEntity` 把 Hibernate 的 `PersistentSet` **引用**直接交给领域对象，等于让聚合持有托管集合——现改为拷贝 `new HashSet<>(...)`。
2. **批量/导入类操作刻意不加事务**：`batchShip`、各 `*ImportApplicationService` 采用**逐行独立提交**（单行失败不影响其余行，配合行级 `*_import_job_errors` 收集错误）。这是有意取舍而非遗漏，代价是整批不具备原子性。
3. `@TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)`（~~`DomainEventPublisher:135`~~ → 🔄 2026-10-21 现为 `:137`）现在**确会被触发**（存在事务后），但其处理器只 `log.info("事务回滚，清理事件: {}")`（`:139`），**没有真正清理聚合内已发出的事件**——事件在 `publishEventsFrom` 里已同步推给 Spring 与 MQ，回滚并不能撤回已投递的消息。配合 §3.1 的异常吞掉，「落库与发消息」仍无原子性保证，也**没有 outbox**。

连带后果（原文仍成立）：`Order`/`LogisticsOrder`/`PurchaseOrder` 的跨聚合一致性依赖事件驱动，而事件通道本身不可靠（见 §3 第 1、4 条）。聚合内 `calculateTotal()`、金额校验等不变式有效。

## 5. 横切能力

| 能力 | 实现 | 状态 |
|---|---|---|
| 认证 | `JwtAuthenticationFilter` + `JwtTokenProvider`（JJWT 0.12.5，HS512） | 签发真实、~~**校验被桩化**~~ → 🔄 **2026-03-22 复测：校验已真实**（`validateToken` = `parseClaims(token) != null`，验签 + 过期判断；~~过滤器构造注入 `:31`、`:43` 处把 roles/permissions 还原成 authorities~~ → 🔄 2026-10-21 行号下移：`:60` 判 `validateToken`、`:92-98` 还原 authorities、`:107-110` 在 `finally` 里 `clearTenantId()`）。旧表述来自 S-01/S-02 未修时的口径，已于 2026-01-25 修复（见 [security-findings.md S-01/S-02](security-findings.md)、[ADR-0004](adr/0004-jwt-authentication.md)） |
| 授权 | ~~`SecurityConfig.authorizeRequests`（已废弃 API）｜规则存在但**实际失效**，无方法级鉴权~~ → 🔄 **2026-10-21 复测：规模已完全不同**：已改 `authorizeHttpRequests`（~~`SecurityConfig:63`~~ → 现 `:77`，L-01 已修；`permitAll` 只剩 `:78` 的 `/api/auth/login\|refresh\|logout` 与 `:81` 的 swagger 资源），`@EnableMethodSecurity`（~~`:35`~~ → 现 `:48`）+ **`@PreAuthorize` 真实注解 130 处 / 22 个控制器**（`grep` 原文命中 136 处 / 26 个文件，差额 6 处是 Javadoc 文本），其中 **24 个控制器里 22 个已挂**（只有 `AuthController`、`TenantController` 没有——后者靠 `SecurityConfig` 规则与切面，见 [ISS-061]）。~~29 处 / 覆盖 10/20 个控制器（`Purchase` 12 处方法级、`Finance` 9 处、8 个类级）~~ 是 2026-03-22 的旧水位。**逐控制器实测**：`PlatformIntegration` 14、`Warehouse` 13、`Logistics` 13、`WarehouseImport` 10、`Product` 12、`Purchase` 12、`Order` 9、`Finance` 9、`Customer` 9、`LogisticsChannel` 7、`Shop` 7、`ShopWarehouse` 5、`Role` 3、6 个 `*ImportController` 各 1、`AuditLog`/`SystemConfig`/`Overview`/`User` 各 1。<br>⚠️ 残留两点：① 注解全部是 `hasRole('PLATFORM_ADMIN') or hasAuthority(...)` 的**或**关系，平台管理员恒绕过（设计上如此，但意味着租户级隔离不约束该角色）；② ~~`readOnly` 型权限点中 10 个"种了没人查"（M-06）~~ → 🔄 **M-06 的这一段已过期**：权限点现在有唯一字典 `domain/tenant/PermissionPoints.java`（25 个租户级 + 1 个平台级 = 26），实测 **26 个点全部被 `hasAuthority('…')` 引用、无孤儿**，且 `user-web/src/pages/role/RoleList.jsx` 的 `PERMISSION_PRESETS` 与之**逐名对齐（前端 26 = 后端 26，双向差集为空）**；旧的 `*:readOnly` 命名已不在 catalog 里。守卫测试见 ISS-090（`TenantParameterGuardTest`）。 | 🟡 已接线；Auth/Tenant 靠 URL 规则 |
| 租户上下文 | `TenantContext`（ThreadLocal）+ `TenantAccessGuard` + `TenantIsolationAspect` | ~~AOP 逻辑**全部注释掉**，且从不 `clearTenantId`~~ → 🔄 **2026-03-22 复测：切面已生效且已清理**；🔄 **2026-10-21 再复测：判据已扩到三条**——`TenantIsolationAspect:76` 的 `@Before("serviceMethods()")` 先 `TenantContext.normalizeRequested(...)`（`:101`，把历史默认桶字面量归一化成「未指定」再判，避免误判 400），非平台管理员越权抛 `TENANT_ACCESS_DENIED`（`:128`）；**平台管理员跨租户时新增存在性校验** `TenantAccessGuard.requireExistingTenant(...)`（`:119`），且该校验**只对写请求生效**（`isReadRequest()` 按当前 HTTP 动词取 GET/HEAD/OPTIONS，`:150`、`:86`）——ISS-135 的收口：凭空点名一个不存在的租户 UUID 会让数据落进谁也查不出来的幻影桶，而查询只会返回空页。<br>⚠️ 真实覆盖面仍只有一条规则（"形参名为 `tenantId` 的 `String`"）：无上下文时 `:78-81` 直接放行，`repositoryMethods()` 切点（`:156-162`）**只打 trace 不过滤**，读路径仍靠服务层参数自觉。 |
| API 文档 | springdoc-openapi 2.6.0 | 端点已标注 `@Tag`/`@Operation`；`SecurityScheme` 未定义 |
| 启动初始化 | `DataInitializationConfig`（CommandLineRunner） | 无租户时建默认租户 + 管理员 `admin/123456`（日志明文打印密码）；对已有库还会幂等地建系统角色 `PLATFORM_ADMIN`（2026-10-07 实测挂 **26** 个权限点，ADR-0013 的 `shop:*` 四点计入）并分给 `admin`。⚠️ 后两步此前**每次都静默失败**（无会话线程触碰懒集合 → `LazyInitializationException` 被 `catch` 吞掉），导致 `admin` 实际没有平台管理员角色、`hasRole(PLATFORM_ADMIN)` 的接口全 403 —— **ISS-061**，2026-01-25 已修并实测（启动日志出现「已为用户 admin 分配角色 PLATFORM_ADMIN」，`GET /api/tenants` 由 403 → 200）。🔄 2026-10-07：`ensurePlatformAdminRole()` 的补齐条件由「数量不足」（`currentSize < expected.size()`，catalog 等量改名时永不触发）改为 `!permissions.containsAll(expected)`；同轮 `shop:credential:write` 这类三段点名会让种子整批回滚且只留一条 ERROR 日志，见 **ISS-090** |
| 异常处理 | `GlobalExceptionHandler` | 存在，但领域层不抛业务异常 → 大量落到 500 |
| 参数校验 | — | **未启用**（无 `@Valid`、无 Bean Validation 注解） |
| 缓存 / 限流 / 熔断 / 链路追踪 / 指标 | — | **均无** |
| 定时任务 / 异步 | — | **无** `@EnableScheduling`/`@Async` |
| 外部平台 SDK | ~~—｜**无**（无 RestTemplate/WebClient/Feign/SDK 依赖）~~ → 🔄 **2026-03-22 复测：结论反了**——依赖树确实没有第三方 HTTP/SDK 客户端（`pom.xml` 无 openfeign/webflux/okhttp/httpclient），但**外呼层是真实存在的**：`infrastructure/config/HttpClientConfig.java:23-25` 用 JDK `java.net.http.HttpClient` + `JdkClientHttpRequestFactory` 装配 `platformRequestFactory`，超时由~~`application.yml:53-55`~~ → 🔄 2026-10-21 现 `application.yml:60-61`（connect 5s / read 15s）注入；`infrastructure/platform/` ~~实测 **60 个类 / 11,123 行**~~ → 🔄 **61 个类 / 11,308 行**（0 处 `TODO`/`UnsupportedOperationException`），含 8 个平台适配器目录（amazon、ebay、shopify、shopee、ozon、magento、mercado、aliexpress）+ `common`/`oauth` + ~~7 个通用 `HttpPlatform*Fetcher`~~ → 🔄 **6 个 `HttpPlatform*Fetcher`（order/product/warehouse/customer/logistics/finance）+ `HttpPlatformConnectionTester`** + `PlatformFetcherRegistry`，13 个注入点（`PlatformHttpExchange:46-47`、`ShopifyAdminApi:62`、`PlatformIntegrationServiceImpl:47` 等）。**共 24 个 `implements Platform*Fetcher` 实现、10 个 `*ConnectionTester`**（8 家协议 + `Http`/`Sample` 两条通用通道）。⚠️ 三点保留：**① 响应体字段名无官方出处**（按候选键探测，见 ISS-064/065）；② ~~5 个 `SamplePlatform*Fetcher`~~ → 🔄 现 **6 个**，都是**示例/降级实现**（`type()="SAMPLE"`，造 `平台-ORD-001` 假数据），联调未做时导入链路仍能跑通——真实凭据下的端到端从未验证；③ 🔄 **六条数据线覆盖严重不齐**（按 `implements Platform*Fetcher` 实测）：**订单线 8 家协议**（shopify / magento / amazon / ebay / shopee / ozon / mercado / aliexpress）、**商品线只有 4 家**（shopify / magento / shopee / ozon，amazon / ebay / mercado / aliexpress 没有 ProductFetcher）、**客户 / 物流 / 店铺仓库 / 财务四条线只有 `CUSTOM_SHOP` + `SAMPLE` 两个注册键、协议适配器为 0**——`PlatformFetcherRegistry.resolve(...)` 对后四条线的任何协议平台都会抛 `PLATFORM_ADAPTER_NOT_FOUND`（报错文案自带「已注册: [...]」清单），这就是前端「同步客户 / 物流 / 库存」三个按钮对八家协议平台一律失败的根因。发货回写（`PlatformFulfillmentPusher`）另有一档：只有 shopify / magento / ozon / ebay 四家。另 `TEMU`/`TIKTOK_SHOP` 只在枚举里，一家适配器都没有。 | 🟡 外呼已真实，六条数据线覆盖不齐 |
| CI | — | **无**（无 `.github/`、`Jenkinsfile`、`.gitlab-ci.yml`、pre-commit） |
| 后端代码规范工具 | — | **无**（无 checkstyle/spotless/.editorconfig/mvnw）；前端有 `eslint.config.js`（flat config） |

## 6. 目录结构（实测）

```
ecommerce-logistics-erp/
├── backend/                         # 单 Maven 模块：site.hexaarch.ecommerce:ecommerce-logistics-erp:1.0.0-SNAPSHOT
│   ├── pom.xml
│   └── src/
│       ├── main/java/site/hexaarch/ecommerce/logistics/
│       │   ├── EcommerceLogisticsApplication.java
│       │   ├── interfaces/      {controller(24 类 / 13 个子包：order,product,finance,customer,logistics,platform,purchase,warehouse,shop,tenant,audit,config,overview), common(Result), handler, dto/{15 个子包：order,product,finance,customer,logistics,platform,purchase,shop,overview + 6 个 *import}}
│       │   ├── application/     {service(28 个类 / 14 个子包，含新增 audit,config,overview,shop,support，~~16 个顶层类~~ 已全部下沉为子包、顶层 0 个类), config, command, query}
│       │   ├── domain/          {order,logistics,warehouse,product,tenant,finance,platform,purchase,customer,common} → 🔄 2026-10-21 补 **shop,audit,config,overview** 四个，共 14 个顶层包
│       │   └── infrastructure/  {persistence/{jpa/{entity,repository,mapper},repository},
│       │                         platform(61 类 / 11,308 行，第三方外呼适配器), importing,
│       │                         security, messaging/{,config,consumer}, aop, config}
│       ├── main/resources/application.yml      # 仅此一个配置文件（无 profile）
│       └── test/java/...                       # 🔄 2026-10-21 **102 个测试类 / 801 个 `@Test`**（~~54 类 / 494 例~~）；`src/test/resources` **仍不存在**，协议 fixture 内联在 22 个用 `com.sun.net.httpserver.HttpServer` 的测试类里；全仓 0 个 `@SpringBootTest`
├── frontend/
│   ├── user-web/                    # :5120，46 个页面 / 43 条路由，全部真实接口（含 order-create/product/customer/finance/logistics/platform/purchase），api.js 真实 HTTP + Bearer
│   └── admin-web/                   # :5110，6 个页面全部真实接口（Login + user/tenant/platform×3，含审计台账），api.js 真实 HTTP + localStorage token 注入 Authorization
├── scripts/
│   ├── docker/docker-compose.yml    # 仅 RocketMQ namesrv/broker/proxy（无 MySQL、无应用服务）
│   └── smoke/                       # 🔄 9 份真机脚本：smoke.sh + security-slice-smoke.sh + 6 份常驻探针 + 2 个静态门（audit-coverage-scan.js、sum-surefire.js）
└── docs/                            # 本目录
```

> 🔄 **2026-10-21 对上图的订正**：`interfaces/controller` 20 → **24 类 / 13 子包**（新增 `shop`、`audit`、`config`、`overview` 四个上下文口）；`interfaces/dto` 13 → **15 子包**（新增 `shop`、`overview`）；`application/service` 由「16 个顶层类 + 4 子包」重构为 **0 顶层类 + 14 子包 / 28 个类**（新增 `audit`,`config`,`overview`,`shop`,`support`；`support/` 装 `TenantAccessGuard` 这类跨域护栏）；`infrastructure/platform` 60 类 / 11,123 行 → **61 类 / 11,308 行**；`messaging/` 明确分出 `consumer/` 子包。⚠️ **`application/service/tenant/BusinessProcessOrchestrationService` 已从代码中消失**（全仓 0 处引用），§2 表格与 [ADR-0012](adr/0012-ddd-single-module-layering.md) 里点名它的表述随之失效。测试侧口径不变：`src/test/resources` **到 2026-10-21 仍不存在**，八家平台的响应体 fixture 是以字符串常量内联在测试类里的（22 个测试类用 `com.sun.net.httpserver.HttpServer` 起本地桩），这正是 ISS-064「全部结论只有本地 fixture 证据」的技术形态。

🔄 **2026-03-22 对上图的订正**（代码树快照，原地更新，旧数字列在此以便追溯）：`controller` 12 → **20**；`application/service` 11 → **16 个顶层类 + 4 个子包**；`interfaces/dto` 3 个子包 → **13 个**（新增 customer/platform/purchase 与 6 个 `*import`）；`infrastructure` 原图漏了 **`platform`（60 类 / 11,123 行）** 与 **`importing`** 两个包；测试 **31 类 / 211 例 → 54 类 / 494 例**（`src/test/resources` 仍然不存在）；user-web **34 → 45 个页面**，admin-web **4 → 5 个页面**且"整个 api.js 为 mock、无认证"**已不成立**（现 `baseURL: http://localhost:8082/api` + 请求拦截器注入 `Bearer ${localStorage.token}`，见 S-03）。⚠️ 顺带订正端口口径：`frontend/*/vite.config.js` **都没有写 `server.port`**（两份文件各只有 `plugins: [react()]`），":5173 / :5174" 是 Vite 默认值 + 第二个实例自动 +1 的**运行结果**，不是配置约定。

## 7. 与 DDD 理想分层的偏离（技术债清单）

按影响排序，修复前请读 [ADR-0012](adr/0012-ddd-single-module-layering.md)。

| # | 偏离 | 证据 |
|---|---|---|
| 1 | ~~无事务边界~~ | ✅ 2026-01-25 已修：`@Transactional` ~~**98 处**（application 90 / infrastructure 8 / domain 0 / interfaces 0）~~ → ~~**110 处**~~ → ~~🔄 **2026-03-22 复测 116 处 / 27 个文件**（application 96 / infrastructure **20** / domain 0 / interfaces 0，注释内 0 处）~~ → 🔄 **2026-10-21 复测 145 处 / 30 个文件**（application **124** / infrastructure **21** / domain 0 / interfaces 0），但 ~~**全部裸注解**——`readOnly` 0、`rollbackFor` 0~~ → ~~**`readOnly` 7 处（只在两个仓储实现，为 ISS-061 补）**~~ → ~~🔄 **`readOnly = true` 9 处**（`UserRepositoryImpl:52,58,64,70`、`RoleRepositoryImpl:39,46,53`、`PurchaseApplicationService:115,136`）~~ → 🔄 **`readOnly = true` 20 处**，主体已从仓储层移到应用服务层（`SystemConfigApplicationService:50,69,77,82`、`AuditApplicationService:50,59,67,75`、`OverviewApplicationService:52,64`、`PurchaseApplicationService:108,129`；仓储层 `UserRepositoryImpl:54,60,66,72,81`、`RoleRepositoryImpl:39,46,53`）、**`rollbackFor` 仍 0**，且批量/导入路径有意逐行提交（见 §4） |
| 2 | **domain 层泄漏 Spring 注解** | ~~16 个领域服务标 `@Service`~~ → **2026-01-25 实测 20 个**（`grep -rl "@Service" domain/`）→ 🔄 2026-10-21 **仍是 20 个**（新增的 4 个上下文都没有领域服务，见 §2 表）；`PlatformIntegrationServiceImpl` 用 `@Autowired`（`:45`，唯一一处）。（JPA 注解仍完全干净，`domain/` 内 0 处；`@Transactional` 在 domain 也是 **0 处**） |
| 3 | **infrastructure 反向依赖 interfaces** | ~~`jpa/mapper/OrderMapper.java:16-17` import `interfaces/dto/OrderDto,OrderItemDto`~~ → 🔄 2026-10-21 路径已分包：`jpa/mapper/order/OrderMapper.java:18-19` import `interfaces/dto/order/{OrderDto,OrderItemDto}`；`OrderController.java:22,47,49` 直接注入 infrastructure 的 `OrderMapper`。**Mapper 已按上下文分子包（9 个：customer,finance,logistics,order,platform,product,purchase,tenant,warehouse），但这条反向依赖没有随分包而消失** |
| 4 | **应用层跨上下文直连他域仓储** | `application/service/logistics/LogisticsApplicationService.java:24` import、`:64` 注入 `domain/order/repository/OrderRepository`——🔄 2026-10-21 行号下移（原 `:24,43`），该越层依赖**依然存在** |
| 5 | ~~双重持久化~~ 🟡 **重复 save 已消除，但保存位置仍越层** | 2026-01-25 复核：`OrderApplicationService.createOrder` 不再二次 `save`；但 save 落在**领域服务** `domain/order/service/OrderProcessingService.java:55,72,94,116,128`（~~`:55,72,94,116,138,150` 6 处~~ → 🔄 2026-10-21 复测 **5 处**），与 [tech/backend-standards.md §5](tech/backend-standards.md) 第 3 条「保存只出现在应用服务一处」仍不一致 |
| 6 | **仓储注册方式不一致，存在双注册** | 4 个 Impl 只靠 `@Repository` 扫描（`Customer/FinancialTransaction/PurchaseOrder/PlatformIntegration`）；`Role/User/Tenant/Product` 既有 `@Repository` 又在 `PersistenceConfig` 里 `@Bean`；新增的 `ProductImportJob`/`ProductFieldMapping` 沿用 `@Repository`（未进 `PersistenceConfig`）——两种风格并存且新增沿其中一种，说明约定未被明文写下，见 [tech/backend-standards.md §7](tech/backend-standards.md)。<br>🔄 2026-01-25 口径：`PersistenceConfig` 现有 **9 个 `@Bean`**，26 个 `*RepositoryImpl` 里 **21 个**同时标 `@Repository`。<br>🔄 **2026-10-21 复测**：`PersistenceConfig` **10 个 `@Bean`**（新增加入的是 `OverviewStatisticsRepository`——**新增的 `ShopRepository`/`AuditLogRepository`/`SystemSettingRepository` 三个都只走 `@Repository` 扫描**，没有进 `PersistenceConfig`，"新增沿用其中一种风格"这条判断继续成立）；`*RepositoryImpl` 26 → **30 个**，其中 **24 个**同时标 `@Repository`（双注册面仍在扩大）。 |
| 7 | **tenant 上下文结构不一致** | 领域服务接口在 `domain`，实现（`UserServiceImpl/RoleServiceImpl/TenantServiceImpl`）落在 `application/service/tenant`（🔄 2026-10-21 已进 `tenant` 子包，不再平铺在 `application/service` 顶层） |
| 8 | **CQRS 骨架未接线** | ~~`application/command/{Command,CreateOrderCommand,UpdateOrderStatusCommand,OrderCommandHandler}` 与 `application/query/*` 除自身文件外**无任何调用方**，无 dispatcher~~ → 🔄 2026-10-21：骨架**已按上下文重新分包**（`command/{Command.java, order, product, shop}`、`query/{Query.java, config, order}`），`OrderCommandHandler` 这类 handler 与旧的两个具体命令类已不在顶层；结论未变——`CreateOrderCommand`/`UpdateOrderStatusCommand`/`OrderCommandHandler` 全仓 0 处外部引用，**仍然没有 dispatcher**，只是从"未接线"变成"接了子包的空架子"。 |
| 9 | ~~多处桩实现~~ ✅ **绝大部分已消除（2026-01-25 实测）** | 原列的桩逐条复测：<br>① 全仓 `System.out.println` **0 处**（原文写 `PlatformIntegrationServiceImpl` 三个 sync 方法只打印）；该类现在做真实工作，失败抛 `BusinessException`（~~`PLATFORM_INTEGRATION_NOT_FOUND`/`_INACTIVE`/`PLATFORM_STORE_URL_MISSING`/`PLATFORM_INVENTORY_PUSH_FAILED`，`:158,162,166,198`~~ → 🔄 2026-10-21 实测三处：`PLATFORM_INTEGRATION_NOT_FOUND`（`:184`）、`PLATFORM_STORE_URL_MISSING`（`:197`）、`PLATFORM_INVENTORY_PUSH_FAILED`（`:229`）；**`PLATFORM_INACTIVE` 这个码在该类已不存在**）。<br>② `executeInventoryCount` 不再返回 `List.of()`，改为逐条走 `inventoryCountService.countInventory`；`moveInventory` 的 `TRANSFER` 分支不再 `UnsupportedOperationException`，改调 `transferInventory(...)`，未支持类型抛 `ValidationException`（🔄 2026-10-21 该类已扩容到 400+ 行，原 `:140-150`/`:227-228`/`:230` 行号失效；事件发布点在 `:423`）。<br>⚠️ 但"分支接上了"不等于"数量算对了"：同日逐条实测发现入库/调拨/出库/盘点仍 500→400（**ISS-059/ISS-060**）——`InventoryManagementService`/`InventoryCountService` 用**不受仓库限定**的 `findByProductId` 兜底定位库存行（调拨时命中源仓记录），且聚合与仓储把同一行映射成两个实例（聚合内的增减看不见）。现四处统一改为**在仓库聚合内定位**（`inventory_records.warehouse_id` NOT NULL，聚合即该仓全部行），找不到才新建并 `addInventoryRecord` 挂回聚合；实测入库/调拨/出库/盘点/调整五种路径全 200，库中 A=45、B=10 与响应一致。<br>③ ~~`OrderSyncService`、`LogisticsTrackingService` 同为桩~~：`OrderSyncService` **已不存在**；`domain/logistics/service/LogisticsTrackingService` 仍在。<br>④ 真实平台外呼已具备：`infrastructure/platform/` 有 6 个 `HttpPlatform*Fetcher`（走 `HttpClientConfig.PLATFORM_REQUEST_FACTORY`，带 `platform.http.connect-timeout: 5s`/`read-timeout: 15s`），与 6 个 `SamplePlatform*Fetcher` 并存，由请求参数 `useSampleData` 选择（~~`OrderImportApplicationService.java:87-88,432`~~ → 🔄 2026-10-21 该参数在 `:212` 接收、`:220` 塞进 request）——**注意这是客户端可控的开关**，传 `true` 就导入示例数据；分派逻辑见 `PlatformFetcherRegistry.registryKey(...)`，`useSampleData` 的优先级**高于协议本身**（勾选后无论什么平台都走 mock）。<br>⑤ 仍未收口：`WarehouseApplicationService.java:123`（~~`:93`~~）用 `Location.builder()` 在应用层直接构造实体（单处 save）。 |
| 10 | **贫血风险** | `Warehouse` 上下文编排逻辑大量停留在应用服务而非聚合内（🔄 2026-10-21 量化：`LogisticsApplicationService` **675 行**、`WarehouseApplicationService` **426 行**、`OrderApplicationService` 247 行；§3 统计的 48 处 `publishEventsFrom` 里 **25 处压在 `Purchase`(9)/`Logistics`(9)/`Product`(6)/`Warehouse`(1) 四个应用服务上**，而 `WarehouseApplicationService` 426 行只发 1 个事件——编排与状态推进的密度还在往应用层堆，聚合内不变式没有同步变厚） |

## 8. 构建与依赖

`java.version=17`，parent `spring-boot-starter-parent:3.5.9`；属性 `lombok 1.18.34`、`mapstruct 1.5.5.Final`、`spring.kafka.version 3.4.2`（**死属性，无 Kafka 依赖**）。

依赖（`backend/pom.xml`）：`spring-boot-starter-web`、`-data-jpa`、`-security`、`-aop`、`jjwt-api/impl/jackson 0.12.5`、`spring-boot-devtools`(optional)、`rocketmq-spring-boot-starter 2.2.3`、`mysql-connector-j`、`lombok`(provided)、`spring-boot-starter-test`、`springdoc-openapi-starter-webmvc-ui 2.6.0`、`junit-jupiter-api/engine`、`mockito-core`（后三者已由 starter-test 传递引入，属冗余声明）、`mapstruct` + `mapstruct-processor`(provided，同时又配在 `annotationProcessorPaths`，重复)。
插件：`spring-boot-maven-plugin`、`maven-compiler-plugin 3.11.0`（source/target 17 + lombok/mapstruct 处理器）、`maven-surefire-plugin 3.2.5`。

## 9. 命名约定（可观测）

| 模式 | 含义 | 数量 |
|---|---|---|
| `*JpaEntity` | JPA 持久化实体 | ~~**40** 个 `@Entity`/`@Table`（旧记 24，2026-01-25 重测）~~ → 🔄 2026-10-21 **43 个 `@Entity`**（`audit_logs`/`shops`/`system_configs` 三张新表计入；见 [db/schema.sql](db/schema.sql) 的落后说明） |
| `*JpaRepository` | Spring Data 接口 | ~~**26**~~ → 🔄 **29** |
| `*Repository` / `*RepositoryImpl` | 领域仓储接口 / 适配器实现 | ~~**26 / 26**~~ → 🔄 **30 / 30**（差额来自每个导入产品线各带的 `*ImportJobRepository` + `*FieldMappingRepository`） |
| `*Mapper` | MapStruct 领域↔JPA 转换 | **12**（其余上下文的转换内联在 RepositoryImpl 里；🔄 2026-10-21 已按上下文分成 9 个子包，**未覆盖的仓储从 3 个涨到 18 个**，见 §2 末） |
| `*ApplicationService` | 应用服务 | ~~**15**~~ → 🔄 **20** |
| `*Service` / `*ServiceImpl` | 领域服务（接口/实现） | domain 下 `*Service` **23** 个文件（未变），~~`*ServiceImpl` 7 个~~ → 🔄 **4 个**（`Customer`,`FinancialTransaction`,`PlatformIntegration`,`PurchaseOrder`；`UserServiceImpl`/`RoleServiceImpl`/`TenantServiceImpl` 三个已改名或并入 `application/service/tenant/`，见 §7 第 7 条） |
| `*Event` | 领域事件 | ~~**22**（旧记 23 属多计 1 个的计量错误，见 traceability.md §5 漂移警告）~~ → 🔄 2026-10-21 **24**（`shop/event/` 补了 `ShopCreatedEvent` + `ShopAuthStatusChangedEvent`；`BaseDomainEvent` 不算） |
| `*Controller` | REST 控制器 | ~~**20**（旧记 13）；`@Get/Post/Put/Patch/DeleteMapping` 合计 ~~**198** 处（2026-01-25 为 196…）~~ → 🔄 **2026-03-22 复测 202 处**~~ → 🔄 **2026-10-21 复测 24 个控制器 / 214 处映射**（与 [api/api.md](api/api.md) 的「214 端点」逐字对齐；`@RequestMapping` 前缀实测 **24 个**，全部 `/api/…`，其中 6 个是 `*ImportController`（order/product/customer/finance/logistics/warehouse）用 `/api/x/import` 子路径而非版本号） |
| `*Dto` | 出参 | ~~`OrderDto`,`OrderItemDto`,`CreateOrderDto`,`ProductDto`,`FinancialTransactionDto`~~ → 🔄 2026-10-21 平台域已 DTO 化并铺开到 **15 个 `interfaces/dto` 子包**；⚠️ **未收口**：`WarehouseController.java:116` 等仍把领域聚合直接序列化（见 [ADR-0011](adr/0011-result-envelope-and-errors.md) 与 README 的 P1 条目） |
| `*Request` / `*Command` / `*Query` | 控制器内**静态内部类**请求体 / CQRS 骨架 | — |
| 聚合根 | 无后缀（`Order`,`Customer`,`Warehouse`…） | ~~9~~ → 🔄 **17 个 `domain/*/aggregate/` 文件**（含 `*ImportJob` 与新增 `Shop`,`ShopWarehouse`；真正继承 `BaseAggregateRoot` 的只有 6 个，见 §3） |

作者标签统一 `@author kenyon`。前端：`*Api` 导出对象（`api.js`）、`pages/<module>/{List,Detail,Create}*` 三件套。

## 10. 非功能性现状

| 维度 | 现状 |
|---|---|
| 性能 | ~~无分页~~ → 🔄 2026-03-22：**12 个控制器已返回 `PageResult`** → 🔄 **2026-10-21：14 个控制器返回 `PageResult`**（order/product/customer/finance/logistics/purchase 六条业务线 + 对应 6 个 `*ImportController` + 新增的 `WarehouseController` 与 `AuditLogController`）；~~**仍有 7 个整体返回不定长数组**（`Warehouse`、`ShopWarehouse`、`PlatformIntegration`、`LogisticsChannel`、`Tenant`、`User`、`Role`，见 M-07）~~ → 🔄 现为 **10 个控制器完全不含 `PageResult`**（`LogisticsChannel`、`PlatformIntegration`、`Shop`、`ShopWarehouse`、`Tenant`、`User`、`Role`、`SystemConfig` + 天生不分页的 `Auth`/`Overview`）；⚠️ **`WarehouseController` 是混着来的**：同一个类里既有分页列表、又有 `:116` 那样直接吐聚合的不定长端点。M-07 的口径要按「端点」而不是「控制器」重述。~~无二级索引~~ → 🔄 **二级索引继续加**：21 个 `*JpaEntity` 声明了 **34 个 `@Index`**（2026-03-22 档是 19 个实体 / 30 个索引），`docs/db/schema.sql` 另有 **27 条 `KEY idx_*` + 22 条 `UNIQUE KEY`**，快照里 42 条 `CREATE TABLE`；⚠️ **快照与实体已脱节 3 张表**（`audit_logs`/`shops`/`system_configs`），见 [db/schema.sql](db/schema.sql)；无缓存、无读写分离 → 未分页的那批列表接口随数据量线性恶化 |
| 可用性 | 单实例；无健康检查端点使用（actuator **仍未引入**，2026-10-21 复测 `pom.xml` 0 处）；无重试/熔断——⚠️ 但平台**外呼侧**有自己的退避重试（`PlatformHttpExchange` + `PlatformFailures.exhausted(...)`），与全局限流/熔断不是一回事，见 §5 |
| 安全 | 见 [security-findings.md](security-findings.md)：~~鉴权架空、密钥明文、敏感字段外泄~~ → 🔄 2026-03-22 复测：S-01/S-02/S-03/S-05/S-06 已修（JWT 校验真实、admin-web 已接认证、平台凭据信封加密 + 出参掩码）；🔄 **2026-10-21 再订正**：~~**仍未修的是 S-04**（`application.yml:93` JWT 密钥明文写死）~~ → S-04 已改为**可被环境变量覆盖**（`application.yml:119` = `${JWT_SECRET:…默认值…}`，同形还有 `:28` 的 `${ERP_DB_PASSWORD:123456}` 与 `PLATFORM_CREDENTIAL_MASTER_KEY`），但**默认值仍在受版本控制的文件里**，ISS-021 的实质风险没消，只是从「硬编码」降级为「默认值兜底」；~~**10 个无方法级鉴权的控制器（M-06）~~ → 🔄 只剩 **2 个**（`AuthController` 是 permitAll 的登录口、`TenantController` 靠 `SecurityConfig` 规则 + 切面），`@PreAuthorize` 已从 29 处涨到 **136 处**；权限点也已有唯一字典 `domain/tenant/PermissionPoints.java`（26 点全部被引用，前后端逐名对齐）——M-06 里「种了没人查」那半条已不成立 |
| 可观测 | `logging.level` 仅 INFO 四组，无 `logback-spring.xml`、无 traceId、无指标 |
| 可维护 | 无 CI、无后端 lint、无 schema 迁移工具、~~事务缺失~~ → 事务已有但无 `rollbackFor`（见 §7 第 1 条） |
| 兼容 | ~~Spring Security 使用已废弃的 `authorizeRequests`~~ → 🔄 2026-03-22：已迁 `authorizeHttpRequests`（~~`SecurityConfig:63`~~ → 现 `:77`，L-01 已修）；**无 API 版本化仍成立**（🔄 2026-10-21：**24 个控制器**的 `@RequestMapping` 全部是 `/api/<复数资源名>`，无 `/api/v1`；6 个导入控制器用 `/api/x/import` 子路径而非版本号） |
