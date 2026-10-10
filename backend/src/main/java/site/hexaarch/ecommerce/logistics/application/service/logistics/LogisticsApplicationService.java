package site.hexaarch.ecommerce.logistics.application.service.logistics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import site.hexaarch.ecommerce.logistics.application.service.finance.FinanceApplicationService;
import site.hexaarch.ecommerce.logistics.application.service.order.OrderApplicationService;
import site.hexaarch.ecommerce.logistics.application.service.support.TenantAccessGuard;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.common.exception.EntityNotFoundException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionType;
import site.hexaarch.ecommerce.logistics.domain.logistics.aggregate.LogisticsOrder;
import site.hexaarch.ecommerce.logistics.domain.logistics.entity.LogisticsChannel;
import site.hexaarch.ecommerce.logistics.domain.logistics.repository.LogisticsChannelRepository;
import site.hexaarch.ecommerce.logistics.domain.logistics.repository.LogisticsOrderRepository;
import site.hexaarch.ecommerce.logistics.domain.logistics.service.LogisticsTrackingService;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsFee;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsLabel;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsStatus;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsTrace;
import site.hexaarch.ecommerce.logistics.domain.order.aggregate.Order;
import site.hexaarch.ecommerce.logistics.domain.order.entity.OrderItem;
import site.hexaarch.ecommerce.logistics.domain.order.repository.OrderRepository;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.OrderStatus;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.ShippingAddress;
import site.hexaarch.ecommerce.logistics.domain.product.aggregate.Product;
import site.hexaarch.ecommerce.logistics.domain.product.entity.SKU;
import site.hexaarch.ecommerce.logistics.domain.product.repository.ProductRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.messaging.DomainEventPublisher;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logistics.AddTraceRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logistics.BatchShipItemDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logistics.BatchShipRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logistics.BatchShipResultDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logistics.CreateLogisticsRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logistics.LogisticsOrderDto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;

/**
 * 物流应用服务，协调领域对象完成物流相关的业务操作。
 * <p>
 * 对外统一输出 {@link LogisticsOrderDto}，避免把带领域事件的物流单聚合直接序列化给前端。
 * </p>
 *
 * @author kenyon
 */
@Slf4j
@Service
public class LogisticsApplicationService {

    private static final DateTimeFormatter INPUT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_ONLY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final LogisticsOrderRepository logisticsOrderRepository;
    private final LogisticsChannelRepository logisticsChannelRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final LogisticsTrackingService logisticsTrackingService;
    private final DomainEventPublisher domainEventPublisher;
    private final LogisticsOrderResolver logisticsOrderResolver;
    private final OrderApplicationService orderApplicationService;
    private final FinanceApplicationService financeApplicationService;

    /**
     * 运费入账流水的关联单据类型（BR-10）。与采购应付的 {@code PURCHASE_ORDER} 同构：
     * {@code referenceId} 存物流单 ID，{@code description} 里带人读的物流单号。
     */
    public static final String FREIGHT_REFERENCE_TYPE =
            FinanceApplicationService.LOGISTICS_ORDER_REFERENCE_TYPE;

    /**
     * "货已经交给承运商"的状态集合：一进入这些状态，运费就是既成事实，账上必须有对应应付（BR-10）。
     * {@code PENDING}／{@code LABEL_GENERATED} 只是打单，货没走；{@code CANCELLED} 没有费用发生。
     * {@code FAILED}（配送失败）算在内——承运商已经揽过货，这一趟的钱已经花了。
     * 不用 {@code ordinal()} 判前后：枚举声明次序把 {@code LABEL_GENERATED} 排在 {@code COLLECTED} 之后，
     * 按序号判会把"已打单"误当成已发货。
     */
    private static final Set<LogisticsStatus> SHIPPED_OR_LATER = EnumSet.of(
            LogisticsStatus.COLLECTED, LogisticsStatus.IN_TRANSIT, LogisticsStatus.DELIVERING,
            LogisticsStatus.DELIVERED, LogisticsStatus.FAILED);

