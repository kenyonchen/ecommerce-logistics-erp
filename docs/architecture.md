# 技术架构设计

> 全部结论核对自代码。**计量时点：2026-03-08 工作树 = 463 个主源码 Java 文件 / 31 个测试文件**（上一档 2026-01-25 = 444 / 26；`bfbce09` 为 220 / 9，2026-01-25 曾记为 266 / 10）。单 Maven 模块。重算命令见 [tech/traceability.md §5](tech/traceability.md)。
> 本文描述**当前实际架构（As-Is）**，并在末尾列出与 DDD 理想分层的偏离项。

## 1. 总览

```mermaid
graph LR
    subgraph 客户端
        A[user-web<br/>:5173 React19+AntD6]
        B[admin-web<br/>:5174 React19+AntD6<br/>已接真实接口：5 页 / 33 方法]
    end
    subgraph 服务端
        C[interfaces<br/>20 Controller · 198 端点<br/>Result 信封 / GlobalExceptionHandler]
        D[application<br/>21 个应用服务文件 · 编排]
        E[domain<br/>9 限界上下文 + common<br/>聚合/值对象/领域服务/领域事件/仓储接口]
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
    E -.->|真实外呼已实现：6 个 Http*Fetcher<br/>useSampleData=false 时走它| I
```

- 前端 `baseURL` 硬编码 `http://localhost:8082/api`（**2026-01-25 两处都还在**：`user-web/src/services/api.js:6`、`admin-web/src/services/api.js:6`），**没有 Vite proxy**（两个 `vite.config.js` 都只有 `plugins:[react()]`）→ 跨域直连，CORS 是硬约束。~~后端 `SecurityConfig` 只放行 `http://localhost:5173`，admin-web 的 5174 不在白名单~~ → **M-09 已修**：`SecurityConfig.java:50` 现为 `List.of("http://localhost:5173", "http://localhost:5174")`，admin-web 已能跨域访问并带令牌（5 页 / 33 个 api 方法 + Login + 路由守卫）。
- 端口 5173/5174 的分工来自 Vite 默认端口自动递增，并非配置声明。

## 2. 分层与依赖方向

| 层 | 主源码文件数 | 内容 |
|---|---|---|
| `interfaces` | 90 | 20 `@RestController`、`Result`（`interfaces/common`）、`GlobalExceptionHandler`（`interfaces/handler`）、`interfaces/dto/{order,product,finance}` |
| `application` | 33 | 21 个应用服务文件（含 `BusinessProcessOrchestrationService`、`CustomUserDetailsService`）、`application/config/ApplicationConfig`、`application/command/`、`application/query/` |
| `domain` | 177 | 9 个上下文（order/product/customer/logistics/warehouse/purchase/finance/tenant/platform…）+ `common` 共享内核（含 `PageResult`），**其中 20 个领域服务标了 `@Service`** |
| `infrastructure` | 143 | 45 个 `*JpaEntity`（40 个有 `@Table`）、`jpa/mapper` 桥接、`repository` 自定义实现、`security`、`messaging`、`config`、`aop` |
<!-- 2026-01-25 复测：原表 4 行数字均为 2026-01-25 口径——interfaces 34（13 控制器）/ application 26（13 应用服务）/ domain 117 / infrastructure 88。现按 find 实测为 90 / 33 / 177 / 143，控制器 20 个、application/service 下 21 个文件、domain 内 @Service 20 处、45 个 *JpaEntity（40 个带 @Table）。四仓合计 443 个主源码文件。 -->

根包 `EcommerceLogisticsApplication` 对四个顶层包做 `@ComponentScan`；**分层只靠包约定，没有 Maven 多模块隔离**。

### 每个上下文的内部结构

`domain/{module}/{aggregate, entity?, event?, repository, service, valueobject}`
注意用的是 `aggregate`（不是 `model`）；`customer/finance/platform` 无 `entity` 目录，`tenant` 无 `event` 目录。

