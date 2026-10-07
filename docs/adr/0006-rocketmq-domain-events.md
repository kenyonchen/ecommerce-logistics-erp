# ADR-0006：以 RocketMQ 承载领域事件，聚合事件双通道发布

- 状态：**Proposed**（代码已写，链路未真正生效）
- 日期记录：2026-01-25
- 相关：[architecture.md §3/§4](../architecture.md)、[security-findings.md S-06/L-04](../security-findings.md)

## 背景

业务链路天然是事件驱动的：订单创建 → 生成物流单；库存不足 → 触发采购；物流完成 → 生成财务记录。跨上下文协作需要在不直接依赖彼此的前提下推进（订单上下文不应该知道物流上下文的存在）。

## 决策

1. 聚合在状态变更方法内 `registerDomainEvent(...)`，共 22 个 `*Event`。
2. 基础设施层 `DomainEventPublisher.publishEventsFrom(aggregate)` 反射读取 `domainEvents`，对每个事件**同时**：
   - `ApplicationEventPublisher.publishEvent(event)`（进程内，供 `EventHandler` 消费）；
   - `RocketMQTemplate.convertAndSend(topic, event)`（跨进程）。
3. topic 由事件类全名推断：`order-events` / `logistics-events` / `inventory-events` / `finance-events` / `purchase-events`（无 tag、无 `selectorExpression`）。
4. `RocketMQEventConsumer` 内定义 4 个 `@RocketMQMessageListener`，consumerGroup 分别 `logistics-group`、`warehouse-group`、`finance-group`、`purchase-group`。
5. 环境侧用 `scripts/docker/docker-compose.yml` 起 RocketMQ 5.3.2（namesrv + broker + proxy）。

## 后果（当前实现的四个已确证问题）

1. **副作用重复**：`EventHandler`（`@Component` + 4 个 `@EventListener`）与 MQ 消费者对同一批事件执行**相同**动作。双通道 ⇒ 一个 `OrderCreatedEvent` 会被处理两次（两次建物流单）。要么只保留进程内通道（单体阶段足够），要么让 MQ 通道独占副作用。
2. **`spring.rocketmq.*` 是死配置**：无任何 `@ConfigurationProperties`/`@Value` 读取；且 starter 期望的顶层前缀是 `rocketmq.`，本项目嵌在 `spring.rocketmq.` 下，前缀不匹配。
3. **`RocketMQTemplate` 被手工裸 new**：`RocketMQConfig.java:21-23` `return new RocketMQTemplate();`（无 nameServer、无 producer group）⇒ 发送路径脱离 starter 配置；发送异常只在 `DomainEventPublisher.java:74-76` 被 catch 后记 error（事件静默丢失）。
4. **监听器可能未注册**（运行期待核实）：4 个监听器是 `RocketMQEventConsumer` 的**非静态内部类**且各自标 `@Component`，Spring 组件扫描通常忽略非独立内部类。
5. 事件消费里 tenantId 被硬编码且不一致：`"default-tenant"`（`RocketMQEventConsumer.java:54`）vs `event.getTenantId()`（`EventHandler.java:51`）；建采购单处一处 `"default-tenant"`、一处 `"DEFAULT_TENANT"`。
6. **无事务边界**（全仓 `@Transactional` 0 处），`@TransactionalEventListener(AFTER_ROLLBACK)` 永不触发 ⇒ 事件可能在业务回滚后仍被发出，且没有 outbox 机制保证「落库与发消息」的原子性。

## 建议

- 单体阶段先只保留**进程内事件**（`@EventListener` / `@TransactionalEventListener` + 真实事务边界），把 MQ 作为「将来拆分服务时」的选项；这样能同时消掉问题 1、2、3、4。
- 若确需 MQ：配置前缀改为顶层 `rocketmq.`、删除手工 `RocketMQTemplate` bean 改用 starter 装配、监听器提为独立静态类、引入 outbox 表 + 定时投递保证至少一次与可回放，并为消费端做幂等（当前消费者无任何幂等键）。
- 无论哪条路：补齐 `tenantId` 来源（从事件载荷取，禁止硬编码）。


## 更新（2026-01-25 按工作树复测）

决策**仍未最终敲定**（问题清单里 1/2/3/4/5 已修，但两条新的硬阻塞使 MQ 通道端到端仍不可用，所以"要不要保留 MQ"这个决定事实上被推迟了）。逐项对账：

