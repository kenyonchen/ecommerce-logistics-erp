package site.hexaarch.ecommerce.logistics.interfaces.controller.order;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import site.hexaarch.ecommerce.logistics.application.service.order.OrderImportApplicationService;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;
import site.hexaarch.ecommerce.logistics.infrastructure.importing.ExcelSourceParser;
import site.hexaarch.ecommerce.logistics.interfaces.common.Result;
import site.hexaarch.ecommerce.logistics.interfaces.dto.orderimport.OrderImportJobDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.orderimport.OrderImportPreviewRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.orderimport.OrderImportPreviewResponse;
import site.hexaarch.ecommerce.logistics.interfaces.dto.orderimport.OrderImportRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.FieldMappingDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.FieldMappingSaveRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.TargetFieldDto;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 第三方电商平台订单导入控制器。
 * <p>
 * 接口分组：
 * <ul>
 *     <li>数据获取与预览：{@code POST /api/orders/import/preview}、{@code POST /api/orders/import/upload}</li>
 *     <li>批量导入：{@code POST /api/orders/import}</li>
 *     <li>结果反馈：{@code GET /api/orders/import/jobs}、{@code GET /api/orders/import/jobs/{id}}</li>
 *     <li>字段映射：{@code GET|PUT /api/orders/import/field-mappings}</li>
 * </ul>
 * </p>
 *
 * @author kenyon
 */
@Slf4j
@RestController
@RequestMapping("/api/orders/import")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN') or hasAuthority('order:import')")
@Tag(name = "订单导入", description = "第三方电商平台订单数据获取、字段映射与批量导入")
public class OrderImportController {

    private final OrderImportApplicationService orderImportApplicationService;
    private final ExcelSourceParser excelSourceParser;

    @Operation(summary = "标准目标字段列表", description = "获取可用于字段映射的系统标准订单字段")
    @GetMapping("/target-fields")
    public Result<List<TargetFieldDto>> targetFields() {
        return Result.success(orderImportApplicationService.targetFields());
    }

    @Operation(summary = "字段转换规则列表", description = "获取字段取值转换规则")
    @GetMapping("/transforms")
    public Result<List<TargetFieldDto>> transforms() {
        return Result.success(orderImportApplicationService.transforms());
    }

    @Operation(summary = "订单状态字典", description = "导入时可映射的订单状态")
    @GetMapping("/order-statuses")
    public Result<List<TargetFieldDto>> orderStatuses() {
        return Result.success(orderImportApplicationService.orderStatuses());
    }

    @Operation(summary = "查询字段映射", description = "查询指定租户与平台已保存的字段映射")
    @GetMapping("/field-mappings")
    public Result<List<FieldMappingDto>> getFieldMappings(
            @Parameter(description = "租户ID") @RequestParam(required = false) String tenantId,
            @Parameter(description = "平台名称") @RequestParam String platform) {
        return Result.success(orderImportApplicationService.getMappings(tenantId, platform));
    }

    @Operation(summary = "保存字段映射", description = "按租户+平台整体保存字段映射关系")
    @PutMapping("/field-mappings")
    public Result<List<FieldMappingDto>> saveFieldMappings(@RequestBody FieldMappingSaveRequest request) {
        return Result.success(orderImportApplicationService.saveMappings(request));
    }

    @Operation(summary = "订单导入预览", description = "拉取平台订单或解析传入内容，并完成字段映射，返回待导入数据")
    @PostMapping("/preview")
    public Result<OrderImportPreviewResponse> preview(@RequestBody OrderImportPreviewRequest request) {
        try {
            return Result.success(orderImportApplicationService.preview(request));
        } catch (BusinessException e) {
            log.warn("[OrderImport] 预览失败: {}", e.getMessage());
            return Result.error(e.getErrorCode(), e.getMessage());
        }
    }

