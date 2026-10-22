# 后端工程规范与分层契约

> 定位：把 [architecture.md](../architecture.md) 的「现状」转成**可执行的约束**——新增/修改后端代码时按本文做，评审时按本文查。
> 事实基线：2026-01-25 工作树（含未提交的 product 分页改动）。凡标注「⚠️ 现状违反」的是当前代码不合规、但按规范必须收敛的点，不要求一次性改造，**新增代码不得延续**。
> 相关决策：[ADR-0001](../adr/0001-domain-driven-design.md)（分层与上下文）、[ADR-0012](../adr/0012-ddd-single-module-layering.md)（单模块分包与偏离）、[ADR-0010](../adr/0010-mapstruct-domain-jpa-bridge.md)（MapStruct 桥接）、[ADR-0011](../adr/0011-result-envelope-and-errors.md)（信封与错误）、[ADR-0003](../adr/0003-multi-tenancy-isolation.md)（租户隔离）。

## 1. 分层依赖规则（强约束）

包根：`site.hexaarch.ecommerce.logistics`。

| 层 | 允许依赖 | 禁止依赖 | 现状 |
|---|---|---|---|
| `interfaces` | `application`、`domain`（只读模型/枚举）、`interfaces/common` | `infrastructure` | ⚠️ 违反：`OrderController` 直接注入 `infrastructure.persistence.jpa.mapper.OrderMapper` |
| `application` | `domain` | `interfaces`、`infrastructure`（除 `TenantContext` 这类已声明的共享内核） | ⚠️ 部分违反：`LogisticsApplicationService:24,43` 持有他域 `domain/order/repository/OrderRepository` |
| `domain` | `domain`（本上下文）、`domain/common` | Spring/Spring Data/JPA/HTTP 任何注解 | ⚠️ 违反：16 个领域服务标 `@Service`；`PlatformIntegrationServiceImpl.java:22` 用 `@Autowired`。JPA 注解实测 0 处，此项干净 |
| `infrastructure` | `domain`（实现其接口）、`application` | `interfaces` | ⚠️ 违反：`jpa/mapper/OrderMapper.java:16-17` import `interfaces/dto/OrderDto,OrderItemDto` |

规则细目：

1. **依赖方向单向向下**（`interfaces → application → domain ← infrastructure`）。`domain` 是内环，不感知任何外环技术。
2. **跨上下文访问只能走两种方式**：① 目标上下文的领域服务/仓储接口（同进程读）；② 领域事件（最终一致写）。禁止在 A 上下文的应用服务里直接注入 B 上下文的 `Repository`；确需只读聚合，定义 `Acl`（防腐层）接口放在 A 域、由 B 域适配实现。
3. **Mapper 不得跨越到 `interfaces/dto`**。领域↔JPA 的 `*Mapper` 只处理 `domain ↔ jpa entity`；`entity/domain → 出参 DTO` 由 `interfaces` 层的 assembler 或独立 `*DtoMapper` 负责。
4. `interfaces` 不写业务判断（状态流转、金额计算、库存扣减）；只做入参绑定、调用应用服务、DTO 组装、返回信封。
5. `application` 不实现业务规则；只做编排、事务边界（见 §5）、事件发布触发。

### 落地方式：ArchUnit 门禁

单模块分包无法用 Maven 隔离，规范必须靠测试期校验。建议新增 `backend/src/test/java/.../ArchitectureRulesTest.java`：

```java
@AnalyzeClasses(packages = "site.hexaarch.ecommerce.logistics")
class ArchitectureRulesTest {
    @ArchTest
    static final ArchRule domainMustNotDependOnSpring =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..", "javax.persistence..", "jakarta.persistence..");

    @ArchTest
    static final ArchRule infrastructureMustNotDependOnInterfaces =
        noClasses().that().resideInAPackage("..infrastructure..")
            .should().dependOnClassesThat().resideInAPackage("..interfaces..");

    @ArchTest
    static final ArchRule interfacesMustNotDependOnInfrastructure =
        noClasses().that().resideInAPackage("..interfaces..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..");
}
```

依赖：`test` scope 引入 `com.tngtech.archunit:archunit-junit5`。**接入顺序**：先把三条规则写成 `DoNotIncludeImportedClasses` 白名单放通现有 4 处违反点，再逐条摘除白名单——否则第一天就红灯，规则会被直接删掉。

