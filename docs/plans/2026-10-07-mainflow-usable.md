# 主流程可用性收敛实施计划

> **For Claude:** 用 superpowers:executing-plans 逐任务执行本计划。

**Goal:** 修复事件发布断链（ISS-062）、订单发货扣库存联动（BR-04）、仓库列表分页与租户读过滤（ISS-050），使"下单→发货→扣库存→事件联动"主流程端到端可用。

**Architecture:** 单 Maven 模块 DDD（interfaces→application→domain←infrastructure）。事件经 `DomainEventPublisher.publishEventsFrom(aggregate)` 同步发布；仓储 `save()` 重建聚合会丢事件，需在仓储层搬运事件。租户上下文由 `TenantContext`（JWT 过滤器填充）提供。

**Tech Stack:** Java 17 / Spring Boot 3.5 / Spring Data JPA；React 19 + Vite（frontend/user-web）。

---

## Task 1: 修复领域事件断链（ISS-062）

**问题根因**：`*RepositoryImpl.save()` 返回 `toDomainAggregate(savedEntity)` 重建的副本，mapper 把 `domainEvents` 置空 → 应用服务对副本发布事件 = 发布空列表。受影响：Order（OrderCreated/StatusChanged/Shipped/Completed）、LogisticsOrder（含 LabelGenerated）、Warehouse、PurchaseOrder、Customer、FinancialTransaction、Shop、PlatformIntegration。

**Files:**
- Modify: `backend/src/main/java/site/hexaarch/ecommerce/logistics/domain/common/BaseAggregateRoot.java`
- Modify: 8 个 `infrastructure/persistence/repository/**/*RepositoryImpl.java` 的 `save()`（order / logistics / warehouse / purchase / customer / finance / shop / platform）
- Modify: `backend/.../domain/warehouse/service/InventoryManagementService.java`（返回聚合以供发布）
- Modify: `backend/.../application/service/warehouse/WarehouseApplicationService.java`（出入库/调拨/调整补发布）

**Step 1**: `BaseAggregateRoot` 增加静态反射搬运方法（Order/Warehouse/LogisticsOrder 未继承本类，必须用反射兜底）：

```java
/**
 * 将 source 聚合上已注册、尚未发布的领域事件搬运到 target 聚合。
 * 仓储层 save() 会用 JPA 实体重建聚合副本（domainEvents 被置空），
 * 应用服务随后对副本发布事件将拿到空列表 —— 保存后调用本方法把事件补回副本。
 */
public static void carryOverEvents(Object source, Object target) {
    if (source == null || target == null || source == target) {
        return;
    }
    List<Object> events = extractEvents(source);
    if (events == null || events.isEmpty()) {
        return;
    }
    List<Object> targetEvents = extractEvents(target);
    if (targetEvents == null) {
        return;
    }
    targetEvents.addAll(events);
}

@SuppressWarnings("unchecked")
private static List<Object> extractEvents(Object aggregate) {
    if (aggregate instanceof BaseAggregateRoot<?> base) {
        return base.getDomainEvents();
    }
    Class<?> type = aggregate.getClass();
    while (type != null) {
        try {
            java.lang.reflect.Field field = type.getDeclaredField("domainEvents");
            field.setAccessible(true);
            return (List<Object>) field.get(aggregate);
        } catch (NoSuchFieldException e) {
            type = type.getSuperclass();
        } catch (IllegalAccessException e) {
            return null;
        }
    }
    return null;
}
```

**Step 2**: 各 `*RepositoryImpl.save()` 在 return 前补一行（以 OrderRepositoryImpl 为例）：

```java
Order savedOrder = orderMapper.toDomainAggregate(savedEntity);
BaseAggregateRoot.carryOverEvents(order, savedOrder);
return savedOrder;
```

**Step 3**: `InventoryManagementService` 三个公共方法（inboundInventory / outboundInventory / adjustInventory）返回类型 `InventoryRecord` → 内部 record `InventoryOperationResult(InventoryRecord record, Warehouse warehouse)`，`return new InventoryOperationResult(inventoryRecord, warehouseRepository.save(warehouse))`。

**Step 4**: `WarehouseApplicationService` 相应改造：`inventoryIn/inventoryOut/countInventory/moveInventory/transferInventory` 取 `result.warehouse()` 调 `publishDomainEvents(...)`、返回 `result.record()`。

**Step 5**: `mvn -q clean compile` 通过；`mvn test` 观察基线。

## Task 2: 订单发货扣库存（BR-04）