| 上下文 | 聚合根 | 实体 | 值对象 | 领域服务 | 领域事件 | 仓储接口 |
|---|---|---|---|---|---|---|
| order | `Order` | `OrderItem` | `OrderStatus`,`PaymentInfo`,`ShippingAddress` | `OrderProcessingService`（~~`OrderSyncService`~~ 该类已删除） | 4 | `OrderRepository` |
| logistics | `LogisticsOrder` | `LogisticsChannel` | `LogisticsStatus`,`LogisticsFee`,`LogisticsLabel`,`PriceRule` | `LogisticsOrderProcessingService`,`LogisticsTrackingService`,`LogisticsChannelService` | 4 | `LogisticsOrderRepository`,`LogisticsChannelRepository` |
| warehouse | `Warehouse` | `InventoryRecord`,`Location` | `InventoryStatus`,`InventoryMovement`(内嵌 `MovementType`) | `WarehouseManagementService`,`InventoryManagementService`,`InventoryCountService` | 3 | `WarehouseRepository`,`InventoryRecordRepository` |
| product | `Product` | `ProductCategory`,`SKU` | `ProductPrice`,`ProductAttribute` | `ProductManagementService`,`SKUManagementService`,`ProductCategoryService` | 3 | `ProductRepository` |
| tenant | `Tenant` | `User`,`Role` | `TenantStatus`,`UserStatus` | 接口 `UserService/RoleService/TenantService`（**实现落在 application 层**） | 无 | `TenantRepository`,`UserRepository`,`RoleRepository` |
| finance | `FinancialTransaction` | — | `TransactionStatus`,`TransactionType` | `FinancialTransactionService`+`Impl` | 2 | `FinancialTransactionRepository` |
| platform | `PlatformIntegration` | — | `PlatformName`,`PlatformStatus`,`PlatformAuthType`（协议：`CUSTOM_SHOP`/`SHOPIFY`，决定用哪套适配器） | `PlatformIntegrationService`+`Impl`；**出站 SPI 在 `domain/platform/client/`（23 个类型：6 个 `Platform*Fetcher` + `PlatformFulfillmentPusher` + `PlatformConnectionTester` + `PlatformCredential` 等）**，实现在 `infrastructure/platform/{,shopify}/`，由 `PlatformFetcherRegistry` 按协议键分派 | 2 | `PlatformIntegrationRepository` |
| purchase | `PurchaseOrder` | `PurchaseOrderItem` | `PurchaseStatus` | `PurchaseOrderService`+`Impl` | 2 | `PurchaseOrderRepository` |
| customer | `Customer` | — | `CustomerStatus` | `CustomerService`+`Impl` | 2 | `CustomerRepository` |
| common（共享内核） | `BaseAggregateRoot`,`BaseEntity`,`BaseDomainEvent`,`BaseTenantEntity` | | | `exception/`：`BusinessException`,`EntityNotFoundException`,`InvalidOperationException`,`ValidationException` | | |

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

15 个领域仓储接口 ↔ 15 个 `*RepositoryImpl` ↔ 15 个 `*JpaRepository` ↔ 12 个 `*Mapper`；24 个 `@Entity`（子聚合/值对象表无独立 JPA 接口，随聚合根级联读写）。
⚠️ **3 个仓储无独立 Mapper**，领域↔实体转换写在 Impl 内：`InventoryRecordRepositoryImpl`、`ProductImportJobRepositoryImpl`、`ProductFieldMappingRepositoryImpl`（其余 12 个各有 1 个 MapStruct Mapper）。新增仓储没有沿用 Mapper 约定，是规范漂移，见 [tech/backend-standards.md §7](tech/backend-standards.md)。

## 3. 领域事件与消息

### 富聚合（行为 + 事件注册在聚合内）
`Order` / `PurchaseOrder` / `Customer` / `FinancialTransaction` / `PlatformIntegration` / `Warehouse` / `LogisticsOrder` 在 `create()`、`updateStatus()`、`activate()` 等方法内调用 `registerDomainEvent(...)`。
⚠️ `BaseAggregateRoot` 使用不一致：5 个聚合继承它，`Order`/`LogisticsOrder`/`Product`/`Warehouse` **未继承**，各自重复实现 `domainEvents` + `registerDomainEvent`。

### 双通道发布（`DomainEventPublisher`）
`publishEventsFrom(aggregate)` 反射读取聚合的 `domainEvents` 字段，对每个事件**同时**：
1. `applicationEventPublisher.publishEvent(event)` —— 进程内；
2. `rocketMQTemplate.convertAndSend(topic, event)` —— MQ。

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

> 🔄 2026-01-25 更正（ISS-039 已修）：本表原只列 5 个 topic，`product`/`customer`/`platform` 三个包在 `DomainEventPublisher.getTopicForEvent` 返回 `null`、MQ 侧 `log.warn` 后静默丢弃。现已补齐为 **8 个 topic**（`infrastructure/messaging/DomainEventPublisher.java:110-128`），22 个领域事件按其包名全部有归属，不再有返回 null 的分支。

调用点：`OrderApplicationService:42,58,68`、`ProductApplicationService:51`、`LogisticsApplicationService:55,69`、`CustomerApplicationService:44`、`FinanceApplicationService:60`、`PlatformIntegrationApplicationService:32`、`PurchaseApplicationService:45,83,96`。

### 消费者（4 个独立顶层类，`infrastructure/messaging/consumer/`）

