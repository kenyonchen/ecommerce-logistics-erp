package site.hexaarch.ecommerce.logistics.interfaces.controller.logistics;

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
import site.hexaarch.ecommerce.logistics.application.service.logistics.LogisticsImportApplicationService;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;
import site.hexaarch.ecommerce.logistics.infrastructure.importing.ExcelSourceParser;
import site.hexaarch.ecommerce.logistics.interfaces.common.Result;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logisticsimport.LogisticsImportJobDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logisticsimport.LogisticsImportPreviewRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logisticsimport.LogisticsImportPreviewResponse;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logisticsimport.LogisticsImportRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.FieldMappingDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.FieldMappingSaveRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.TargetFieldDto;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 第三方电商平台物流信息导入控制器。
 * <p>
 * 接口分组：
 * <ul>
 *     <li>数据获取与预览：{@code POST /api/logistics/import/preview}、{@code POST /api/logistics/import/upload}</li>
 *     <li>平台直接同步：{@code POST /api/logistics/import/sync}</li>
 *     <li>批量导入：{@code POST /api/logistics/import}</li>
 *     <li>结果反馈：{@code GET /api/logistics/import/jobs}、{@code GET /api/logistics/import/jobs/{id}}</li>
 *     <li>字段映射：{@code GET|PUT /api/logistics/import/field-mappings}</li>
 * </ul>
 * </p>
 *
 * @author kenyon
 */
@Slf4j
@RestController
@RequestMapping("/api/logistics/import")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN') or hasAuthority('logistics:import')")
@Tag(name = "物流导入", description = "第三方电商平台物流数据获取、字段映射与批量导入")
public class LogisticsImportController {

    private final LogisticsImportApplicationService logisticsImportApplicationService;
    private final ExcelSourceParser excelSourceParser;

    @Operation(summary = "标准目标字段列表", description = "获取可用于字段映射的系统标准物流字段")
    @GetMapping("/target-fields")
    public Result<List<TargetFieldDto>> targetFields() {
        return Result.success(logisticsImportApplicationService.targetFields());
    }

    @Operation(summary = "字段转换规则列表", description = "获取字段取值转换规则")
    @GetMapping("/transforms")
    public Result<List<TargetFieldDto>> transforms() {
        return Result.success(logisticsImportApplicationService.transforms());
    }

    @Operation(summary = "物流状态字典", description = "导入时可映射的物流状态")
    @GetMapping("/logistics-statuses")
    public Result<List<TargetFieldDto>> logisticsStatuses() {
        return Result.success(logisticsImportApplicationService.logisticsStatuses());
    }

    @Operation(summary = "查询字段映射", description = "查询指定租户与平台已保存的字段映射")
    @GetMapping("/field-mappings")
    public Result<List<FieldMappingDto>> getFieldMappings(
            @Parameter(description = "租户ID") @RequestParam(required = false) String tenantId,
            @Parameter(description = "平台名称") @RequestParam String platform) {
        return Result.success(logisticsImportApplicationService.getMappings(tenantId, platform));
    }

    @Operation(summary = "保存字段映射", description = "按租户+平台整体保存字段映射关系")
    @PutMapping("/field-mappings")
    public Result<List<FieldMappingDto>> saveFieldMappings(@RequestBody FieldMappingSaveRequest request) {
        return Result.success(logisticsImportApplicationService.saveMappings(request));
    }

    @Operation(summary = "物流导入预览", description = "拉取平台物流数据或解析传入内容，完成字段映射并返回待导入数据")
    @PostMapping("/preview")
    public Result<LogisticsImportPreviewResponse> preview(@RequestBody LogisticsImportPreviewRequest request) {
        try {
            return Result.success(logisticsImportApplicationService.preview(request));
        } catch (BusinessException e) {
            log.warn("[LogisticsImport] 预览失败: {}", e.getMessage());
            return Result.error(e.getErrorCode(), e.getMessage());
        }
    }