    public LogisticsApplicationService(LogisticsOrderRepository logisticsOrderRepository,
                                       LogisticsChannelRepository logisticsChannelRepository,
                                       OrderRepository orderRepository,
                                       ProductRepository productRepository,
                                       LogisticsTrackingService logisticsTrackingService,
                                       DomainEventPublisher domainEventPublisher,
                                       LogisticsOrderResolver logisticsOrderResolver,
                                       OrderApplicationService orderApplicationService,
                                       FinanceApplicationService financeApplicationService) {
        this.logisticsOrderRepository = logisticsOrderRepository;
        this.logisticsChannelRepository = logisticsChannelRepository;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.logisticsTrackingService = logisticsTrackingService;
        this.domainEventPublisher = domainEventPublisher;
        this.logisticsOrderResolver = logisticsOrderResolver;
        this.orderApplicationService = orderApplicationService;
        this.financeApplicationService = financeApplicationService;
    }

    /**
     * 创建物流单（HTTP 入口，订单与渠道均可按ID或编号指定）
     */
    @Transactional
    public LogisticsOrderDto createLogisticsOrder(CreateLogisticsRequest request) {
        String tenantId = resolveTenant(request.getTenantId());
        Order order = logisticsOrderResolver.resolveOrder(tenantId, request.getOrderId(), request.getOrderNumber(),
                null, null);
        // 按ID引用他租户订单建物流单同样越权，先校验订单归属（ISS-050）
        TenantAccessGuard.requireOwned("Order", order.getTenantId(), order.getId());
        LogisticsChannel channel = logisticsOrderResolver.resolveChannel(request.getLogisticsChannelId(),
                request.getChannelCode());

        LogisticsOrder logisticsOrder = LogisticsOrder.create(tenantId, order.getId(), channel.getChannelId(),
                request.getTrackingNumber(), StringUtils.hasText(request.getCarrier())
                        ? request.getCarrier() : channel.getChannelName());
        estimateFreight(logisticsOrder, order, channel);
        // 建单即发货是第四条发货入口，必须与手工揽收、批量发货同语义：订单回写 + 运费入账。
        // 放在 save 之前沿用 ISS-072 的次序——订单状态机或库存拒绝了，就不该留下一条"已揽收但订单没发"的假物流单；
        // 因此待处理（PENDING）订单按状态机不可直发 SHIPPED，这里会明确拒绝而不是静默造出脏数据。
        boolean shipImmediately = Boolean.TRUE.equals(request.getShipImmediately());
        if (shipImmediately) {
            logisticsOrder.markShipped(null);
            shipOrder(order.getId());
        }

        LogisticsOrder saved = logisticsOrderRepository.save(logisticsOrder);
        if (shipImmediately) {
            postFreightExpense(saved);
        }
        domainEventPublisher.publishEventsFrom(saved);
        return toDto(saved);
    }

    /**
     * 创建物流单（消息与流程编排链路使用，渠道标识可为渠道ID或渠道编码）
     */
    @Transactional
    public LogisticsOrder createLogisticsOrder(String tenantId, String orderId, String channelIdentifier) {
        LogisticsChannel channel = logisticsOrderResolver.resolveChannelByIdOrCode(channelIdentifier);
        LogisticsOrder logisticsOrder = LogisticsOrder.create(tenantId, orderId, channel.getChannelId());
        estimateFreight(logisticsOrder, orderRepository.findById(orderId).orElse(null), channel);
        LogisticsOrder savedLogisticsOrder = logisticsOrderRepository.save(logisticsOrder);
        domainEventPublisher.publishEventsFrom(savedLogisticsOrder);
        return savedLogisticsOrder;
    }

    /**
     * 更新物流状态
     */
    @Transactional
    public LogisticsOrderDto updateLogisticsStatus(String logisticsOrderId, LogisticsStatus newStatus) {
        return updateLogisticsStatus(logisticsOrderId, newStatus, null);
    }