| topic | consumerGroup | 处理器 | 动作 |
|---|---|---|---|
| `order-events` | `logistics-group` | `OrderCreatedEventConsumer` | 建物流单（tenantId 取 `event.getTenantId()`，为空才回退 `"default-tenant"`） |
| `order-events` | `warehouse-group` | `OrderStatusChangedEventConsumer` | 仅打印（预留库存是注释占位） |
| `logistics-events` | `finance-group` | `LogisticsCompletedEventConsumer` | 仅打印（生成财务记录是注释占位） |
| `inventory-events` | `purchase-group` | `InventoryShortageEventConsumer` | 建采购单（tenantId **仍硬编码** `"default-tenant"`，`:37`） |

> 🔄 2026-01-25 更正（ISS-034 已修）：本节原文描述的是 `RocketMQEventConsumer` 内部 4 个**非 static 内部类**监听的旧结构，该结构已不存在——4 个监听器已拆为 `infrastructure/messaging/consumer/` 下的 4 个**顶层 `@Component` 类**，每个都带 `@ConditionalOnProperty(prefix="rocketmq", name="enabled", havingValue="true")` 与 `@RocketMQMessageListener(topic=..., consumerGroup=...)`。

⚠️ **四条问题的当前状态**（2026-01-25 复核，逐条对照代码）：
1. **副作用重复** — ❌ **仍未修**。`EventHandler`（`infrastructure/messaging/EventHandler.java:23`，`@Component` + 4 个 `@EventListener`，`:45,:61,:76,:92`）与 MQ 消费者做的是同一批动作；双通道发布下 `OrderCreatedEvent` 仍会被进程内 handler 与 `OrderCreatedEventConsumer` **各建一次物流单**。消费者里已加注释标注这是同源双写（`OrderCreatedEventConsumer:35`），但**代码层面没有任何去重或互斥开关**——`rocketmq.enabled=true` 且 handler 同时在线即重复。
2. ~~`spring.rocketmq.*` 是死配置~~ — ✅ **已修**：配置已挪到顶层 `rocketmq.`（`application.yml:39-45`：`enabled: true`、`name-server: localhost:9876`、`producer.group`、`send-message-timeout: 3000`、`retry-times-when-send-failed: 2`），由 starter 直接消费。
3. ~~`RocketMQTemplate` 被手工裸 new~~ — ✅ **已修**：`RocketMQConfig` 不再 `return new RocketMQTemplate()`。它改为 `@ConditionalOnProperty(prefix="rocketmq", name="enabled")` + `@Import(RocketMQAutoConfiguration.class)`——`rocketmq-spring-boot-starter 2.2.3` 只在 `META-INF/spring.factories` 注册自动配置，而 Spring Boot 3.x 已不读该文件（改读 `META-INF/spring/…AutoConfiguration.imports`），不显式 `@Import` 时模板与监听器容器一律不装配。模板/生产者改由 starter 依顶层 `rocketmq.*` 构建，nameServer 因此真正生效。
4. ~~消费者很可能未注册~~ — ✅ **结构问题已修**（见上方更正），但**换来两个新的前置条件**：① `rocketmq.enabled=true` 时**没有 broker 就起不来**（监听器容器启动即连 nameServer），本机纯代码跑动需要显式置 `false`；② 消费侧**反序列化仍会失败**——22 个事件类是「全 `final` 字段 + 私有 builder 构造 + 无 `@JsonCreator`/无空参构造」（如 `OrderCreatedEvent:27` 的 `private OrderCreatedEvent(Builder)`），还嵌套 `ShippingAddress`/`PaymentInfo` 等值对象，Jackson 无法从 MQ 报文还原，`RocketMQListener<OrderCreatedEvent>` 的泛型绑定在此处会抛 `MessageConversionException`。⇒ **MQ 通道「装配通了但消费不通」，端到端仍未验证通过**（新立 ISS-052）。

详见 [ADR-0006](adr/0006-rocketmq-domain-events.md)。

### 3.1 事件发布失败的处理

`DomainEventPublisher.publishEventsFrom` 整体包在 `try { ... } catch (Exception e) { log.error("发布领域事件时出错", e); }`（`:35-53`）里。⇒ **事件发布失败不会让业务事务回滚，也不会向上游暴露**，调用方拿到的仍是成功响应；`publishToRocketMQ` 内部同样自行 catch（`:88-100`）。这是有意的「事件不影响主流程」取舍，但配合下面 §4 的 AFTER_ROLLBACK 语义，等于**没有任何机制保证「落库」与「发事件」的一致性**（ISS-053）。

## 4. 事务边界

