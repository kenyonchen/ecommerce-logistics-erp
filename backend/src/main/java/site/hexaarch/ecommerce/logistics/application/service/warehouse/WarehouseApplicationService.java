package site.hexaarch.ecommerce.logistics.application.service.warehouse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.hexaarch.ecommerce.logistics.application.service.support.TenantAccessGuard;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.common.exception.EntityNotFoundException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;
import site.hexaarch.ecommerce.logistics.domain.purchase.repository.PurchaseOrderRepository;
import site.hexaarch.ecommerce.logistics.domain.warehouse.aggregate.ShopWarehouse;
import site.hexaarch.ecommerce.logistics.domain.warehouse.aggregate.Warehouse;
import site.hexaarch.ecommerce.logistics.domain.warehouse.entity.InventoryRecord;
import site.hexaarch.ecommerce.logistics.domain.warehouse.entity.Location;
import site.hexaarch.ecommerce.logistics.domain.warehouse.repository.InventoryRecordRepository;
import site.hexaarch.ecommerce.logistics.domain.warehouse.repository.ShopWarehouseRepository;
import site.hexaarch.ecommerce.logistics.domain.warehouse.repository.WarehouseRepository;
import site.hexaarch.ecommerce.logistics.domain.warehouse.service.InventoryCountService;
import site.hexaarch.ecommerce.logistics.domain.warehouse.service.InventoryManagementService;
import site.hexaarch.ecommerce.logistics.domain.warehouse.service.WarehouseManagementService;
import site.hexaarch.ecommerce.logistics.domain.warehouse.valueobject.InventoryMovement;
import site.hexaarch.ecommerce.logistics.infrastructure.messaging.DomainEventPublisher;
import site.hexaarch.ecommerce.logistics.infrastructure.security.TenantContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 仓储应用服务，协调领域对象完成仓储相关的业务操作。
 *
 * @author kenyon
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseApplicationService {

    public static final String DEFAULT_TENANT_ID = "default-tenant";

    private final WarehouseRepository warehouseRepository;
    private final InventoryRecordRepository inventoryRecordRepository;
    private final WarehouseManagementService warehouseManagementService;
    private final InventoryManagementService inventoryManagementService;
    private final InventoryCountService inventoryCountService;
    private final DomainEventPublisher domainEventPublisher;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ShopWarehouseRepository shopWarehouseRepository;

    /**
     * 创建仓库
     */
    @Transactional
    public Warehouse createWarehouse(String tenantId, String warehouseName, String warehouseCode, String address, int capacity) {
        // 前端不再传 tenantId 时回退到 JWT 上下文，避免落库为 null 后被租户过滤查不到（ISS-051）
        String resolvedTenantId = resolveTenantId(tenantId);
        var warehouse = warehouseManagementService.createWarehouse(resolvedTenantId, warehouseName, warehouseCode, address, capacity);
        publishDomainEvents(warehouse);
        return warehouse;
    }

    /**
     * 更新仓库
     */
    @Transactional
    public Warehouse updateWarehouse(String warehouseId, String warehouseName, String warehouseCode, String address, int capacity) {
        // 先按登录租户校验归属，跨租户直接 404，避免改到他家仓库（ISS-050）
        requireWarehouse(warehouseId);
        var warehouse = warehouseManagementService.updateWarehouse(warehouseId, warehouseName, warehouseCode, address, capacity);
        publishDomainEvents(warehouse);
        return warehouse;
    }

    /**
     * 删除仓库
     * <p>
     * 三道门按"先看清存量、再查引用"排列：库存与库位是本聚合自己的子实体（删仓会随聚合一并回收），
     * 而采购单与店铺仓库绑定<b>不在</b>本聚合内、也没有外键，删掉仓只会把它们 {@code warehouse_id}
     * 掏成断链——采购域没有删除端点，断链之后只能靠 SQL 手工修（docs/qa/known-issues.md ISS-142）。
     * 口径与 R-31、R-59 一致：有引用就回可读中文原因并整体拒绝，不留半态。
     * </p>
     */
    @Transactional
    public void deleteWarehouse(String warehouseId) {
        Warehouse warehouse = getWarehouse(warehouseId);
        List<InventoryRecord> inventoryRecords = inventoryRecordRepository.findByWarehouseId(warehouseId);
        boolean hasStock = inventoryRecords.stream().anyMatch(record -> record.getQuantity() > 0);
        if (hasStock) {
            throw new ValidationException("仓库中仍存在库存记录，无法删除，请先清空库存");
        }
        long referencingOrders = purchaseOrderRepository.countByWarehouseId(warehouseId);
        if (referencingOrders > 0) {
            throw new ValidationException("仓库已被 " + referencingOrders + " 张采购单引用，无法删除，请先处理这些采购单");
        }
        List<ShopWarehouse> shopBindings = shopWarehouseRepository.findByLocalWarehouseId(warehouseId);
        if (!shopBindings.isEmpty()) {
            throw new ValidationException("仓库已绑定 " + shopBindings.size() + " 个店铺仓库，无法删除，请先解除绑定");
        }
        inventoryRecords.forEach(record -> inventoryRecordRepository.delete(record.getInventoryId()));
        warehouseRepository.delete(warehouse.getWarehouseId());
    }

    /**
     * 启用/停用仓库
     */
    @Transactional
    public Warehouse changeWarehouseStatus(String warehouseId, boolean active) {
        Warehouse warehouse = getWarehouse(warehouseId);
        Warehouse updated = warehouse.toBuilder().active(active).build();
        return warehouseRepository.save(updated);
    }

    /**
     * 添加库位
     */
    @Transactional
    public Warehouse addLocation(String warehouseId, String locationCode, String locationType, Integer capacity) {
        if (locationCode == null || locationCode.isBlank()) {
            throw new ValidationException("库位编码不能为空");
        }
        // 库位挂到仓库聚合上，先校验仓库归属（ISS-050）
        requireWarehouse(warehouseId);
        Location location = Location.builder()
                .warehouseId(warehouseId)
                .locationCode(locationCode.trim())
                .locationType(locationType == null || locationType.isBlank() ? "SHELF" : locationType.trim())
                .capacity(capacity == null || capacity <= 0 ? 1 : capacity)
                .occupied(0)
                .active(true)
                .build();
        var warehouse = warehouseManagementService.addLocation(warehouseId, location);
        publishDomainEvents(warehouse);
        return warehouse;
    }

    /**
     * 库存入库
     */
    @Transactional
    public InventoryRecord inventoryIn(String skuCode, String warehouseId, Integer quantity) {
        requireWarehouse(warehouseId);
        if (skuCode == null || skuCode.isBlank()) {
            throw new ValidationException("SKU编码不能为空");
        }
        if (quantity == null || quantity <= 0) {
            throw new ValidationException("入库数量必须大于0");
        }
        var result = inventoryManagementService.inboundInventory(warehouseId, skuCode, skuCode, quantity, null);
        publishDomainEvents(result.warehouse());
        return result.record();
    }

    /**
     * 库存出库
     */
    @Transactional
    public InventoryRecord inventoryOut(String skuCode, String warehouseId, Integer quantity) {
        requireWarehouse(warehouseId);
        if (skuCode == null || skuCode.isBlank()) {
            throw new ValidationException("SKU编码不能为空");
        }
        if (quantity == null || quantity <= 0) {
            throw new ValidationException("出库数量必须大于0");
        }
        var result = inventoryManagementService.outboundInventory(warehouseId, skuCode, quantity);
        publishDomainEvents(result.warehouse());
        return result.record();
    }

    /**
     * 执行库存盘点
     */
    @Transactional
    public List<InventoryRecord> executeInventoryCount(String warehouseId, List<InventoryMovement> movements) {
        requireWarehouse(warehouseId);
        List<InventoryRecord> results = new ArrayList<>();
        if (movements == null || movements.isEmpty()) {
            return results;
        }
        for (InventoryMovement movement : movements) {
            var result = inventoryCountService.countInventory(warehouseId, movement.getProductId(), movement.getQuantity());
            publishDomainEvents(result.warehouse());
            results.add(result.record());
        }
        return results;
    }

    /**
     * 查询仓库库存
     */
    public List<InventoryRecord> getWarehouseInventory(String warehouseId) {
        // 库存记录本身无租户字段，按父级仓库归属收敛（ISS-050）
        requireWarehouse(warehouseId);
        return inventoryRecordRepository.findByWarehouseId(warehouseId);
    }

    /**
     * 根据ID查询仓库
     */
    public Warehouse findWarehouseById(String id) {
        var warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("仓库不存在: " + id));
        TenantAccessGuard.requireOwned("Warehouse", warehouse.getTenantId(), id);
        return warehouse;
    }

    /**
     * 查询所有仓库
     */
    public List<Warehouse> findAllWarehouses() {
        return withinCurrentTenant(warehouseRepository.findAll());
    }

    /**
     * 分页 + 租户过滤查询仓库（tenantId 为空时不过滤）。
     */
    public PageResult<Warehouse> searchWarehouses(String tenantId, int page, int size) {
        return warehouseRepository.search(TenantAccessGuard.scopedTenantId(tenantId), page, size);
    }

    /**
     * 根据仓库编码查询仓库
     */
    public Warehouse findWarehouseByCode(String warehouseCode) {
        var warehouse = warehouseRepository.findByWarehouseCode(warehouseCode)
                .orElseThrow(() -> new EntityNotFoundException("仓库不存在: " + warehouseCode));
        TenantAccessGuard.requireOwned("Warehouse", warehouse.getTenantId(), warehouseCode);
        return warehouse;
    }

    /**
     * 根据仓库ID查询库存
     */
    public List<InventoryRecord> findInventoryByWarehouse(String warehouseId) {
        // 库存记录本身无租户字段，按父级仓库归属收敛（ISS-050）
        requireWarehouse(warehouseId);
        return inventoryRecordRepository.findByWarehouseId(warehouseId);
    }

    /**
     * 库存盘点（单个产品）
     */
    @Transactional
    public InventoryRecord countInventory(String warehouseId, String productId, int actualQuantity) {
        requireWarehouse(warehouseId);
        var result = inventoryCountService.countInventory(warehouseId, productId, actualQuantity);
        publishDomainEvents(result.warehouse());
        return result.record();
    }

    /**
     * 库存移动
     */
    @Transactional
    public InventoryRecord moveInventory(String warehouseId, InventoryMovement movement) {
        if (movement == null) {
            throw new ValidationException("库存移动参数不能为空");
        }
        requireWarehouse(warehouseId);
        // 根据移动类型执行相应的库存操作
        if (movement.getMovementType() == InventoryMovement.MovementType.INBOUND) {
            var result = inventoryManagementService.inboundInventory(
                    warehouseId,
                    movement.getProductId(),
                    movement.getSku(),
                    movement.getQuantity(),
                    null
            );
            publishDomainEvents(result.warehouse());
            return result.record();
        } else if (movement.getMovementType() == InventoryMovement.MovementType.OUTBOUND) {
            var result = inventoryManagementService.outboundInventory(
                    warehouseId,
                    movement.getProductId(),
                    movement.getQuantity()
            );
            publishDomainEvents(result.warehouse());
            return result.record();
        } else if (movement.getMovementType() == InventoryMovement.MovementType.ADJUSTMENT) {
            var result = inventoryManagementService.adjustInventory(
                    warehouseId,
                    movement.getProductId(),
                    movement.getQuantity()
            );
            publishDomainEvents(result.warehouse());
            return result.record();
        } else if (movement.getMovementType() == InventoryMovement.MovementType.TRANSFER) {
            return transferInventory(warehouseId, movement);
        } else {
            throw new ValidationException("不支持的库存移动类型: " + movement.getMovementType());
        }
    }

    /**
     * 库存调拨：源仓库出库 + 目标仓库入库，整体在一个事务内完成
     */
    private InventoryRecord transferInventory(String warehouseId, InventoryMovement movement) {
        String targetWarehouseId = movement.getTargetWarehouseId();
        if (targetWarehouseId == null || targetWarehouseId.isBlank()) {
            throw new ValidationException("库存调拨必须指定目标仓库");
        }
        requireWarehouse(targetWarehouseId);
        if (movement.getQuantity() <= 0) {
            throw new ValidationException("调拨数量必须大于0");
        }
        String productId = movement.getProductId();
        String sku = movement.getSku() != null ? movement.getSku() : productId;
        String productKey = productId != null && !productId.isBlank() ? productId : sku;
        var sourceResult = inventoryManagementService.outboundInventory(warehouseId, productKey, movement.getQuantity());
        var targetResult = inventoryManagementService.inboundInventory(
                targetWarehouseId, productKey, sku, movement.getQuantity(), null);
        publishDomainEvents(sourceResult.warehouse());
        publishDomainEvents(targetResult.warehouse());
        return targetResult.record();
    }

    private Warehouse getWarehouse(String warehouseId) {
        var warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new EntityNotFoundException("仓库不存在: " + warehouseId));
        // 所有按ID加载仓库的写/校验入口都汇聚到这里，统一做归属校验（ISS-050）
        TenantAccessGuard.requireOwned("Warehouse", warehouse.getTenantId(), warehouseId);
        return warehouse;
    }

    /**
     * 列表按登录租户收敛；平台管理员或无租户上下文时不过滤（ISS-050）。
     */
    private List<Warehouse> withinCurrentTenant(List<Warehouse> warehouses) {
        String tenantId = TenantAccessGuard.currentTenantId();
        if (tenantId == null) {
            return warehouses;
        }
        return warehouses.stream()
                .filter(warehouse -> tenantId.equals(warehouse.getTenantId()))
                .toList();
    }

    private void requireWarehouse(String warehouseId) {
        if (warehouseId == null || warehouseId.isBlank()) {
            throw new ValidationException("仓库ID不能为空");
        }
        getWarehouse(warehouseId);
    }

    /**
     * 在当前租户下取第一个仓库（按 ID 升序，稳定），供订单发货等无仓库字段的链路解析出库仓。
     *
     * @param tenantId 租户ID
     * @return 仓库聚合
     * @throws ValidationException 租户下没有可用仓库
     */
    public Warehouse findFirstWarehouseOfTenant(String tenantId) {
        return warehouseRepository.findFirstByTenantId(
                        TenantAccessGuard.resolveWriteTenant(tenantId, DEFAULT_TENANT_ID))
                .orElseThrow(() -> new ValidationException("当前租户下没有可用仓库，请先创建仓库"));
    }

    /**
     * 解析发货出库仓（BR-04）。
     * <p>
     * 订单聚合与订单行都没有出库仓字段，所以只能按 SKU 在租户内择仓。旧口径直接取"租户第一个仓"，
     * 多仓租户下会把扣减落到一个根本不备该 SKU 的仓上，发货因此报
     * {@code ENTITY_NOT_FOUND 库存记录不存在}（冒烟 3.11 实测）。现口径：
     * ① 优先取余量足够且余量最大的仓（同 SKU 多库位按求和计）；
     * ② 没有够的仓时取持有该 SKU 的仓，让"库存不足"按业务语义报出而不是 404；
     * ③ 租户内没有任何仓备该 SKU 时回退到第一个仓，保持原有错误口径可定位。
     * </p>
     *
     * @param tenantId 租户ID（空则按默认租户）
     * @param sku      出库 SKU（同时匹配 productId 列，历史数据两列同值）
     * @param quantity 需出库数量
     * @return 出库仓ID
     */
    public String resolveOutboundWarehouseId(String tenantId, String sku, int quantity) {
        String scopedTenantId = TenantAccessGuard.resolveWriteTenant(tenantId, DEFAULT_TENANT_ID);
        List<Warehouse> warehouses = warehouseRepository.listByTenantId(scopedTenantId);
        if (warehouses.isEmpty()) {
            throw new ValidationException("当前租户下没有可用仓库，请先创建仓库");
        }
        Warehouse largest = null;
        int largestStock = -1;
        Warehouse holder = null;
        for (Warehouse warehouse : warehouses) {
            int stock = stockOf(warehouse, sku);
            if (stock < 0) {
                continue;
            }
            if (holder == null) {
                holder = warehouse;
            }
            if (stock >= quantity && stock > largestStock) {
                largestStock = stock;
                largest = warehouse;
            }
        }
        Warehouse chosen = largest != null ? largest : holder;
        return (chosen != null ? chosen : warehouses.get(0)).getWarehouseId();
    }

    /**
     * 该仓内此 SKU 的可用余量；未备此 SKU 返回 -1（与"备了 0 件"区分开）。
     */
    private int stockOf(Warehouse warehouse, String sku) {
        if (warehouse == null || sku == null || warehouse.getInventoryRecords() == null) {
            return -1;
        }
        List<InventoryRecord> matches = warehouse.getInventoryRecords().stream()
                .filter(record -> record != null
                        && (sku.equals(record.getSku()) || sku.equals(record.getProductId())))
                .toList();
        if (matches.isEmpty()) {
            return -1;
        }
        return matches.stream().mapToInt(InventoryRecord::getQuantity).sum();
    }

    private String resolveTenantId(String tenantId) {
        return TenantAccessGuard.resolveWriteTenant(tenantId, DEFAULT_TENANT_ID);
    }

    private void publishDomainEvents(Warehouse warehouse) {
        if (warehouse != null) {
            domainEventPublisher.publishEventsFrom(warehouse);
        }
    }
}