    /**
     * 更新物流状态（可附带轨迹描述）
     */
    @Transactional
    public LogisticsOrderDto updateLogisticsStatus(String logisticsOrderId, LogisticsStatus newStatus,
                                                   String description) {
        LogisticsOrder saved = persistStatusChange(getLogisticsOrder(logisticsOrderId), newStatus, description);
        return toDto(saved);
    }

    /**
     * 批量发货：逐单生成物流单并推进订单状态，单条失败不影响其他订单
     */
    public BatchShipResultDto batchShip(BatchShipRequest request) {
        String tenantId = resolveTenant(request.getTenantId());
        boolean syncOrderStatus = request.getUpdateOrderStatus() == null || request.getUpdateOrderStatus();
        List<BatchShipItemDto> items = resolveItems(request);
        BatchShipResultDto result = BatchShipResultDto.of(items.size());

        for (int i = 0; i < items.size(); i++) {
            BatchShipItemDto item = items.get(i);
            int rowNumber = item.getRowNumber() == null ? i + 1 : item.getRowNumber();
            String orderLabel = firstText(item.getOrderNumber(), item.getOrderId(), item.getExternalOrderId());
            try {
                shipSingle(tenantId, request, item, result, syncOrderStatus);
            } catch (Exception e) {
                log.warn("[BatchShip] 第{}行发货失败 order={}: {}", rowNumber, orderLabel, e.getMessage());
                result.recordFailure(rowNumber, orderLabel, normalizeErrorMessage(e));
            }
        }
        result.buildMessage();
        return result;
    }

    /**
     * 登记物流轨迹
     */
    @Transactional
    public LogisticsOrderDto addTrace(String logisticsOrderId, AddTraceRequest request) {
        LogisticsOrder logisticsOrder = getLogisticsOrder(logisticsOrderId);
        String description = request == null ? null : request.getDescription();
        if (!StringUtils.hasText(description)) {
            throw new ValidationException("轨迹描述不能为空");
        }
        LocalDateTime time = parseDateTime(request.getTime(), false);
        String status = StringUtils.hasText(request.getStatus()) ? request.getStatus().trim().toUpperCase() : null;
        logisticsOrder.appendTrace(new LogisticsTrace(time == null ? LocalDateTime.now() : time, status, description,
                request.getLocation()));
        LogisticsOrder saved = logisticsOrderRepository.save(logisticsOrder);
        domainEventPublisher.publishEventsFrom(saved);
        return toDto(saved);
    }

    /**
     * 生成物流标签（面单）
     */
    @Transactional
    public LogisticsOrderDto generateLabel(String logisticsOrderId) {
        LogisticsOrder logisticsOrder = getLogisticsOrder(logisticsOrderId);
        if (logisticsOrder.getLogisticsLabel() != null) {
            return toDto(logisticsOrder);
        }
        Order order = orderRepository.findById(logisticsOrder.getOrderId()).orElse(null);
        LogisticsLabel label = LogisticsLabel.builder()
                .trackingNumber(logisticsOrder.getTrackingNumber())
                .recipientInfo(buildRecipientInfo(order))
                .packageInfo(LogisticsLabel.PackageInfo.builder().build())
                .labelFormat("PDF")
                .build();
        logisticsOrder.generateLabel(label);
        LogisticsOrder saved = logisticsOrderRepository.save(logisticsOrder);
        domainEventPublisher.publishEventsFrom(saved);
        return toDto(saved);
    }