> ✅ 2026-01-25 更正（ISS-036/042 已修）：本节原文「全仓 `@Transactional` 出现次数：0」**已过期**。实测 ~~`@Transactional` 共 **98** 处~~ → **110 处**（application 90 / infrastructure **20** / domain 0 / interfaces 0），另有 `@TransactionalEventListener` 1 处。

**当前分布（按层）**：`application/` **90** ｜ `infrastructure/` **8** ｜ `domain/` **0** ｜ `interfaces/` **0**。
即事务边界统一画在应用服务方法上（如 `PurchaseApplicationService:43,90,104,118,129,140,151`），领域层保持无框架注解——与 [ADR-0012](adr/0012-ddd-single-module-layering.md) 的分层约定一致。

**仍存在的三点不足**（2026-01-25 复核）：
1. ~~**98 处全是裸 `@Transactional`**：`readOnly` 0 处、`rollbackFor` 0 处~~ → ~~**110 处，`readOnly` 已有 7 处、`rollbackFor` 仍 0 处**~~ → 🔄 **2026-03-22 复测：116 处 / 27 个文件，`readOnly = true` 9 处、`rollbackFor` 仍 0 处**（`readOnly` 新增的 2 处在 `PurchaseApplicationService:115,136`，即采购列表与详情）。默认只对 `RuntimeException`/`Error` 回滚，方法签名上声明 checked 异常的路径不会回滚；读方法绝大多数仍未标 `readOnly=true`，拿不到连接池/Hibernate 侧的只读优化。
   ⚠️ 本轮（2026-01-25 后段）新增一条同类教训：**事务不能只补在应用服务层**。`UserRepositoryImpl`/`RoleRepositoryImpl` 的读方法原先无事务，映射时触碰 `@ElementCollection` 懒集合（`UserJpaEntity.roleIds`、`RoleJpaEntity.permissions`）——`spring.mvc.open-session-in-view` 只覆盖 Web 请求线程，`CommandLineRunner` 里调用就是 `LazyInitializationException`（**ISS-061**）。7 处 `readOnly = true` 正是为此而加（`UserRepositoryImpl.java:52,58,64,70`、`RoleRepositoryImpl.java:39,46,53`）。同因还有一个隐藏缺陷：`RoleMapper.toDomainEntity` 把 Hibernate 的 `PersistentSet` **引用**直接交给领域对象，等于让聚合持有托管集合——现改为拷贝 `new HashSet<>(...)`。
2. **批量/导入类操作刻意不加事务**：`batchShip`、各 `*ImportApplicationService` 采用**逐行独立提交**（单行失败不影响其余行，配合行级 `*_import_job_errors` 收集错误）。这是有意取舍而非遗漏，代价是整批不具备原子性。
3. `@TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)`（`DomainEventPublisher:135`）现在**确会被触发**（存在事务后），但其处理器只 `log.info`，**没有真正清理聚合内已发出的事件**——事件在 `publishEventsFrom` 里已同步推给 Spring 与 MQ，回滚并不能撤回已投递的消息。配合 §3.1 的异常吞掉，「落库与发消息」仍无原子性保证，也**没有 outbox**。

连带后果（原文仍成立）：`Order`/`LogisticsOrder`/`PurchaseOrder` 的跨聚合一致性依赖事件驱动，而事件通道本身不可靠（见 §3 第 1、4 条）。聚合内 `calculateTotal()`、金额校验等不变式有效。

## 5. 横切能力