## 2. 上下文内部包结构

固定形态（缺项就不建目录，不要建空目录）：

```
domain/{context}/
├── aggregate/      聚合根（无后缀名：Order、Customer…）
├── entity/         聚合内实体（OrderItem、SKU）—— 仅当存在子实体时才建
├── valueobject/    值对象（OrderStatus、PaymentInfo、ProductPrice）
├── service/        领域服务（不归属单一聚合的逻辑；接口与实现同包）
├── event/          领域事件（*Event）
└── repository/     仓储接口（*Repository，纯接口，无注解）
```

⚠️ 现状不一致项（新增上下文时按本节，不要照抄）：

| 不一致 | 现状 | 规范要求 |
|---|---|---|
| 目录名 | 用 `aggregate`（不是 `model`） | 保持 `aggregate`，全仓一致 |
| `tenant` 上下文服务实现位置 | 接口在 `domain/tenant/service`，实现 `UserServiceImpl/RoleServiceImpl/TenantServiceImpl` 落在 `application/service` | 领域服务实现与接口同包（`domain/{ctx}/service`）；`application` 只放 `*ApplicationService` |
| `customer/finance/platform` 无 `entity` 目录 | 合理（无子实体） | 保持——不建空目录 |
| `tenant` 无 `event` 目录 | 合理（无事件） | 保持；若新增租户生命周期事件，一并补 `DomainEventPublisher` 的 topic 路由（§6） |
| `BaseAggregateRoot` 继承 | 5 个聚合继承，`Order/LogisticsOrder/Product/Warehouse` 未继承并各自重复实现 `domainEvents` | **新增聚合必须继承 `BaseAggregateRoot`**；存量迁移见 §6 的反射风险说明 |

## 3. 命名约定

| 后缀/形态 | 所在层 | 职责 | 实测数量（基线） |
|---|---|---|---|
| 无后缀（`Order`） | `domain/*/aggregate` | 聚合根，行为 + 不变式 + 事件注册 | 9 |
| `*Event` | `domain/*/event` | 领域事件（不可变事实） | 22 |
| `*Repository` / `*RepositoryImpl` | `domain` / `infrastructure/persistence/repository` | 领域仓储接口 / 适配器 | 13 / 13 |
| `*JpaEntity` | `infrastructure/persistence/jpa/entity` | JPA 持久化实体 | 21 `@Entity` |
| `*JpaRepository` | `infrastructure/persistence/jpa/repository` | Spring Data 接口 | 13 |
| `*Mapper` | `infrastructure/persistence/jpa/mapper` | MapStruct 领域↔JPA | 12（`InventoryRecord` 无独立 Mapper，转换写在 Impl 内） |
| `*ApplicationService` | `application/service` | 用例编排 | 11 |
| `*Service` / `*ServiceImpl` | `domain/*/service` | 领域服务 | 按上下文 |
| `*Dto` | `interfaces/dto/{ctx}` | 出参 | `OrderDto`、`OrderItemDto`、`CreateOrderDto`、`ProductDto`、`FinancialTransactionDto` 等 |
| `*Request` | `interfaces/controller` 静态内部类 | 入参 | 见 §4 |

补充规则：

- **入参不用领域模型**：`PlatformIntegrationController` 直接用聚合 `PlatformIntegration` 收发（S-05），新接口必须用 `*Request`/`*Dto`。
- **禁止同名歧义**：`domain/{ctx}/repository/OrderRepository` 与 `jpa/repository/OrderJpaRepository` 是两套体系，任何情况下不要 `import` 错；IDE 里以全限定名书写的引用视为评审缺陷。
- 作者标签统一 `@author kenyon`（现状一致）。
- 布尔字段不加 `is` 前缀于领域模型（避免 Lombok getter 与 Jackson 序列化名不一致）。

## 4. 接口层规范