    /**
     * 标记发货（揽收）。
     * <p>
     * 揽收即发货：订单必须同步推进到 SHIPPED，出库（BR-04）随那次状态迁移发生一次。
     * 订单侧的任何拒绝（非法流转、库存不足）都在这里向上抛出，物流单因此不落库，
     * 避免出现"物流已揽收、订单未发货、库存未扣"的三方不一致（ISS-072）。
     * </p>
     */
    @Transactional
    public LogisticsOrderDto ship(String logisticsOrderId, String shippedAt, String trackingNumber) {
        LogisticsOrder logisticsOrder = getLogisticsOrder(logisticsOrderId);
        logisticsOrder.assignTrackingNumber(trackingNumber);
        LocalDateTime shipped = parseDateTime(shippedAt, false);
        logisticsOrder.markShipped(shipped == null ? LocalDateTime.now() : shipped);
        shipOrder(logisticsOrder.getOrderId());
        LogisticsOrder saved = logisticsOrderRepository.save(logisticsOrder);
        postFreightExpense(saved);
        domainEventPublisher.publishEventsFrom(saved);
        return toDto(saved);
    }

    /**
     * 取消物流单（已被揽收/运输/送达的物流单不可取消）
     */
    @Transactional
    public LogisticsOrderDto cancel(String logisticsOrderId) {
        LogisticsOrder logisticsOrder = getLogisticsOrder(logisticsOrderId);
        LogisticsStatus current = logisticsOrder.getLogisticsStatus();
        if (current != null && (current.isCollected() || current.isInTransit() || current.isDelivered())) {
            throw new ValidationException("物流单已[" + current.getDescription() + "]，无法取消");
        }
        logisticsOrder.updateStatus(LogisticsStatus.CANCELLED, "手工取消");
        LogisticsOrder saved = logisticsOrderRepository.save(logisticsOrder);
        domainEventPublisher.publishEventsFrom(saved);
        return toDto(saved);
    }

    /**
     * 刷新物流跟踪状态。
     * <p>
     * 当前未接入物流商实时查询接口，此处仅记录一次「查询」轨迹并回传现有状态；
     * 平台侧的物流状态推进由「物流导入」链路负责。
     * </p>
     */
    public LogisticsOrderDto track(String logisticsOrderId) {
        LogisticsOrder logisticsOrder = getLogisticsOrder(logisticsOrderId);
        LogisticsStatus latest = logisticsTrackingService.trackLogisticsStatus(logisticsOrderId);
        logisticsOrder.appendTrace(LogisticsTrace.of(LocalDateTime.now(),
                latest == null ? null : latest.name(), "已查询物流跟踪信息", null));
        LogisticsOrder saved = logisticsOrderRepository.save(logisticsOrder);
        domainEventPublisher.publishEventsFrom(saved);
        return toDto(saved);
    }

    /**
     * 查询物流单详情
     */
    public LogisticsOrderDto getLogisticsOrderDto(String logisticsOrderId) {
        return toDto(getLogisticsOrder(logisticsOrderId));
    }

    /**
     * 查找物流单
     */
    public LogisticsOrder getLogisticsOrder(String logisticsOrderId) {
        LogisticsOrder logisticsOrder = logisticsOrderRepository.findById(logisticsOrderId)
                .orElseThrow(() -> new EntityNotFoundException("物流单不存在: " + logisticsOrderId));
        // 全部按ID读写物流单的入口都走这里，统一校验归属（ISS-050）
        TenantAccessGuard.requireOwned("LogisticsOrder", logisticsOrder.getTenantId(), logisticsOrderId);
        return logisticsOrder;
    }

    /**
     * 根据订单ID查找物流单
     */
    public List<LogisticsOrderDto> findLogisticsOrderByOrderId(String orderId) {
        List<LogisticsOrder> orders = withinCurrentTenant(logisticsOrderRepository.findByOrderId(orderId));
        List<LogisticsOrderDto> dtos = new ArrayList<>(orders.size());
        for (LogisticsOrder order : orders) {
            dtos.add(toDto(order));
        }
        return dtos;
    }