**设计**：状态机里 SHIPPED 之后只能 COMPLETED、取消仅限发货前 → 在「非 SHIPPED → SHIPPED」迁移时逐订单行扣减库存，取消无需回滚。仓库来源：订单无 warehouseId，取租户下第一个仓库；无仓库则抛业务异常阻断发货。同状态幂等迁移不重复扣。

**Files:**
- Modify: `domain/warehouse/repository/WarehouseRepository.java` + Impl + `infrastructure/persistence/jpa/repository/warehouse/WarehouseJpaRepository.java`：加 `findFirstByTenantIdOrderByIdAsc`
- Modify: `application/service/warehouse/WarehouseApplicationService.java`：加 `DEFAULT_TENANT_ID`、`findFirstWarehouseIdByTenant(tenantId)`（空则抛 `ValidationException("发货失败：当前租户下没有可用仓库，请先创建仓库")`）；`createWarehouse` 的 tenantId 空白时回退 `TenantContext.getTenantId()` → `DEFAULT_TENANT_ID`
- Modify: `application/service/order/OrderApplicationService.java`：注入 `WarehouseApplicationService`；`updateOrderStatus` 记录 oldStatus，`newStatus == SHIPPED && oldStatus != SHIPPED` 时逐行 `warehouseApplicationService.inventoryOut(item.getSku(), warehouseId, item.getQuantity())`

**Step 1**: 先改测试（新增/扩展 `OrderApplicationServiceTest`）：发货调用扣库存、非发货状态不扣、库存不足异常上抛。
**Step 2**: 实现；`mvn test` 通过。

## Task 3: 仓库列表分页 + 租户过滤（ISS-050 @ 仓库）

**Files:**
- `WarehouseJpaRepository`：加 `Page<WarehouseJpaEntity> findByTenantId(String tenantId, Pageable pageable);`
- `WarehouseRepository`（领域接口）：加 `PageResult<Warehouse> search(String tenantId, int page, int size);` + Impl（PageRequest.of + DESC createdAt，size 夹取 1..200，参照 OrderRepositoryImpl.search）
- `WarehouseApplicationService.searchWarehouses(tenantId, page, size)`
- `WarehouseController.getAllWarehouses`：改 `Result<PageResult<Warehouse>>`，参数 `tenantId/page/size`，`resolveTenant`：参数 → `TenantContext.getTenantId()` → `DEFAULT_TENANT_ID`
- 受影响测试同步更新

## Task 4: 财务/产品租户口径统一

- `FinanceController.searchFinancialTransactions`：补 `resolveTenant`（参数 → TenantContext → DEFAULT），不再透传 null
- `ProductApplicationService.createProduct`：tenantId 空白回退 TenantContext → DEFAULT（核实现状）
- 前端 `ProductForm.jsx:69` 删除硬编码 `tenantId: DEFAULT_TENANT_ID`

## Task 5: 缺货事件租户修正

- `InventoryShortageEvent` 若无 tenantId 字段则补上；`Warehouse.updateInventory` 注册事件时带上 `warehouse.getTenantId()`
- `EventHandler.handleInventoryShortageEvent` 与 `InventoryShortageEventConsumer` 用事件的 tenantId（空则回退 default-tenant），替换硬编码

## Task 6: 前端适配（frontend/user-web）

- `src/services/api.js`：`getWarehouses: (params) => api.get('/warehouses', { params })`
- `pages/warehouse/WarehouseList.jsx`：适配 `PageResult`（`{content,total,page,size}`，参照 `OrderList.jsx`/`PurchaseList.jsx` 的写法），加分页器与 tenantId 缺省（依赖后端 resolve，不传即可）
- `pages/purchase/CreatePurchase.jsx:21-29`：仓库下拉兼容 PageResult（`res?.content ?? []`）
- `pages/warehouse/WarehouseDetail.jsx`：出入库弹窗加「调拨」类型：`movementType: 'TRANSFER'` + 目标仓库下拉（来自仓库列表），调 `warehouseApi.moveInventory(warehouseId, {...})`
- `npm run lint` + `npm run build` 通过

## Task 7: 全量验证

- `mvn -f backend/pom.xml test` 全绿（或不低于基线且新增用例全过）
- 前端两个工程 lint/build
- 冒烟（可选）：`scripts/docker/erp-services.sh start`（复用既有容器，禁止新建）→ `mvn spring-boot:run` → curl 登录建仓库/入库/建订单/发货，验证库存扣减与事件日志

## Task 8: 文档同步 + 分任务提交

- `docs/api/api.md`：仓库列表端点签名
- `docs/product/03-feature-map.md`：3.1（仓库列表分页）、3.6（BR-04 打通）、§5 ISS-062 修复说明
- `README.md` 功能状态表相应行
- 每个任务一个 commit，说明动机