| 项 | 规范 | 现状偏差 |
|---|---|---|
| 路由 | 复数资源名 + kebab-case：`/api/platform-integrations`、`/api/orders/{id}/status` | ⚠️ `SecurityConfig.java:40` 写的规则是 `/api/platform/**`，真实前缀 `/api/platform-integrations/**`（S-03）——新增控制器路径必须同步安全规则 |
| 动作型端点 | 用子资源表达状态迁移（`POST /api/orders/{id}/status`），不用动词路径 | 基本一致 |
| 入参承载 | 写操作用 JSON body（`@RequestBody`）；列表筛选用 query。**同一资源不得两种混用** | ⚠️ 见 [api.md §3](../api/api.md)：部分端点用表单/散参 |
| `tenantId` | **一律从令牌/`TenantContext` 取，禁止作为客户端参数** | ⚠️ `OrderController:45` 等以 `@RequestParam(defaultValue="default-tenant")` 接收（M-05）。整改见 [security-design.md §4](security-design.md) |
| 分页 | 列表端点必须支持 `page/size`（从 0 起，`size` 上限 200），返回 `PageResult` | ⚠️ 仅 `ProductRepositoryImpl:71,78` 已支持（未提交改动）；其余 11 控制器为无界返回（M-07） |
| 校验 | DTO 加 Bean Validation + 控制器 `@Valid` | ⚠️ 实测 `@Valid`/`@Validated`/`@NotNull`/`@NotBlank` 全仓 **0 处**，且 pom 无 `spring-boot-starter-validation`（M-10） |
| OpenAPI | 类上 `@Tag`、方法上 `@Operation`（现状 13/13 类、100/100 方法已覆盖，保持不掉） | ✅ |
| 返回值 | 统一 `Result<T>`，**不在控制器写 try/catch 吞异常** | ⚠️ `Finance/PurchaseController` 自吞异常返回 `code "500"`+HTTP 200（M-08） |

## 5. 应用服务与事务边界（最高优先级规范）

**规则：每一个会写多于一张表、或「写库 + 发事件」的用例方法，必须标 `@Transactional(rollbackFor = Exception.class)`。**

✅ 现状（2026-01-25 复测）：全仓 `@Transactional` ~~**98 处**（application 90 / infrastructure 8 / domain 0 / interfaces 0）~~ → **110 处**（application 90 / infrastructure **20** / domain 0 / interfaces 0），ISS-042 已修。
⚠️ 但规范要求的「查询用 `readOnly=true`、显式 `rollbackFor`」仍未落实：~~**98 处全是裸注解**（`readOnly` 0 处、`rollbackFor` 0 处）~~ → **`readOnly` 7 处（全在 `UserRepositoryImpl`/`RoleRepositoryImpl` 的读方法，为 ISS-061 而加，非全局落实）、`rollbackFor` 仍 0 处**（第 142 行口径）→ checked 异常不回滚。另注意：导入/批量链路按行提交是**有意**的部分成功语义，不要顺手给它们加外层事务。后果与优先级见 [security-findings.md S-06](../security-findings.md)。

写法约定：

```java
@Service
@RequiredArgsConstructor
public class OrderApplicationService {
    private final OrderRepository orderRepository;
    private final DomainEventPublisher eventPublisher;

    @Transactional(rollbackFor = Exception.class)          // 1. 边界在应用服务
    public Order createOrder(CreateOrderCommand cmd) {
        Order order = Order.create(...);                    // 2. 不变式在聚合内
        orderRepository.save(order);                        // 3. 单次保存（禁止双写）
        eventPublisher.publishEventsFrom(order);            // 4. 保存后发布
        return order;
    }
}
```

1. 事务边界只在 `application` 层，`domain` 与 `interfaces` 不加。
   - ✅ 这条**在 `domain`/`interfaces` 两层严格成立**（2026-01-25 实测：两层的 `@Transactional` 均 **0 处**）。
   - ⚠️ "只在 application" 不成立：`infrastructure` ~~有 **20 处**（分布在 **7 个** `*RepositoryImpl`，见本节末「补充口径」）~~ → 🔄 **2026-10-21 实测 21 处 / 8 个文件**（其中 **7 个** `*RepositoryImpl`，见本节末「补充口径」），且**这 7 个文件里有 2 个是为满足本条第 2 款而加的**（见下条）。