    /**
     * 条件分页查询物流单
     */
    public PageResult<LogisticsOrderDto> search(String tenantId, String trackingNumber, String orderId,
                                                String channelId, String status, String keyword, String startDate,
                                                String endDate, int page, int size) {
        LogisticsStatus logisticsStatus = null;
        if (StringUtils.hasText(status)) {
            try {
                logisticsStatus = LogisticsStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ValidationException("不支持的物流状态: " + status);
            }
        }
        String resolvedChannelId = StringUtils.hasText(channelId)
                ? logisticsOrderResolver.resolveChannelByIdOrCode(channelId).getChannelId() : null;
        return logisticsOrderRepository
                // 列表查询按登录租户收敛：未传租户即取登录租户，显式传别家租户直接拒绝（ISS-050）
                .search(TenantAccessGuard.scopedTenantId(tenantId), trackingNumber, orderId, resolvedChannelId,
                        logisticsStatus, keyword, startDate, endDate, Math.max(page, 0),
                        Math.min(Math.max(size, 1), 200))
                .map(this::toDto);
    }

    /**
     * 查找所有物流单
     */
    public List<LogisticsOrderDto> findAllLogisticsOrders() {
        List<LogisticsOrder> orders = withinCurrentTenant(logisticsOrderRepository.findAll());
        List<LogisticsOrderDto> dtos = new ArrayList<>(orders.size());
        for (LogisticsOrder order : orders) {
            dtos.add(toDto(order));
        }
        return dtos;
    }

    /**
     * 删除物流单。
     * <p>
     * ISS-139 / R-59：删单前必须把它派生的运费流水一起撤掉。修复前这里只删单据行，
     * 台账里那条 {@code expense/pending} 原样留着，关联单据号指向一张已经不存在的物流单。
     * 已确认成功（SUCCESS）的流水会挡住删除并回业务原因 —— 已过账的分录不能凭空消失。
     * </p>
     */
    @Transactional
    public void delete(String logisticsOrderId) {
        LogisticsOrder logisticsOrder = getLogisticsOrder(logisticsOrderId);
        financeApplicationService.reverseTransactionsForDocument(logisticsOrder.getLogisticsOrderId(),
                FREIGHT_REFERENCE_TYPE, "物流单 " + logisticsOrder.getLogisticsOrderNumber());
        logisticsOrderRepository.delete(logisticsOrderId);
    }

    private void shipSingle(String tenantId, BatchShipRequest request, BatchShipItemDto item,
                            BatchShipResultDto result, boolean syncOrderStatus) {
        Order order = logisticsOrderResolver.resolveOrder(tenantId, item.getOrderId(), item.getOrderNumber(),
                item.getExternalOrderId(), null);
        // 批量发货会推进订单状态，先校验订单归属，避免跨租户发货（ISS-050）
        TenantAccessGuard.requireOwned("Order", order.getTenantId(), order.getId());
        LogisticsChannel channel = logisticsOrderResolver.resolveChannel(
                firstText(item.getLogisticsChannelId(), request.getLogisticsChannelId()),
                firstText(item.getChannelCode(), request.getChannelCode()));

        LogisticsOrder pending = null;
        for (LogisticsOrder existing : logisticsOrderRepository.findByOrderId(order.getId())) {
            LogisticsStatus status = existing.getLogisticsStatus();
            if (status != null && status != LogisticsStatus.PENDING) {
                result.recordSkipped(order.getOrderNumber(),
                        "订单已存在物流单[" + existing.getTrackingNumber() + "]，状态为[" + status.getDescription()
                                + "]，已跳过");
                return;
            }
            pending = existing;
        }

        LocalDateTime shippedAt = parseDateTime(item.getShippedAt(), false);
        LogisticsOrder logisticsOrder = pending != null
                ? pending
                : LogisticsOrder.create(tenantId, order.getId(), channel.getChannelId(), item.getTrackingNumber(),
                firstText(item.getCarrier(), channel.getChannelName()));
        estimateFreight(logisticsOrder, order, channel);
        logisticsOrder.assignTrackingNumber(item.getTrackingNumber());
        logisticsOrder.markShipped(shippedAt == null ? LocalDateTime.now() : shippedAt);

        // 订单状态推进与出库放在物流单落库之前：状态机或库存拒绝了这一行，
        // 物流单不该留下一条"已揽收但订单没发"的假记录（ISS-072）。
        if (syncOrderStatus) {
            shipOrder(order.getId());
        }
        LogisticsOrder saved = logisticsOrderRepository.save(logisticsOrder);
        postFreightExpense(saved);
        domainEventPublisher.publishEventsFrom(saved);
        result.recordSuccess(saved.getLogisticsOrderId());
    }