    @Operation(summary = "上传文件导入", description = "上传JSON/CSV/Excel(xlsx)文件，mode=preview 仅预览，mode=import 直接执行导入")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Object> upload(
            @Parameter(description = "导入文件(JSON/CSV/Excel)") @RequestPart("file") MultipartFile file,
            @Parameter(description = "预览或导入") @RequestParam(required = false, defaultValue = "preview") String mode,
            @Parameter(description = "租户ID") @RequestParam(required = false) String tenantId,
            @Parameter(description = "平台名称") @RequestParam(required = false) String platform,
            @Parameter(description = "平台物流单号已存在时是否覆盖更新") @RequestParam(required = false)
            Boolean updateExisting) {
        String content = readContent(file);
        String resolvedPlatform = platform == null
                ? LogisticsImportApplicationService.FILE_PLATFORM : platform;

        LogisticsImportPreviewRequest previewRequest = new LogisticsImportPreviewRequest();
        previewRequest.setTenantId(tenantId);
        previewRequest.setPlatform(resolvedPlatform);
        previewRequest.setContent(content);
        previewRequest.setFileName(file.getOriginalFilename());

        if (!"import".equalsIgnoreCase(mode)) {
            return Result.success(logisticsImportApplicationService.preview(previewRequest));
        }

        LogisticsImportPreviewResponse previewed = logisticsImportApplicationService.preview(previewRequest);
        LogisticsImportRequest importRequest = new LogisticsImportRequest();
        importRequest.setTenantId(tenantId);
        importRequest.setPlatform(previewed.getPlatform());
        importRequest.setRows(previewed.getRows());
        importRequest.setFileName(file.getOriginalFilename());
        importRequest.setUpdateExisting(updateExisting == null || updateExisting);
        return Result.success(logisticsImportApplicationService.importLogistics(importRequest));
    }

    @Operation(summary = "批量导入物流数据", description = "执行批量导入，返回导入任务结果（含每行的成功/跳过/失败明细）")
    @PostMapping
    public Result<LogisticsImportJobDto> importLogistics(@RequestBody LogisticsImportRequest request) {
        try {
            return Result.success(logisticsImportApplicationService.importLogistics(request));
        } catch (BusinessException e) {
            log.warn("[LogisticsImport] 导入失败: {}", e.getMessage());
            return Result.error(e.getErrorCode(), e.getMessage());
        }
    }

    @Operation(summary = "从平台同步物流数据", description = "按平台集成直接拉取物流数据并导入，用于定时任务或页面「一键同步」")
    @PostMapping("/sync")
    public Result<LogisticsImportJobDto> syncFromPlatform(
            @Parameter(description = "平台集成ID") @RequestParam(required = false) String integrationId,
            @Parameter(description = "租户ID") @RequestParam(required = false) String tenantId,
            @Parameter(description = "平台订单号") @RequestParam(required = false) String orderNumber,
            @Parameter(description = "运单号") @RequestParam(required = false) String trackingNumber,
            @Parameter(description = "发货时间起 yyyy-MM-dd HH:mm:ss") @RequestParam(required = false) String shippedFrom,
            @Parameter(description = "发货时间止 yyyy-MM-dd HH:mm:ss") @RequestParam(required = false) String shippedTo,
            @Parameter(description = "拉取条数") @RequestParam(required = false) Integer limit,
            @Parameter(description = "是否使用平台示例数据") @RequestParam(required = false) Boolean useSampleData) {
        try {
            return Result.success(logisticsImportApplicationService.syncFromPlatform(integrationId, tenantId,
                    orderNumber, trackingNumber, shippedFrom, shippedTo, limit, useSampleData));
        } catch (BusinessException e) {
            log.warn("[LogisticsImport] 平台同步失败: {}", e.getMessage());
            return Result.error(e.getErrorCode(), e.getMessage());
        }
    }

    @Operation(summary = "查询导入任务列表", description = "分页查询导入任务，用于结果回溯")
    @GetMapping("/jobs")
    public Result<PageResult<LogisticsImportJobDto>> listJobs(
            @Parameter(description = "租户ID") @RequestParam(required = false) String tenantId,
            @Parameter(description = "页码，从0开始") @RequestParam(required = false, defaultValue = "0") Integer page,
            @Parameter(description = "每页条数") @RequestParam(required = false, defaultValue = "10") Integer size) {
        return Result.success(logisticsImportApplicationService.listJobs(tenantId,
                page == null ? 0 : page, size == null ? 10 : size));
    }

    @Operation(summary = "查询导入任务详情", description = "含所有失败行的错误明细")
    @GetMapping("/jobs/{jobId}")
    public Result<LogisticsImportJobDto> getJob(@Parameter(description = "导入任务ID") @PathVariable String jobId) {
        return Result.success(logisticsImportApplicationService.getJob(jobId));
    }

    @Operation(summary = "下载CSV导入模板", description = "返回标准CSV模板文本内容")
    @GetMapping(value = "/template", produces = "text/csv; charset=utf-8")
    public ResponseEntity<ByteArrayResource> csvTemplate() {
        String template = logisticsImportApplicationService.csvTemplate();
        ByteArrayResource resource = new ByteArrayResource(template.getBytes(StandardCharsets.UTF_8));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=logistics_import_template.csv")
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