2. **只读用例显式不标注或标 `@Transactional(readOnly = true)`**，不要随手加写事务。
   - ~~⚠️ 现状：98 处**全是裸注解**，`readOnly` 0 处、`rollbackFor` 0 处~~ → **2026-01-25：`@Transactional` 共 110 处，其中 `readOnly = true` **7 处**（全部在 `UserRepositoryImpl` 的 4 个 find + `RoleRepositoryImpl` 的 3 个 find），`rollbackFor` 仍 **0 处**（checked 异常不回滚，S-06 未收口）**。 → 🔄 **2026-10-21 复测：`@Transactional` 145 处 / 30 个文件（application 124 / infrastructure 21 / domain 0 / interfaces 0），`readOnly = true` 20 处、`rollbackFor` 仍 0**（readOnly 分布：SystemConfig 4、Audit 4、Overview 2、Purchase 2、User 5、Role 3）。
   - 🔴 **新增硬性口径（本轮 ISS-061 换来的）**：**仓储读方法只要经过 mapper 触碰 `@ElementCollection` / `@OneToMany` 等懒加载集合，就必须自己标 `@Transactional(readOnly = true)`，不能指望调用方的事务或 OSIV。** 理由有两层：① OSIV 只覆盖 Web 请求线程，`CommandLineRunner`（`DataInitializationConfig`）、MQ 消费者、定时任务里读 `user.getRoleIds()` / `role.getPermissions()` 会直接 `LazyInitializationException`；② 该异常若被初始化代码的 `catch` 吞掉，**症状不是启动失败，而是"权限莫名没生效"**（本次表现为管理端 `hasRole("PLATFORM_ADMIN")` 恒定 403）。配套要求：mapper 把集合交给领域对象前**必须拷贝**（`RoleMapper` 用 `new HashSet<>(permissions)`），否则领域侧的改动会绕过脏检查直接写回库表。
   - 反例口径：给「入参不带懒加载集合」的 find 方法加 `readOnly` 属于无效装饰，不要为了凑数扩散。
3. ⚠️ ~~禁止重复保存：`OrderApplicationService.createOrder` 领域服务内部已 `save`，应用层 `:39-42` 又 `save` 一次~~ → **✅ 重复保存已消除（2026-01-25 复核）**：`OrderApplicationService.createOrder`（`:55-63`）现在只调 `orderProcessingService.createOrder(...)` + `publishEventsFrom`，不再 `save`。但**这条规范本身仍未满足**：保存动作落在**领域服务**里——`domain/order/service/OrderProcessingService.java:55,72,94,116,138,150` 共 6 处 `orderRepository.save`，而本条要求「领域服务只做计算与校验并返回聚合」，仓储的直接调用者应该在应用层（对照 `OrderApplicationService.updateOrderStatus:72-77` 就是这个形态）。要嘛改调用方向，要嘛把这条规范改成「允许领域服务保存，但一次用例只能有一处 save」。
4. 事件发布必须在 `save` 之后、方法返回之前（~~现状 19 处调用点已符合~~ → **2026-01-25 实测 39 处 `publishEventsFrom` 调用点**，分布在 11 个服务：`LogisticsApplicationService` 10、`CustomerApplicationService` 7、`ProductApplicationService` 6、`FinanceApplicationService` 4、`PurchaseApplicationService` 3、`OrderApplicationService` 3，其余 5 个各 1-2；`createOrder` 这类「领域服务内 save → 应用层 publish」的顺序符合，但 `OrderProcessingService` 自己不发事件、由应用层补发，双通道重复问题见 ISS-054）。等真正接入事务后，改用 `@TransactionalEventListener(phase = AFTER_COMMIT)` 替代双通道直发（[ADR-0006](../adr/0006-rocketmq-domain-events.md)）。
   - 补充口径（2026-01-25）：~~`@Transactional` 全仓 **98 处**（另有 1 处 `@TransactionalEventListener`，不计入），分布在 **26 个文件**：application 层 20 个文件、`infrastructure/persistence/repository/*RepositoryImpl` 5 个文件、`infrastructure/messaging` 1 个文件——第 1 条「只在 application 层」因此**并不成立**，仓储自定义实现也在标事务。**98 处全部是裸注解**，无一处 `readOnly = true`，第 2 条同样未落地。~~
     → **本轮复测更新：全仓 110 处**（application 90 / infrastructure 20 / domain 0 / interfaces 0），`infrastructure/persistence/repository` 下带注解的 `*RepositoryImpl` 从 **5 个文件增加到 7 个**（新增 `UserRepositoryImpl`、`RoleRepositoryImpl`，共 9 处注解：4 写 + 5 读，即上面第 2 款的 7 处 `readOnly` 之来源）。`@TransactionalEventListener` 仍 1 处、不计入。第 1 条的结论不变（仓储也在标事务），但第 2 条已**部分落地**。<br>🔄 **2026-10-21 复测：145 处 / 30 个文件（application 124 / infrastructure 21 / domain 0 / interfaces 0），`readOnly = true` 20 处、`rollbackFor` 0；`*RepositoryImpl` 带注解的仍是 7 个文件**——第 2 条从「部分落地」推进到「应用服务与仓储读方法均有 readOnly，但写方法仍无 rollbackFor」。