    /**
     * 发货的订单侧动作：把订单推进到 SHIPPED。
     * <p>
     * 出库（BR-04）唯一挂在这一步（见 {@link OrderApplicationService#updateOrderStatus}），
     * 三条发货入口 —— 订单状态端点、批量发货、物流单手工揽收 —— 都经此处回写，
     * 因此"订单进入 SHIPPED ⇒ 库存恰好扣一次"对任何入口成立；已 SHIPPED 的订单重复调用幂等不重扣。
     * </p>
     */
    private void shipOrder(String orderId) {
        orderApplicationService.updateOrderStatus(orderId, OrderStatus.SHIPPED);
    }

    private LogisticsOrder persistStatusChange(LogisticsOrder logisticsOrder, LogisticsStatus newStatus,
                                               String description) {
        if (newStatus == null) {
            throw new ValidationException("物流状态不能为空");
        }
        logisticsOrder.updateStatus(newStatus, description);
        LogisticsOrder saved = logisticsOrderRepository.save(logisticsOrder);
        // 状态推进端点同样能把单据落到"已揽收及之后"，那里运费已是既成事实，
        // 不能因为它不叫 ship 就漏记（R-58）。入账自身幂等，同单第二次推进状态不会记两笔。
        if (SHIPPED_OR_LATER.contains(newStatus)) {
            postFreightExpense(saved);
        }
        domainEventPublisher.publishEventsFrom(saved);
        return saved;
    }