    @Operation(summary = "上传文件导入", description = "上传JSON/CSV/Excel(xlsx)文件，mode=preview 仅预览，mode=import 直接执行导入")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Object> upload(
            @Parameter(description = "导入文件(JSON/CSV)") @RequestPart("file") MultipartFile file,
            @Parameter(description = "预览或导入") @RequestParam(required = false, defaultValue = "preview") String mode,
            @Parameter(description = "租户ID") @RequestParam(required = false) String tenantId,
            @Parameter(description = "平台名称") @RequestParam(required = false) String platform,
            @Parameter(description = "平台订单号已存在时是否覆盖更新") @RequestParam(required = false) Boolean updateExisting) {
        String content = readContent(file);
        String resolvedPlatform = platform == null ? OrderImportApplicationService.FILE_PLATFORM : platform;

        if ("import".equalsIgnoreCase(mode)) {
            OrderImportPreviewRequest previewRequest = new OrderImportPreviewRequest();
            previewRequest.setTenantId(tenantId);
            previewRequest.setPlatform(resolvedPlatform);
            previewRequest.setContent(content);
            previewRequest.setFileName(file.getOriginalFilename());
            OrderImportPreviewResponse previewed = orderImportApplicationService.preview(previewRequest);

            OrderImportRequest importRequest = new OrderImportRequest();
            importRequest.setTenantId(tenantId);
            importRequest.setPlatform(previewed.getPlatform());
            importRequest.setRows(previewed.getRows());
            importRequest.setUpdateExisting(updateExisting == null || updateExisting);
            importRequest.setFileName(file.getOriginalFilename());
            return Result.success(orderImportApplicationService.importOrders(importRequest));
        }

        OrderImportPreviewRequest previewRequest = new OrderImportPreviewRequest();
        previewRequest.setTenantId(tenantId);
        previewRequest.setPlatform(resolvedPlatform);
        previewRequest.setContent(content);
        previewRequest.setFileName(file.getOriginalFilename());
        return Result.success(orderImportApplicationService.preview(previewRequest));
    }

    @Operation(summary = "批量导入订单", description = "执行批量导入，返回导入任务结果（含每行的成功/失败明细）")
    @PostMapping
    public Result<OrderImportJobDto> importOrders(@RequestBody OrderImportRequest request) {
        try {
            return Result.success(orderImportApplicationService.importOrders(request));
        } catch (BusinessException e) {
            log.warn("[OrderImport] 导入失败: {}", e.getMessage());
            return Result.error(e.getErrorCode(), e.getMessage());
        }
    }

    @Operation(summary = "查询导入任务列表", description = "分页查询导入任务，用于结果回溯")
    @GetMapping("/jobs")
    public Result<PageResult<OrderImportJobDto>> listJobs(
            @Parameter(description = "租户ID") @RequestParam(required = false) String tenantId,
            @Parameter(description = "页码，从0开始") @RequestParam(required = false, defaultValue = "0") Integer page,
            @Parameter(description = "每页条数") @RequestParam(required = false, defaultValue = "10") Integer size) {
        return Result.success(orderImportApplicationService.listJobs(tenantId,
                page == null ? 0 : page, size == null ? 10 : size));
    }

    @Operation(summary = "查询导入任务详情", description = "含所有失败行的错误明细")
    @GetMapping("/jobs/{jobId}")
    public Result<OrderImportJobDto> getJob(@Parameter(description = "导入任务ID") @PathVariable String jobId) {
        return Result.success(orderImportApplicationService.getJob(jobId));
    }

    @Operation(summary = "下载CSV导入模板", description = "返回标准CSV模板文本内容")
    @GetMapping(value = "/template", produces = "text/csv; charset=utf-8")
    public ResponseEntity<ByteArrayResource> csvTemplate() {
        String template = orderImportApplicationService.csvTemplate();
        ByteArrayResource resource = new ByteArrayResource(template.getBytes(StandardCharsets.UTF_8));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=order_import_template.csv")
                .body(resource);
    }

    private String readContent(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("上传文件为空");
        }
        try {
            if (excelSourceParser.isExcel(file.getOriginalFilename())) {
                // Excel先转换为CSV，后续字段映射/预览/导入流程与CSV完全一致
                return excelSourceParser.toCsv(file.getBytes());
            }
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ValidationException("读取上传文件失败: " + e.getMessage());
        }
    }
}