5. `AFTER_ROLLBACK` 类监听不得承担业务补偿——补偿要有独立的可重试任务，见 [integration-design.md §7](integration-design.md)。

## 6. 领域事件规范

| 项 | 规范 | 现状 |
|---|---|---|
| 事件基类 | 所有事件继承 `BaseDomainEvent`（提供 `eventId`(UUID)、`occurredOn`） | ⚠️ 仅 8/22 继承（platform/purchase/finance/customer 各 2）；order/logistics/warehouse/product 的 14 个是独立类 |
| 命名 | 过去式事实：`OrderCreatedEvent`、`InventoryShortageEvent` | ✅ 一致 |
| 载荷 | 只放**下游需要的最小字段 + ID**；禁止内嵌支付/地址/密钥类值对象 | ⚠️ `OrderCreatedEvent.java:23-25` 内嵌 `ShippingAddress` 与 `PaymentInfo`（含 `transactionId`），事件经 MQ 原样外发；`CustomerCreatedEvent.java:17` 含 email |
| 发布通道 | 单通道（先进程内，MQ 作为可选投递），避免同一事件双触发 | ⚠️ 双通道直发导致 `EventHandler` 与 `OrderCreatedEventConsumer` **各建一次物流单**（重复副作用） |
| topic | 常量表驱动，禁止按类名字符串猜测 | 现状 `DomainEventPublisher:85-99` 用包名匹配推断 5 个 topic；`product/purchase/customer/platform` 事件**无 topic 命中**（`:72` 返回 null 仅 warn） |
| 反射取事件 | 必须走 `BaseAggregateRoot.getDomainEvents()` 方法 | ⚠️ `DomainEventPublisher:37-39` 用 `getDeclaredField("domainEvents")`，**不含父类字段** → 仅继承基类的聚合会 `NoSuchFieldException` 被 debug 吞掉（`:52-54`），事件静默丢失 |
| 消费者形态 | 每个消费者是**独立顶层类** | ✅ 2026-01-25 已符合：4 个消费者是 `infrastructure/messaging/consumer/` 下的独立顶层 `@Component` 类，各带 `@ConditionalOnProperty(prefix="rocketmq", name="enabled")` + `@RocketMQMessageListener`（ISS-034 已修）。⚠️ 规范之外的两条阻塞仍在：事件无 `@JsonCreator` → 消费端反序列化失败（ISS-052）；与进程内 `@EventListener` 双通道重复副作用（ISS-054） |

## 7. 异常与响应规范

权威定义见 [error-codes.md](../api/error-codes.md)。本节只列编码要求：

- 业务失败**只抛 `BusinessException` 家族**（`EntityNotFoundException` / `InvalidOperationException` / `ValidationException`，均带 `errorCode`）。
- ⚠️ 现状例外要收敛：~~`WarehouseApplicationService:177` 抛 `UnsupportedOperationException`、`:179` 抛 `IllegalArgumentException`，绕过家族体系；`executeInventoryCount` 直接返回 `List.of()`。桩实现要么显式抛 `NotImplementedException`→501，要么不暴露端点。~~
  → **2026-01-25 复测**：`UnsupportedOperationException` 全仓 **0 处**、`executeInventoryCount` 不再返回 `List.of()`（ISS-012 已修）。**但 `IllegalArgumentException` 这条家族旁路仍在，且改成了"由兜底映射吸收"的形态**：`Warehouse.updateInventory:195` 定位不到库存记录时抛 `IllegalArgumentException("Inventory record not found: " + productId)`，由 `GlobalExceptionHandler.java:108-118` 统一映射为 **400 `INVALID_OPERATION`**（ISS-037 的修法）。<br>📌 本轮踩坑记录：这个旁路**掩盖过一次真实缺陷**（ISS-060）——同一条 400 文案既可能是"调用方传错 SKU"，也可能是"仓储定位逻辑串了仓库"，两者在响应里长得一模一样；且该异常在领域内抛出后不带 `errorCode`，排查只能靠读栈。**结论：家族旁路可以留在兜底映射里，但凡它的文案会进生产日志，就要给它一个专属 `errorCode`。**