- ✅ **问题 1（消费者结构）**：4 个监听器已从"非 static 内部类 + 各自 `@Component`"拆为 `infrastructure/messaging/consumer/` 下的 4 个**独立顶层类**（`OrderCreatedEventConsumer`/`OrderStatusChangedEventConsumer`/`LogisticsCompletedEventConsumer`/`InventoryShortageEventConsumer`）。
- ✅ **问题 2（`spring.rocketmq.*` 死配置）**：配置已归位到顶层 `rocketmq.`（`application.yml:36-49`，含 `name-server: localhost:9876`、producer group、`send-message-timeout: 3000`、重试次数）。
- ✅ **问题 3（裸 new `RocketMQTemplate`）**：`RocketMQConfig.java:24-27` 现为 `@Configuration` + `@ConditionalOnProperty(prefix="rocketmq", name="enabled")` + `@Import(RocketMQAutoConfiguration.class)`，模板与生产者改由 starter 依配置构建。这里有个**必须留在文档里的坑**：`rocketmq-spring-boot-starter 2.2.3` 只把自动配置注册在 `META-INF/spring.factories`，而 Boot 3.x 不再读该文件（改读 `META-INF/spring/…AutoConfiguration.imports`）→ 不显式 `@Import` 时 `RocketMQTemplate` 与监听器容器**一律不装配**，且不会有任何报错提示。
- ✅ **问题 5（topic 路由）**：`DomainEventPublisher.getTopicForEvent`（`:110-128`）现覆盖 **8 个事件包**，22 个领域事件不再有返回 null 被 `log.warn` 丢弃的分支。
- ✅ **`@Transactional` 从 0 → ~~98~~ → 2026-01-25 二次实测 **110 处**（application 90 / infrastructure 20）**：AFTER_ROLLBACK 现在真的会触发（处理器只打日志，见 ISS-053）。⚠️ 但 `readOnly` 只有 **7 处**、`rollbackFor` **仍是 0 处**（checked 异常不回滚），所以"事务边界已补齐"这句要打七折读。
- ❌ **仍然成立的阻塞（新立编号）**：
  - **ISS-052**：事件类字段全 `final` + 私有 builder 构造 + **无 `@JsonCreator`** → 消费端 Jackson 无法反序列化。全仓 `@JsonCreator` 仅 1 处，且是值对象 `InventoryMovement.java:39`，不是事件。即"配置通了、类注册了、消息发出去了，消费方仍会在这一步失败"。
  - **ISS-054**：**双通道重复副作用未修**——`EventHandler.java:23` 的 4 个 `@EventListener`（`:45,:61,:76,:92`）与 4 个 MQ 消费者做完全相同的动作，`OrderCreatedEvent` 会被各触发一次（建两张物流单）。这是本 ADR 问题 1 的"另一半"，结构修复并没有消除它。
  - **ISS-053**：`publishEventsFrom()` 对发送异常吞掉只记日志、无 outbox ⇒ 「落库成功、事件丢失」无人知晓。
- ⚠️ 运维前提：`rocketmq.enabled` 现为 **`true`**，`@ConditionalOnProperty` 会连带装配消费者容器 → **本机没有 broker 时启动会失败**；想只跑进程内通道需显式改 `enabled: false`（配置注释已说明这一点）。
- 🔎 **2026-01-25 补记：上面「问题 1/2/3/4/5 已修」里的「4」被算成已修是不准确的，问题 4 只修了一半。** 原问题是「tenantId 被硬编码且不一致」：
  - ✅ **"不一致"这一半确实修了**——订单侧现在两条通道同源：`OrderCreatedEventConsumer.java:36-38` 与 `EventHandler.java:51` 都取 `event.getTenantId()`；原先那个 `"DEFAULT_TENANT"`（下划线写法）已不存在。
  - ❌ **"硬编码"这一半仍在**：`OrderCreatedEventConsumer.java:38` 在 tenantId 为空时回落 `"default-tenant"`；采购侧**两处都仍写死** `"default-tenant"`（`EventHandler.java:100`、`InventoryShortageEventConsumer.java:37`），根因是 `InventoryShortageEvent` 载荷里根本没有 `tenantId` 字段——**要修的是事件契约，不是调用点**。
  - 📌 另：`application.yml` 的 rocketmq 配置块实测在 **`:39-49`**（`:36-38` 是注释），上文写作 `:36-49`。
  - ⇒ 问题 4 的准确状态应从「已修」下调为「🟡 部分修」，并保留到事件载荷补齐 `tenantId` 为止（与 **ISS-051** 同源：租户兜底散落全仓 17 处字面量）。