    private List<BatchShipItemDto> resolveItems(BatchShipRequest request) {
        List<BatchShipItemDto> items = new ArrayList<>();
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            items.addAll(request.getItems());
            return items;
        }
        if (request.getOrderIds() != null) {
            for (String orderId : request.getOrderIds()) {
                if (!StringUtils.hasText(orderId)) {
                    continue;
                }
                BatchShipItemDto item = new BatchShipItemDto();
                item.setOrderId(orderId.trim());
                items.add(item);
            }
        }
        if (items.isEmpty()) {
            throw new ValidationException("批量发货明细为空，请提供 items 或 orderIds");
        }
        return items;
    }

    /**
     * 按渠道计费口径为新建的物流单估一次运费并落库（ISS-137 / R-55）。
     * <p>
     * 计费点只有「创建物流单」这一处：发货、打单、状态推进都不重算——渠道改价不该拖动历史运费。
     * 物流单已带费用（平台导入回传的实际运费）时原样保留。
     * 预估不做国家校验：渠道已由操作者选定，{@code supportsCountry} 属于渠道配置校验，
     * 不因新增计费动作而给建单多开一条拒绝路径。
     * </p>
     */
    private void estimateFreight(LogisticsOrder logisticsOrder, Order order, LogisticsChannel channel) {
        if (logisticsOrder.getLogisticsFee() != null) {
            return;
        }
        logisticsOrder.setLogisticsFee(channel.estimateFee(totalWeightInGrams(order)));
    }

    /**
     * 运费入账（BR-10）：物流单揽收入库后，把<b>创建时已落库</b>的那笔运费记成一条财务应付流水。
     * <p>
     * 三条口径按 R-58 定标：
     * <ul>
     *   <li><b>时点</b>＝物流单已揽收并落库之后，不是建单时（建单只是预估，货没交出去就不该有费用），
     *       也不是送达时（本系统没有回传的签收事实，等到签收等于永远不入账）；</li>
     *   <li><b>金额</b>只读 {@code logistics_fees} 里已有的 {@code totalFee}，绝不在这里重算——
     *       渠道改价不能拖动已发生的费用（R-55 同一条口径）；</li>
     *   <li><b>幂等</b>按「同一张物流单只有一条 EXPENSE」判定（BR-03），重复发货、
     *       发货后再推进状态都不会记第二笔。</li>
     * </ul>
     * 入账的入口与"发货"的入口一一对应：{@link #ship} 手工揽收、{@link #batchShip} 批量发货、
     * {@link #createLogisticsOrder(CreateLogisticsRequest)} 的建单即发货，以及
     * {@link #updateLogisticsStatus} 把单据推进到 {@code SHIPPED_OR_LATER} 里的任一状态。
     * 按 ID 编排建单重载 {@code createLogisticsOrder(tenantId, orderId, channelIdentifier)} 只建单不发货，
     * 因此<b>不</b>入账——它是这条规则唯一必须为假的调用点（见 {@code FreightPostingGuardTest}）。
     * <p>
     * 修复前落下的历史物流单没有费用行，因此<b>不入账</b>：宁可台账少一行，
     * 也不凭猜测给一笔从没算过的运费编个金额。零金额同样跳过（聚合校验金额不能为 0）。
     * </p>
     */
    private void postFreightExpense(LogisticsOrder saved) {
        LogisticsFee fee = saved.getLogisticsFee();
        // LogisticsFee 构造器保证 totalFee 非空（缺省为首重+续重+体积费），这里只需挡"无费用行"和"零/负费用"
        if (fee == null || fee.getTotalFee().signum() <= 0) {
            return;
        }
        if (financeApplicationService.existsFinancialTransaction(
                saved.getLogisticsOrderId(), FREIGHT_REFERENCE_TYPE, TransactionType.EXPENSE)) {
            return;
        }
        // 事件自动建的物流单没有 carrier（只有渠道 ID），未知就不写进备注——台账是给人读的，
        // 出现字面量 null 等于把内部空值泄成业务文案
        String carrier = saved.getCarrier();
        String description = "物流单 " + saved.getLogisticsOrderNumber() + " 运费入账"
                + "（" + (carrier == null || carrier.isBlank() ? "" : "承运商 " + carrier + "：")
                + "首重 " + fee.getBaseFee().toPlainString()
                + " + 续重 " + fee.getWeightFee().toPlainString()
                + (fee.getVolumeFee().signum() == 0
                        ? "" : " + 体积费 " + fee.getVolumeFee().toPlainString())
                + "）";
        financeApplicationService.createFinancialTransaction(TransactionType.EXPENSE, fee.getTotalFee(),
                fee.getCurrency(), saved.getLogisticsOrderId(), FREIGHT_REFERENCE_TYPE,
                description, saved.getTenantId());
        log.info("[Logistics] 物流单 {} 运费入账 {} {}（关联 {}）", saved.getLogisticsOrderNumber(),
                fee.getCurrency(), fee.getTotalFee(), FREIGHT_REFERENCE_TYPE);
    }

    /**
     * 计费重＝订单项 SKU 克重 × 数量。订单项按 {@code sku_code} 关联 SKU，
     * {@code skus.weight} 与渠道 {@code price_per_gram} 同为克。
     * <p>
     * SKU 未维护重量（product BR-06）时按 0 克计，即只收首重：商品资料残缺不能成为拒建物流单的理由，
     * 但也不能拿面单重量顶替——{@code logistics_labels.package_weight} 的单位是千克，用它当克会把运费缩小 1000 倍。
     * </p>
     */
    private long totalWeightInGrams(Order order) {
        if (order == null || order.getOrderItems() == null) {
            return 0L;
        }
        long grams = 0L;
        for (OrderItem item : order.getOrderItems()) {
            if (item == null || !StringUtils.hasText(item.getSku())) {
                continue;
            }
            grams += (long) skuWeightInGrams(item.getSku()) * Math.max(item.getQuantity(), 0);
        }
        return grams;
    }

    private int skuWeightInGrams(String skuCode) {
        return productRepository.findBySkuCode(skuCode)
                .flatMap(product -> product.getSkus() == null
                        ? Optional.<SKU>empty()
                        : product.getSkus().stream()
                        .filter(sku -> sku != null && skuCode.equals(sku.getSkuCode()))
                        .findFirst())
                .map(SKU::getWeightInGrams)
                .orElse(0);
    }

    private LogisticsLabel.RecipientInfo buildRecipientInfo(Order order) {
        if (order == null) {
            return null;
        }
        ShippingAddress address = order.getShippingAddress();
        if (address == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (String part : List.of(address.getCountry(), address.getProvince(), address.getCity(),
                address.getStreet())) {
            if (StringUtils.hasText(part)) {
                parts.add(part);
            }
        }
        return LogisticsLabel.RecipientInfo.builder()
                .name(address.getRecipient())
                .phone(address.getPhone())
                .address(String.join(" ", parts))
                .build();
    }

    private LogisticsOrderDto toDto(LogisticsOrder logisticsOrder) {
        LogisticsOrderDto dto = LogisticsOrderDto.from(logisticsOrder);
        dto.setCreatedAt(format(logisticsOrder.getCreatedAt()));
        dto.setUpdatedAt(format(logisticsOrder.getUpdatedAt()));
        dto.setShippedAt(format(logisticsOrder.getShippedAt()));
        dto.setEstimatedArrivalAt(format(logisticsOrder.getEstimatedArrivalAt()));
        dto.setDeliveredAt(format(logisticsOrder.getDeliveredAt()));

        if (StringUtils.hasText(logisticsOrder.getOrderId())) {
            orderRepository.findById(logisticsOrder.getOrderId())
                    .ifPresent(order -> dto.setOrderNumber(order.getOrderNumber()));
        }
        if (StringUtils.hasText(logisticsOrder.getLogisticsChannelId())) {
            logisticsChannelRepository.findById(logisticsOrder.getLogisticsChannelId())
                    .ifPresent(channel -> {
                        dto.setChannelCode(channel.getChannelCode());
                        dto.setChannelName(channel.getChannelName());
                    });
        }
        return dto;
    }

    private String resolveTenant(String tenantId) {
        return TenantAccessGuard.resolveWriteTenant(tenantId, LogisticsImportApplicationService.DEFAULT_TENANT_ID);
    }

    /**
     * 列表按登录租户收敛；平台管理员或无租户上下文时不过滤（ISS-050）。
     */
    private List<LogisticsOrder> withinCurrentTenant(List<LogisticsOrder> orders) {
        String tenantId = TenantAccessGuard.currentTenantId();
        if (tenantId == null) {
            return orders;
        }
        return orders.stream()
                .filter(order -> tenantId.equals(order.getTenantId()))
                .toList();
    }

    /**
     * 兼容 yyyy-MM-dd HH:mm:ss 与 yyyy-MM-dd 两种入参格式
     *
     * @param required 为 true 时格式非法直接报错，为 false 时静默返回 null
     */
    private LocalDateTime parseDateTime(String text, boolean required) {
        if (!StringUtils.hasText(text)) {
            if (required) {
                throw new ValidationException("时间不能为空（格式 yyyy-MM-dd HH:mm:ss）");
            }
            return null;
        }
        String trimmed = text.trim();
        try {
            return LocalDateTime.parse(trimmed, INPUT_FORMATTER);
        } catch (Exception ignored) {
            // 继续尝试仅日期格式
        }
        try {
            return java.time.LocalDate.parse(trimmed, DATE_ONLY_FORMATTER).atStartOfDay();
        } catch (Exception ignored) {
            if (required) {
                throw new ValidationException("时间格式不正确（应为 yyyy-MM-dd HH:mm:ss）: " + text);
            }
            return null;
        }
    }

    private String format(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(INPUT_FORMATTER);
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String normalizeErrorMessage(Exception e) {
        String message = e.getMessage();
        if (!StringUtils.hasText(message)) {
            message = e.getClass().getSimpleName();
        }
        return message.length() > 300 ? message.substring(0, 300) : message;
    }
}