- ⚠️ 抛异常分布实测：~~`domain` 24 处、`application` 2 处、`interfaces` **0 处**~~ → **2026-01-25 重测（主源码 `grep -rn "throw new"`）**：

  | 层 | 合计 | `BusinessException` 家族 | JDK 原生 `IllegalArgumentException`/`IllegalStateException` | 其他 |
  |---|---|---|---|---|
  | `domain` | **66** | 33（`ValidationException` 18 / `BusinessException` 9 / `InvalidOperationException` 6） | **31（IAE 28 + ISE 3）** | — |
  | `application` | **94** | **94（100%）**：`ValidationException` 83 + 全限定名写法的家族异常 8 + `EntityNotFoundException` 3 | **0** | — |
  | `interfaces` | **19** | **19（100%）**：`ValidationException` 11 + `BusinessException` 4 + 全限定名 4 | **0** | — |
  | `infrastructure` | **35** | 9 | 8 | **18 处适配器专属异常**（`Platform{Product,Order,Logistics,Customer,Warehouse}FetchException` + `FinanceFetchException`，各 4 处；另有 `RuntimeException` 2） |

  **两点订正**：① 原文「application 2 处 / interfaces 0 处」是修复前的旧数——现在 **application 与 interfaces 两层已 100% 只抛家族异常**，家族旁路全部收敛在 **`domain` 那 31 处 JDK 原生异常**（主要是聚合内前置校验）；② `infrastructure` 的 18 处 `*FetchException` 是**有意独立于家族之外**的一族（外呼失败要区分重试/降级，见 [integration-design.md](integration-design.md)），但它们**没有专属 `@ExceptionHandler`**，落到 `GlobalExceptionHandler.java:197` 兜底 → 对外呼故障呈现为 500 且原因被兜底文案吃掉，属待办。<br>规范是**控制器不判断业务**，校验失败由 Bean Validation 抛出，业务失败由领域抛出，全局兜底由 `GlobalExceptionHandler` 完成。
- 禁止把 `e.getMessage()` 拼进响应文案（M-08）。
- 禁止 `catch` 后只记日志继续走（`DomainEventPublisher:74-76` 的 MQ 发送失败即属此类，L-04）。

## 8. 持久化规范