| 能力 | 实现 | 状态 |
|---|---|---|
| 认证 | `JwtAuthenticationFilter` + `JwtTokenProvider`（JJWT 0.12.5，HS512） | 签发真实、~~**校验被桩化**~~ → 🔄 **2026-03-22 复测：校验已真实**（`validateToken` = `parseClaims(token) != null`，验签 + 过期判断；过滤器构造注入 `:31`、`:43` 处把 roles/permissions 还原成 authorities）。旧表述来自 S-01/S-02 未修时的口径，已于 2026-01-25 修复（见 [security-findings.md S-01/S-02](security-findings.md)、[ADR-0004](adr/0004-jwt-authentication.md)） |
| 授权 | ~~`SecurityConfig.authorizeRequests`（已废弃 API）｜规则存在但**实际失效**，无方法级鉴权~~ → 🔄 **2026-03-22 复测：两处都不成立**：已改 `authorizeHttpRequests`（`SecurityConfig:63` 起，L-01 已修），且 `@EnableMethodSecurity`（`:35`）+ **29 处 `@PreAuthorize` 覆盖 10/20 个控制器**（`Purchase` 12 处方法级、`Finance` 9 处、8 个类级）。残留：**10 个控制器无方法级鉴权**、注解全部是 `hasRole('PLATFORM_ADMIN') or hasAuthority(...)` 的**或**关系（平台管理员恒绕过）、`readOnly` 型权限点中 10 个"种了没人查"（见 [security-findings.md M-06](security-findings.md)） |
| 租户上下文 | `TenantContext`（ThreadLocal）+ `TenantIsolationAspect` | ~~AOP 逻辑**全部注释掉**，且从不 `clearTenantId`~~ → 🔄 **2026-03-22 复测：切面已生效且已清理**——`TenantIsolationAspect:55` 的 `@Before("serviceMethods()")` 比对入参 `tenantId` 与登录租户、不一致抛 `TENANT_ACCESS_DENIED`（`:83`），`PLATFORM_ADMIN` 放行（`:105`）；`JwtAuthenticationFilter:82` 在 `finally` 里 `clearTenantId()`。⚠️ 真实覆盖面仍只有一条规则（"形参名为 `tenantId` 的 String"）：无上下文时 `:57-60` 直接放行，`repositoryMethods()` 切点（`:91`）只打 trace，**读路径 9 个仓储未按租户过滤**（ISS-050 / M-03） |
| API 文档 | springdoc-openapi 2.6.0 | 端点已标注 `@Tag`/`@Operation`；`SecurityScheme` 未定义 |
| 启动初始化 | `DataInitializationConfig`（CommandLineRunner） | 无租户时建默认租户 + 管理员 `admin/123456`（日志明文打印密码）；对已有库还会幂等地建系统角色 `PLATFORM_ADMIN`（2026-10-07 实测挂 **26** 个权限点，ADR-0013 的 `shop:*` 四点计入）并分给 `admin`。⚠️ 后两步此前**每次都静默失败**（无会话线程触碰懒集合 → `LazyInitializationException` 被 `catch` 吞掉），导致 `admin` 实际没有平台管理员角色、`hasRole(PLATFORM_ADMIN)` 的接口全 403 —— **ISS-061**，2026-01-25 已修并实测（启动日志出现「已为用户 admin 分配角色 PLATFORM_ADMIN」，`GET /api/tenants` 由 403 → 200）。🔄 2026-10-07：`ensurePlatformAdminRole()` 的补齐条件由「数量不足」（`currentSize < expected.size()`，catalog 等量改名时永不触发）改为 `!permissions.containsAll(expected)`；同轮 `shop:credential:write` 这类三段点名会让种子整批回滚且只留一条 ERROR 日志，见 **ISS-090** |
| 异常处理 | `GlobalExceptionHandler` | 存在，但领域层不抛业务异常 → 大量落到 500 |
| 参数校验 | — | **未启用**（无 `@Valid`、无 Bean Validation 注解） |
| 缓存 / 限流 / 熔断 / 链路追踪 / 指标 | — | **均无** |
| 定时任务 / 异步 | — | **无** `@EnableScheduling`/`@Async` |
| 外部平台 SDK | ~~—｜**无**（无 RestTemplate/WebClient/Feign/SDK 依赖）~~ → 🔄 **2026-03-22 复测：结论反了**——依赖树确实没有第三方 HTTP/SDK 客户端（`pom.xml` 无 openfeign/webflux/okhttp/httpclient），但**外呼层是真实存在的**：`infrastructure/config/HttpClientConfig.java:25-33` 用 JDK `java.net.http.HttpClient` + `JdkClientHttpRequestFactory` 装配 `platformRequestFactory`，超时由 `application.yml:53-55`（connect 5s / read 15s）注入；`infrastructure/platform/` 实测 **60 个类 / 11,123 行**（0 处 `TODO`/`UnsupportedOperationException`），含 8 个平台适配器目录（amazon、ebay、shopify、shopee、ozon、magento、mercado、aliexpress）+ `common`/`oauth` + 7 个通用 `HttpPlatform*Fetcher` + `PlatformFetcherRegistry`，13 个注入点（`PlatformHttpExchange:46-47`、`ShopifyAdminApi:62`、`PlatformIntegrationServiceImpl:47` 等）。⚠️ 两点保留：**响应体字段名无官方出处**（按候选键探测，见 ISS-064/065），且 5 个 `SamplePlatform*Fetcher` 是**示例/降级实现**（`type()="SAMPLE"`，造 `平台-ORD-001` 假数据），联调未做时导入链路仍能跑通——真实凭据下的端到端从未验证 |
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
│       │   ├── interfaces/      {controller(20), common(Result), handler, dto/{13 个子包：order,product,finance,customer,logistics,platform,purchase + 6 个 *import}}
│       │   ├── application/     {service(16 个顶层类 + customer/finance/platform/purchase 4 个子包), config, command, query}
│       │   ├── domain/          {order,logistics,warehouse,product,tenant,finance,platform,purchase,customer,common}
│       │   └── infrastructure/  {persistence/{jpa/{entity,repository,mapper},repository},
│       │                         platform(60 类，第三方外呼适配器), importing,
│       │                         security, messaging/{,config}, aop, config}
│       ├── main/resources/application.yml      # 仅此一个配置文件
│       └── test/java/...                       # 54 个测试类 / 494 个 @Test（仍无 src/test/resources）
├── frontend/
│   ├── user-web/                    # :5173，45 个页面（含 order-create/product/customer/finance/logistics/platform/purchase），api.js 真实 HTTP + Bearer
│   └── admin-web/                   # :5174，5 个页面（Login + user/tenant/platform×2），api.js 已改真实 HTTP + localStorage token 注入 Authorization
├── scripts/docker/docker-compose.yml  # 仅 RocketMQ namesrv/broker/proxy（无 MySQL、无应用服务）
└── docs/                            # 本目录
```

🔄 **2026-03-22 对上图的订正**（代码树快照，原地更新，旧数字列在此以便追溯）：`controller` 12 → **20**；`application/service` 11 → **16 个顶层类 + 4 个子包**；`interfaces/dto` 3 个子包 → **13 个**（新增 customer/platform/purchase 与 6 个 `*import`）；`infrastructure` 原图漏了 **`platform`（60 类 / 11,123 行）** 与 **`importing`** 两个包；测试 **31 类 / 211 例 → 54 类 / 494 例**（`src/test/resources` 仍然不存在）；user-web **34 → 45 个页面**，admin-web **4 → 5 个页面**且"整个 api.js 为 mock、无认证"**已不成立**（现 `baseURL: http://localhost:8082/api` + 请求拦截器注入 `Bearer ${localStorage.token}`，见 S-03）。⚠️ 顺带订正端口口径：`frontend/*/vite.config.js` **都没有写 `server.port`**（两份文件各只有 `plugins: [react()]`），":5173 / :5174" 是 Vite 默认值 + 第二个实例自动 +1 的**运行结果**，不是配置约定。