- 🔴 **2026-01-25 二次实测新增：本 ADR 上面所有关于"消费端通不通"的讨论，前提都不成立——多数聚合的事件根本没有被发布（新立 ISS-062，P0，优先级高于 ISS-052/053/054）。**
  - **判别式不是 topic、不是注解，而是"交给 `publishEventsFrom()` 的是哪一个对象"**：`DomainEventPublisher.java:35-53` 在 `:40` 见 `domainEvents` 为空就**静默 return，连一行日志都不打**；而 12 个 Mapper 里凡是重建聚合的地方都会把 `domainEvents` 初始化成**空列表**（`OrderMapper.java:91`、`WarehouseMapper.java:99-101` 反射 `set(..., new ArrayList<>())`，其余聚合靠构造器/Builder 初始化，如 `Order.java:63,98`、`Warehouse.java:43,73`、`LogisticsOrder.java:31`）。
  - 于是 `return xxxRepository.save(aggregate)` 这种写法（实测 **57 处 / 14 个类**）返回的是**映射副本**，调用方手里的事件列表是空的；只有仓储 `save()` 返回 **void** 的两处（`PurchaseOrderRepositoryImpl.save():42-49`、`FinancialTransactionRepositoryImpl.save():48-51`）迫使领域服务写成 `save(x); return x;`，事件才留在原实例上得以发布。
  - **实测矩阵**（broker 在跑、`rocketmq.enabled: true`，判据只认应用日志里的 `发布领域事件` 行）：整个会话真正发布过的事件类型**只有 4 个**——`PurchaseOrderCreatedEvent`、`FinancialTransactionCreatedEvent`、`ProductCreatedEvent`、`SKUCreatedEvent`。HTTP 200 但**零发布**的有：`POST /api/orders`（`efa6720c-89ca-4b4c-a5ff-59ef189cd5a1`）、`POST /api/customers`（`CUST-f87fb7ee`）、`POST /api/logistics`（`aafbefe3-9742-4e79-b2a6-bb5f7b0cc087`）、入库后再出库到 0（必然命中缺货分支）——**既无 `发布领域事件`，也无 `接收到库存不足事件`，更没有自动生成的采购单**。⇒ 本 ADR 问题清单里"一个 `OrderCreatedEvent` 会被处理两次（两次建物流单）"（ISS-054）**不是现状，而是修好 ISS-062 那一刻立刻显形的风险**；"缺货→自动采购"这条曾被写作唯一闭环的链路，实测一次也没跑过。
  - **最小修法**：不是把 57 处返回形状都改一遍，而是**照抄已有的正确样板** `ProductApplicationService.java:124-126`（另见 `:170,:267,:283,:333`）——`X saved = repo.save(x); domainEventPublisher.publishEventsFrom(x); x.clearDomainEvents(); return saved;`，即发布对象必须是**调用方自己那个聚合实例**。
    ⚠️ **别去补调用点**：`publishEventsFrom` 实测已有 **39 处 / 11 个应用服务**（先前写作 12 为口径错误；`grep -rl DomainEventPublisher` = 11 个业务类 + `RocketMQConfig`。分布：`LogisticsApplicationService` 10、`CustomerApplicationService` 7、`ProductApplicationService` 6、`FinanceApplicationService` 4、`OrderApplicationService` 3、`PurchaseApplicationService` 3、`FinanceImportApplicationService` 2，`OrderImport`/`LogisticsImport`/`WarehouseApplicationService`/`PlatformIntegrationApplicationService` 各 1），调用点普遍存在，错的只是传进去的对象——例如 `OrderApplicationService:63` 发布的是 `orderProcessingService.createOrder(...)` 的返回值（= `OrderProcessingService:55` 的仓储副本），`:79` 干脆直接发布 `savedOrder`。并且要补充：发布器**只在应用层的这 11 个类里**，领域服务（`InventoryManagementService`/`SKUManagementService`/`LogisticsOrderProcessingService`/`WarehouseManagementService`/`OrderProcessingService`）一个都不注入 ⇒ 事件能否发出全靠应用层传对对象。库存还多一层方法级缺口：`WarehouseApplicationService` 的 `publishEventsFrom` 只有 `:268` 一处（在 `moveInventory` 区间），`inventoryIn`（`:110`）与 `inventoryOut`（`:125`）方法体内没有发布调用，所以 `POST /{warehouseId}/inventory/in|out`（`WarehouseController.java:151`/`:161`）这条路径连"传错对象"都谈不上，是根本没取事件。
    时效确认：探针实例是 12:57 启动的构建，已包含 10-01 17:16/17:45 给 `DomainEventPublisher` 新增 `resolveDomainEvents`（`:38`/`:61`，优先走基类访问器）的改动 ⇒ 本节的运行时结论对当前工作树成立。

**因此本 ADR 的「建议」段仍然有效且更具体了**：要么退回纯进程内事件（此时请同时删掉 `EventHandler` 与消费者的重复，并置 `rocketmq.enabled: false`），要么补齐 `@JsonCreator`（或 record/Jackson 模块）+ outbox + 消费端幂等键，再保留 MQ。二选一，不要停在当前~~"两条通道都通、动作做两遍、MQ 那半条其实消费不了"~~的中间态。
> 🔧 **2026-01-25 二次实测把这句现状描述订正为**：既不是"两条通道都通"，也不是"动作做两遍"，而是 **ISS-062 ⇒ 8 个会注册事件的聚合里 5 个（订单/客户/物流/仓储/平台）一条消息也没发过；真发得出去的采购、财务、商品，其 topic（`purchase-events`/`finance-events`/`product-events`）反而没有任何消费者**。所以「二选一」这个决定现在做还不迟，而且比之前更倾向第一条（退回纯进程内事件）——因为 MQ 通道要同时补 ISS-062（发布对象）、ISS-052（`@JsonCreator`）、ISS-053（outbox）、ISS-054（去重）四件事才谈得上端到端可用。