| 项 | 规范 | 现状 |
|---|---|---|
| schema 来源 | JPA 实体 + `ddl-auto: update`（[ADR-0002](../adr/0002-hibernate-ddl-auto.md)），**改实体必须同步 `docs/db/schema.sql`** | ✅ 约定已写入 [docs/README §4.3](../README.md) |
| 索引 | 查询条件列与外键列必须有 `@Table(indexes=...)` | 🟡 2026-01-25 重测：实体侧 **27 个 `@Index` + 11 个 `@UniqueConstraint`，分布在 18 个实体**（旧口径「只有 product 上下文 3 张、其余 21 张无索引」已过期），运行时库非主键索引 66 个。⚠️ 残留：6 张表的 `tenant_id` 无索引；所有业务关联列**没有 DB 级外键约束**（只有索引）——「外键列没索引」这个旧说法本身不准确，MySQL 会为 FK 列自动建索引 |
| 唯一约束 | 业务自然键必须加（`uk_products_external_ref` 为范例） | ⚠️ 仅 `products` 表 + `users.username/email`（`UserJpaEntity:29,35`） |
| 时间字段 | 统一 `LocalDateTime createdAt/updatedAt` + 审计注解 | ⚠️ `LogisticsOrderJpaEntity:57-60` 用 `Long`；无 JPA Auditing（`@CreatedDate/@EntityListeners` 0 命中），靠手工赋值（`ProductRepositoryImpl:112-118`） |
| 关联加载 | 集合用 `LAZY` + 显式 `join fetch` 或 `@EntityGraph` | 实测 7 处显式 LAZY、0 处 EAGER；4 个 `@OneToMany` 有 N+1 形态（`ProductRepositoryImpl:74-77` 逐条转聚合） |
| 乐观锁 | 状态迁移类聚合必须有 `@Version` | ⚠️ 全仓 `@Version` 0 处 |
| 软删除 | 统一 `deleted` 标记 + 查询过滤，不用状态枚举表达删除 | ⚠️ 现状只有 `CustomerStatusJpaEntity:12` 的 `DELETED` 枚举值 |
| 敏感列 | 第三方凭据、支付信息不得明文入库；出参必须剔除 | ⚠️ `platform_integrations.api_key/api_secret` 明文且原样返回（S-05） |
| Repository 注册 | 一种方式：`@Repository` 扫描 | ⚠️ `Customer/FinancialTransaction/PurchaseOrder/PlatformIntegration` 用 `@Repository`；`Role/User/Tenant/Product` 既 `@Repository` 又在 `PersistenceConfig:47-106` `@Bean` 双注册。<br>📐 **2026-01-25 实测总量口径**：`*RepositoryImpl` 共 **26 个**，其中 **21 个**带 `@Repository`；`PersistenceConfig` 共 **9 个 `@Bean`** = **8 个 `new *RepositoryImpl`**（`Order:48`、`LogisticsOrder:54`、`LogisticsChannel:62`、`Warehouse:70`、`InventoryRecord:78`、`User:92`、`Role:100`、`Tenant:106`——这 8 个类除 `User/Role/Tenant` 外都**不**带 `@Repository`，即"扫描"与"@Bean"两种机制在同一份配置里混用）+ **1 个纯转发别名**（`productRepository:86-88` 只是把已被 `@Repository` 扫描到的 `ProductRepositoryImpl` 原样 `return` 出去，不做任何装配）。⚠️ 隐患：`User/Role/Tenant/Product` 这 4 个类**同时**带 `@Repository`（扫描名如 `userRepositoryImpl`）又被 `@Bean` 注册（名 `userRepository`）→ 同一类型上有两个 Bean 定义。目前各注入点的**字段名一律等于 `@Bean` 方法名**（实测 `UserServiceImpl:23,28`、`RoleServiceImpl:20,23`、`TenantServiceImpl:24-25`、`CustomUserDetailsService:31`、`DataInitializationConfig:73,76`），靠"按参数名消歧"才没报 `NoUniqueBeanDefinitionException`。<br>🔴 **未验证项**：应用能启动只说明"消歧成功"，**并没有证据说明 ISS-061 新加的 `@Transactional(readOnly = true)` 落在真正被注入的那一份实例上**（仓库内无 Actuator、无 `@SpringBootTest`，无法列出运行时 Bean）。收敛方向：删掉 `PersistenceConfig` 的 8 个 `@Bean` 与那个别名，统一走 `@Repository` 扫描，让每类型只剩一个定义——届时这个歧义自动消失，也才谈得上用一条冒烟断言锁住权限链路（见 [testing.md](../testing.md) 里"第一个 @SpringBootTest 应验证什么"）。

## 9. 配置与凭据

- 所有可变项走 `${ENV_VAR:default}` 占位 + profile 分文件；⚠️ 现状 `application.yml` 是全仓唯一配置文件、无 profile、无任何占位符，DB 口令 `123456` 与 `jwt.secret` 明文入库（S-04，[ADR-0009](../adr/0009-config-externalization.md)）。
- 新增配置项必须同时登记到 [deployment.md §3](../deployment.md) 的配置面清单。
- ⚠️ ~~`spring.rocketmq.*`（`application.yml:29-39`）是**死配置**：无 `@ConfigurationProperties`/`@Value` 读取，且 starter 期望顶层 `rocketmq.` 前缀。新增 MQ 配置前先确认绑定生效（启动日志打印实际 nameServer）。~~
  → **2026-01-25 已修（ISS-042 同轮）**：前缀纠正为顶层 `rocketmq:`（`application.yml:39-45`：`enabled: true` / `name-server: localhost:9876` / `producer.group` / `send-message-timeout: 3000` / `retry-times-when-send-failed: 2`），并在 `:37-38` 留了「为什么必须是顶层前缀」的说明注释——这条正是上面那句要求写进配置的。⚠️ 仍未收口的 MQ 问题另见 **ISS-052（事件类无 `@JsonCreator`/空参构造 → MQ 侧反序列化失败）**、**ISS-053（AFTER_ROLLBACK 处理器只打日志）**、**ISS-054（进程内 handler 与 MQ 消费者双通道重复副作用）**。