## 7. 与 DDD 理想分层的偏离（技术债清单）

按影响排序，修复前请读 [ADR-0012](adr/0012-ddd-single-module-layering.md)。

| # | 偏离 | 证据 |
|---|---|---|
| 1 | ~~无事务边界~~ | ✅ 2026-01-25 已修：`@Transactional` ~~**98 处**（application 90 / infrastructure 8 / domain 0 / interfaces 0）~~ → ~~**110 处**~~ → 🔄 **2026-03-22 复测 116 处 / 27 个文件**（application 96 / infrastructure **20** / domain 0 / interfaces 0，注释内 0 处），但 ~~**全部裸注解**——`readOnly` 0、`rollbackFor` 0~~ → ~~**`readOnly` 7 处（只在两个仓储实现，为 ISS-061 补）、`rollbackFor` 仍 0**~~ → 🔄 **`readOnly = true` 9 处**（`UserRepositoryImpl:52,58,64,70`、`RoleRepositoryImpl:39,46,53`、`PurchaseApplicationService:115,136`）、**`rollbackFor` 仍 0**，且批量/导入路径有意逐行提交（见 §4） |
| 2 | **domain 层泄漏 Spring 注解** | ~~16 个领域服务标 `@Service`~~ → **2026-01-25 实测 20 个**（`grep -rl "@Service" domain/`）；`PlatformIntegrationServiceImpl` 用 `@Autowired`。（JPA 注解仍完全干净，`domain/` 内 0 处；`@Transactional` 在 domain 也是 **0 处**） |
| 3 | **infrastructure 反向依赖 interfaces** | `jpa/mapper/OrderMapper.java:16-17` import `interfaces/dto/OrderDto,OrderItemDto`；`OrderController` 直接注入 infrastructure 的 `OrderMapper` |
| 4 | **应用层跨上下文直连他域仓储** | `LogisticsApplicationService.java:24,43` 持有 `domain/order/repository/OrderRepository` |
| 5 | ~~双重持久化~~ 🟡 **重复 save 已消除，但保存位置仍越层** | 2026-01-25 复核：`OrderApplicationService.createOrder`（`:55-63`）不再二次 `save`；但 save 落在**领域服务** `domain/order/service/OrderProcessingService.java:55,72,94,116,138,150`（6 处），与 [tech/backend-standards.md §5](tech/backend-standards.md) 第 3 条「保存只出现在应用服务一处」仍不一致 |
| 6 | **仓储注册方式不一致，存在双注册** | 4 个 Impl 只靠 `@Repository` 扫描（`Customer/FinancialTransaction/PurchaseOrder/PlatformIntegration`）；`Role/User/Tenant/Product` 既有 `@Repository` 又在 `PersistenceConfig` 里 `@Bean`；新增的 `ProductImportJob`/`ProductFieldMapping` 沿用 `@Repository`（未进 `PersistenceConfig`）——两种风格并存且新增沿其中一种，说明约定未被明文写下，见 [tech/backend-standards.md §7](tech/backend-standards.md)。<br>🔄 2026-01-25 口径：`PersistenceConfig` 现有 **9 个 `@Bean`**，26 个 `*RepositoryImpl` 里 **21 个**同时标 `@Repository`（双注册面比原文描述的大） |
| 7 | **tenant 上下文结构不一致** | 领域服务接口在 `domain`，实现（`UserServiceImpl/RoleServiceImpl/TenantServiceImpl`）落在 `application/service` |
| 8 | **CQRS 骨架未接线** | `application/command/{Command,CreateOrderCommand,UpdateOrderStatusCommand,OrderCommandHandler}` 与 `application/query/*` 除自身文件外**无任何调用方**，无 dispatcher |
| 9 | ~~多处桩实现~~ ✅ **绝大部分已消除（2026-01-25 实测）** | 原列的桩逐条复测：<br>① 全仓 `System.out.println` **0 处**（原文写 `PlatformIntegrationServiceImpl` 三个 sync 方法只打印）；该类现在做真实工作，失败抛 `BusinessException`（`PLATFORM_INTEGRATION_NOT_FOUND`/`_INACTIVE`/`PLATFORM_STORE_URL_MISSING`/`PLATFORM_INVENTORY_PUSH_FAILED`，`:158,162,166,198`）。<br>② `executeInventoryCount` 不再返回 `List.of()`，改为逐条走 `inventoryCountService.countInventory`（`WarehouseApplicationService.java:140-150`）；`moveInventory` 的 `TRANSFER` 分支不再 `UnsupportedOperationException`，改调 `transferInventory(...)`（`:227-228`），未支持类型抛 `ValidationException`（`:230`）。<br>⚠️ 但"分支接上了"不等于"数量算对了"：同日逐条实测发现入库/调拨/出库/盘点仍 500→400（**ISS-059/ISS-060**）——`InventoryManagementService`/`InventoryCountService` 用**不受仓库限定**的 `findByProductId` 兜底定位库存行（调拨时命中源仓记录），且聚合与仓储把同一行映射成两个实例（聚合内的增减看不见）。现四处统一改为**在仓库聚合内定位**（`inventory_records.warehouse_id` NOT NULL，聚合即该仓全部行），找不到才新建并 `addInventoryRecord` 挂回聚合；实测入库/调拨/出库/盘点/调整五种路径全 200，库中 A=45、B=10 与响应一致。<br>③ ~~`OrderSyncService`、`LogisticsTrackingService` 同为桩~~：`OrderSyncService` **已不存在**；`domain/logistics/service/LogisticsTrackingService` 仍在。<br>④ 真实平台外呼已具备：`infrastructure/platform/` 有 6 个 `HttpPlatform*Fetcher`（走 `HttpClientConfig.PLATFORM_REQUEST_FACTORY`，带 `platform.http.connect-timeout: 5s`/`read-timeout: 15s`），与 6 个 `SamplePlatform*Fetcher` 并存，由请求参数 `useSampleData` 选择（如 `OrderImportApplicationService.java:87-88,432`）——**注意这是客户端可控的开关**，传 `true` 就导入示例数据。<br>⑤ 仍未收口：`WarehouseApplicationService.java:93` 用 `Location.builder()` 在应用层直接构造实体（单处 save）。 |
| 10 | **贫血风险** | `Warehouse` 上下文编排逻辑大量停留在应用服务而非聚合内 |

