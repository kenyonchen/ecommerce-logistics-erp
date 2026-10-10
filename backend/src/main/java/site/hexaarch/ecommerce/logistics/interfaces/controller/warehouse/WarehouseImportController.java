package site.hexaarch.ecommerce.logistics.interfaces.controller.warehouse;

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
import site.hexaarch.ecommerce.logistics.application.service.warehouse.WarehouseImportApplicationService;
import site.hexaarch.ecommerce.logistics.domain.audit.Auditable;
import site.hexaarch.ecommerce.logistics.domain.audit.AuditActions;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.infrastructure.importing.ExcelSourceParser;
import site.hexaarch.ecommerce.logistics.interfaces.common.Result;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.TargetFieldDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.warehouseimport.ShopWarehouseFieldMappingDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.warehouseimport.ShopWarehouseFieldMappingSaveRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.warehouseimport.ShopWarehouseImportJobDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.warehouseimport.ShopWarehouseImportPreviewRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.warehouseimport.ShopWarehouseImportPreviewResponse;
import site.hexaarch.ecommerce.logistics.interfaces.dto.warehouseimport.ShopWarehouseImportRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 第三方电商平台店铺仓库导入控制器。
 * <p>
 * 接口分组：
 * <ul>
 *     <li>数据预览：{@code POST /api/warehouses/import/preview}、{@code POST /api/warehouses/import/upload}</li>
 *     <li>批量导入：{@code POST /api/warehouses/import}</li>
 *     <li>结果反馈：{@code GET /api/warehouses/import/jobs}、{@code GET /api/warehouses/import/jobs/{id}}</li>
 *     <li>字段映射：{@code GET|PUT /api/warehouses/import/field-mappings}</li>
 * </ul>
 * </p>
 *
 * @author kenyon
 */
@Slf4j
@RestController
@RequestMapping("/api/warehouses/import")
@RequiredArgsConstructor
@Tag(name = "店铺仓库导入", description = "第三方电商平台店铺仓库数据获取、字段映射与批量导入")
public class WarehouseImportController {
    private static final String HAS_WAREHOUSE_READ =
            "hasRole('PLATFORM_ADMIN') or hasAuthority('warehouse:read')";
    private static final String HAS_WAREHOUSE_WRITE =
            "hasRole('PLATFORM_ADMIN') or hasAuthority('warehouse:write')";


    private final WarehouseImportApplicationService warehouseImportApplicationService;
    private final ExcelSourceParser excelSourceParser;

    @Operation(summary = "标准目标字段列表", description = "获取可用于字段映射的系统标准店铺仓库字段")
    @PreAuthorize(HAS_WAREHOUSE_READ)
    @GetMapping("/target-fields")
    public Result<List<TargetFieldDto>> targetFields() {
        return Result.success(warehouseImportApplicationService.targetFields());
    }

    @Operation(summary = "字段转换规则列表", description = "获取字段取值转换规则")
    @PreAuthorize(HAS_WAREHOUSE_READ)
    @GetMapping("/transforms")
    public Result<List<TargetFieldDto>> transforms() {
        return Result.success(warehouseImportApplicationService.transforms());
    }

    @Operation(summary = "查询字段映射", description = "查询指定租户与平台已保存的店铺仓库字段映射")
    @PreAuthorize(HAS_WAREHOUSE_READ)
    @GetMapping("/field-mappings")
    public Result<List<ShopWarehouseFieldMappingDto>> getFieldMappings(
            @Parameter(description = "租户ID") @RequestParam(required = false) String tenantId,
            @Parameter(description = "平台名称") @RequestParam String platform) {
        return Result.success(warehouseImportApplicationService.getMappings(tenantId, platform));
    }

    @Operation(summary = "保存字段映射", description = "按租户+平台整体保存店铺仓库字段映射关系")
    @PreAuthorize(HAS_WAREHOUSE_WRITE)
    @Auditable(action = AuditActions.IMPORT_FIELD_MAPPING_UPDATE, label = "保存店铺仓库导入字段映射", targetType = "ShopWarehouseImport")
    @PutMapping("/field-mappings")
    public Result<List<ShopWarehouseFieldMappingDto>> saveFieldMappings(
            @RequestBody ShopWarehouseFieldMappingSaveRequest request) {
        return Result.success(warehouseImportApplicationService.saveMappings(request));
    }

    @Operation(summary = "店铺仓库导入预览", description = "拉取平台店铺仓库或解析传入内容，并完成字段映射，返回待导入数据")
    @PreAuthorize(HAS_WAREHOUSE_WRITE)
    @PostMapping("/preview")
    public Result<ShopWarehouseImportPreviewResponse> preview(@RequestBody ShopWarehouseImportPreviewRequest request) {
        try {
            return Result.success(warehouseImportApplicationService.preview(request));
        } catch (BusinessException e) {
            log.warn("[ShopWarehouseImport] 预览失败: {}", e.getMessage());
            return Result.error(e.getErrorCode(), e.getMessage());
        }
    }