- 目标态 profile 与密钥注入方案见 [dev-guide.md §6](dev-guide.md) 与 [deployment-architecture.md §5](deployment-architecture.md)。

## 10. 日志与注释

- 统一 `@Slf4j`。⚠️ 现状：~~`@Slf4j` 1 个类、`LoggerFactory.getLogger` 5 个类、其余无 logger；`System.out.println` 5 处（`TenantIsolationAspect:41`、`OrderSyncService:31`、`PlatformIntegrationServiceImpl:96,104,112`）~~ → **2026-01-25 实测：有 logger 的类 **44 个**（`@Slf4j` 35 + `LoggerFactory.getLogger` 9），`System.out.println` 在主源码 **0 处**——原列的 5 处随 ISS-009（切面改用 `@Slf4j`）、`OrderSyncService` 整类删除、ISS-013（`sync-*` 改真实编排）一并消失。新代码禁用 `System.out` 这条从此可当硬规则用**。
- 关键路径必须可追溯：进入用例打 `info`（含 `tenantId`、资源 ID、耗时），失败打 `error`（含堆栈）。格式与前缀规范见 [observability.md §3](observability.md)。<br>📌 **本轮反面教材（ISS-061）**：`DataInitializationConfig` 的角色分配步骤把异常 `catch` 成一行 `logger.error("…时发生错误")`，**没有堆栈、没有租户/用户上下文**，实际后果是「管理端所有页面恒定 403」而不是「启动报错」——排查方向被完全带偏。初始化类里的 `catch` 要么重抛、要么打全堆栈，**绝不允许只打一句中文**（与本节最后一条同源）。
- 注释只写**为什么**；~~`// 这里可以实现租户过滤逻辑`（`TenantIsolationAspect:39-40`）这类占位注释在提交前必须变成实现或 issue 编号~~ → **该占位注释已随切面落地被删**（现为 `:91-97` 的"仓储层不过滤，隔离由服务层参数校验保障"——这句是**真实设计意图**而非占位，但它正是 ISS-050 读路径无隔离的根因，已在 [ADR-0003](../adr/0003-multi-tenancy-isolation.md) 记为待办）。仍有占位注释的对照例：~~`UserRepositoryImpl:83 // 实际项目中应从数据库获取用户角色权限`~~ → **本轮已删**（改为真实读取 + `@Transactional(readOnly = true)`）。登记规则不变：编号进 [known-issues.md](../qa/known-issues.md)。
- 明文口令不得出现在日志（~~`DataInitializationConfig:75` 打印默认管理员密码，S-04~~ → **行号漂移，现为 `:145` `logger.info("默认密码: 123456")`，编码点在 `:134`；按本轮边界**仍未修**，作为已知接受风险记录））。

## 11. 提交前自检清单

评审/自测按此顺序执行，任一项不过即不合并：

1. `mvn -f backend/pom.xml test` 通过（当前预期不通过，见 [testing.md §3](../testing.md)；修复后作为门禁）。
2. ArchUnit 三条依赖规则通过（§1）。
3. 新增写用例已加 `@Transactional(rollbackFor = Exception.class)`（§5）。
4. 新增/变更 JPA 实体：`schema.sql` 已同步、索引与唯一约束已补、时间字段口径一致（§8）。
5. 新增端点：`@Tag`/`@Operation` 齐全、DTO 有校验注解、列表有分页、响应不含敏感列（§4）。
6. `api/api.md` 端点表与 `spec/*.md` 接口对照已更新（[docs/README §4.1](../README.md) 的触发时机）。
7. 无新增 `System.out`、无新增吞异常 `catch`。

## 12. 存量违规处置原则

- **不做大爆炸重写**。按「新增代码即刻合规 + 触碰即修（boy scout）」推进；每条存量违规在 [known-issues.md](../qa/known-issues.md) 立项 `ISS-xxx`，改动时顺带收敛并在提交信息引用。
- 若一次性整改（如统一 `BaseAggregateRoot`、去双通道、引入 validation starter），必须先出 ADR（[ADR 索引](../adr/README.md)），再按模块灰度。
- 优先级建议（结合风险与依赖）：事务边界（§5）→ 鉴权与 `tenantId` 收口（§4/§9，见 [security-design.md](security-design.md)）→ 校验与错误码（§7）→ 事件形态（§6）→ 索引与分页（§8）。