## 8. 构建与依赖

`java.version=17`，parent `spring-boot-starter-parent:3.5.9`；属性 `lombok 1.18.34`、`mapstruct 1.5.5.Final`、`spring.kafka.version 3.4.2`（**死属性，无 Kafka 依赖**）。

依赖（`backend/pom.xml`）：`spring-boot-starter-web`、`-data-jpa`、`-security`、`-aop`、`jjwt-api/impl/jackson 0.12.5`、`spring-boot-devtools`(optional)、`rocketmq-spring-boot-starter 2.2.3`、`mysql-connector-j`、`lombok`(provided)、`spring-boot-starter-test`、`springdoc-openapi-starter-webmvc-ui 2.6.0`、`junit-jupiter-api/engine`、`mockito-core`（后三者已由 starter-test 传递引入，属冗余声明）、`mapstruct` + `mapstruct-processor`(provided，同时又配在 `annotationProcessorPaths`，重复)。
插件：`spring-boot-maven-plugin`、`maven-compiler-plugin 3.11.0`（source/target 17 + lombok/mapstruct 处理器）、`maven-surefire-plugin 3.2.5`。

## 9. 命名约定（可观测）

| 模式 | 含义 | 数量 |
|---|---|---|
| `*JpaEntity` | JPA 持久化实体 | **40** 个 `@Entity`/`@Table`（旧记 24，2026-01-25 重测） |
| `*JpaRepository` | Spring Data 接口 | **26** |
| `*Repository` / `*RepositoryImpl` | 领域仓储接口 / 适配器实现 | **26 / 26** |
| `*Mapper` | MapStruct 领域↔JPA 转换 | **12**（其余上下文的转换内联在 RepositoryImpl 里） |
| `*ApplicationService` | 应用服务 | **15** |
| `*Service` / `*ServiceImpl` | 领域服务（接口/实现） | domain 下 `*Service` **23** 个文件，`*ServiceImpl` 7 个 |
| `*Event` | 领域事件 | **22**（旧记 23 属多计 1 个的计量错误，见 traceability.md §5 漂移警告） |
| `*Controller` | REST 控制器 | **20**（旧记 13）；`@Get/Post/Put/Patch/DeleteMapping` 合计 ~~**198** 处（2026-01-25 为 196，增量 = 平台域 `test-connection` + `push-fulfillment`）~~ → 🔄 **2026-03-22 复测 202 处**（+4 = 采购轮与面单/平台轮新增；`@RequestMapping` 前缀实测 20 个，全部 `/api/…`） |
| `*Dto` | 出参 | `OrderDto`,`OrderItemDto`,`CreateOrderDto`,`ProductDto`,`FinancialTransactionDto` |
| `*Request` / `*Command` / `*Query` | 控制器内**静态内部类**请求体 / CQRS 骨架 | — |
| 聚合根 | 无后缀（`Order`,`Customer`,`Warehouse`…） | 9 |