    @Operation(summary = "上传文件导入", description = "上传JSON/CSV文件，mode=preview 仅预览，mode=import 直接执行导入")
    @PreAuthorize(HAS_WAREHOUSE_WRITE)
    @Auditable(action = AuditActions.IMPORT_UPLOAD, label = "上传店铺仓库导入文件", targetType = "ShopWarehouseImportJob")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Object> upload(
            @Parameter(description = "导入文件(JSON/CSV/Excel)") @RequestPart("file") MultipartFile file,
            @Parameter(description = "预览或导入") @RequestParam(required = false, defaultValue = "preview") String mode,
            @Parameter(description = "租户ID") @RequestParam(required = false) String tenantId,
            @Parameter(description = "平台名称") @RequestParam(required = false) String platform,
            @Parameter(description = "平台仓库ID已存在时是否覆盖更新") @RequestParam(required = false) Boolean updateExisting) {
        String content = readContent(file);

        if ("import".equalsIgnoreCase(mode)) {
            ShopWarehouseImportPreviewRequest previewRequest = new ShopWarehouseImportPreviewRequest();
            previewRequest.setTenantId(tenantId);
            previewRequest.setPlatform(platform == null
                    ? WarehouseImportApplicationService.FILE_PLATFORM : platform);
            previewRequest.setContent(content);
            previewRequest.setFileName(file.getOriginalFilename());
            ShopWarehouseImportPreviewResponse previewed = warehouseImportApplicationService.preview(previewRequest);

            ShopWarehouseImportRequest importRequest = new ShopWarehouseImportRequest();
            importRequest.setTenantId(tenantId);
            importRequest.setPlatform(previewed.getPlatform());
            importRequest.setRows(previewed.getRows());
            importRequest.setUpdateExisting(updateExisting == null || updateExisting);
            importRequest.setFileName(file.getOriginalFilename());
            return Result.success(warehouseImportApplicationService.importShopWarehouses(importRequest));
        }

        ShopWarehouseImportPreviewRequest previewRequest = new ShopWarehouseImportPreviewRequest();
        previewRequest.setTenantId(tenantId);
        previewRequest.setPlatform(platform == null
                ? WarehouseImportApplicationService.FILE_PLATFORM : platform);
        previewRequest.setContent(content);
        previewRequest.setFileName(file.getOriginalFilename());
        return Result.success(warehouseImportApplicationService.preview(previewRequest));
    }

    @Operation(summary = "批量导入店铺仓库", description = "执行批量导入，返回导入任务结果（含每行的成功/失败明细）")
    @PreAuthorize(HAS_WAREHOUSE_WRITE)
    @Auditable(action = AuditActions.SHOP_WAREHOUSE_IMPORT_EXECUTE, label = "执行店铺仓库导入", targetType = "ShopWarehouseImportJob")
    @PostMapping
    public Result<ShopWarehouseImportJobDto> importShopWarehouses(@RequestBody ShopWarehouseImportRequest request) {
        try {
            return Result.success(warehouseImportApplicationService.importShopWarehouses(request));
        } catch (BusinessException e) {
            log.warn("[ShopWarehouseImport] 导入失败: {}", e.getMessage());
            return Result.error(e.getErrorCode(), e.getMessage());
        }
    }

    @Operation(summary = "查询导入任务列表", description = "分页查询店铺仓库导入任务，用于结果回溯")
    @PreAuthorize(HAS_WAREHOUSE_READ)
    @GetMapping("/jobs")
    public Result<PageResult<ShopWarehouseImportJobDto>> listJobs(
            @Parameter(description = "租户ID") @RequestParam(required = false) String tenantId,
            @Parameter(description = "页码，从0开始") @RequestParam(required = false, defaultValue = "0") Integer page,
            @Parameter(description = "每页条数") @RequestParam(required = false, defaultValue = "10") Integer size) {
        return Result.success(warehouseImportApplicationService.listJobs(tenantId, page, size));
    }

    @Operation(summary = "查询导入任务详情", description = "含所有失败行的错误明细")
    @PreAuthorize(HAS_WAREHOUSE_READ)
    @GetMapping("/jobs/{jobId}")
    public Result<ShopWarehouseImportJobDto> getJob(
            @Parameter(description = "导入任务ID") @PathVariable String jobId) {
        return Result.success(warehouseImportApplicationService.getJob(jobId));
    }

    @Operation(summary = "下载CSV导入模板", description = "返回标准CSV模板文本内容")
    @PreAuthorize(HAS_WAREHOUSE_READ)
    @GetMapping(value = "/template", produces = "text/csv; charset=utf-8")
    public ResponseEntity<ByteArrayResource> csvTemplate() {
        String template = warehouseImportApplicationService.csvTemplate();
        ByteArrayResource resource = new ByteArrayResource(template.getBytes(StandardCharsets.UTF_8));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=shop_warehouse_import_template.csv")
                .body(resource);
    }

    private String readContent(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException("上传文件为空");
        }
        try {
            if (excelSourceParser.isExcel(file.getOriginalFilename())) {
                return excelSourceParser.toCsv(file.getBytes());
            }
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException(
                    "读取上传文件失败: " + e.getMessage());
        }
    }
}