作者标签统一 `@author kenyon`。前端：`*Api` 导出对象（`api.js`）、`pages/<module>/{List,Detail,Create}*` 三件套。

## 10. 非功能性现状

| 维度 | 现状 |
|---|---|
| 性能 | ~~无分页~~ → 🔄 2026-03-22：**12 个控制器已返回 `PageResult`**（order/product/customer/finance/logistics/purchase + 6 个 `*ImportController`），但**仍有 7 个整体返回不定长数组**（`Warehouse`、`ShopWarehouse`、`PlatformIntegration`、`LogisticsChannel`、`Tenant`、`User`、`Role`，见 M-07）；~~无二级索引~~ → 🔄 **二级索引已存在**：19 个 `*JpaEntity` 在 `@Table(indexes=…)` 上声明了 **30 个 `@Index`**，`docs/db/schema.sql` 另有 **27 条 `KEY idx_*` + 22 条 `UNIQUE KEY`**（订单/采购/物流/商品/客户/店铺仓库的 `tenant_id`、`status`、`created_at` 等热列已覆盖）；无缓存、无读写分离 → 未分页的那批列表接口随数据量线性恶化 |
| 可用性 | 单实例；无健康检查端点使用（actuator **未引入**，`pom.xml` 0 处）；无重试/熔断 |
| 安全 | 见 [security-findings.md](security-findings.md)：~~鉴权架空、密钥明文、敏感字段外泄~~ → 🔄 2026-03-22 复测：S-01/S-02/S-03/S-05/S-06 已修（JWT 校验真实、admin-web 已接认证、平台凭据信封加密 + 出参掩码）；**仍未修的是 S-04**（`application.yml:93` JWT 密钥明文写死）、**M-09**（数据库口令明文）与 10 个无方法级鉴权的控制器（M-06） |
| 可观测 | `logging.level` 仅 INFO 四组，无 `logback-spring.xml`、无 traceId、无指标 |
| 可维护 | 无 CI、无后端 lint、无 schema 迁移工具、~~事务缺失~~ → 事务已有但无 `rollbackFor`（见 §7 第 1 条） |
| 兼容 | ~~Spring Security 使用已废弃的 `authorizeRequests`~~ → 🔄 2026-03-22：已迁 `authorizeHttpRequests`（`SecurityConfig:63` 起，L-01 已修）；**无 API 版本化仍成立**（20 个控制器的 `@RequestMapping` 全部是 `/api/<复数资源名>`，无 `/api/v1`；6 个导入控制器用 `/api/x/import` 子路径而非版本号） |
