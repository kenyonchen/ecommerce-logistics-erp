package site.hexaarch.ecommerce.logistics.interfaces.controller.finance;

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
import site.hexaarch.ecommerce.logistics.application.service.finance.FinanceImportApplicationService;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;
import site.hexaarch.ecommerce.logistics.infrastructure.importing.ExcelSourceParser;
import site.hexaarch.ecommerce.logistics.interfaces.common.Result;
import site.hexaarch.ecommerce.logistics.interfaces.dto.financeimport.FinanceImportJobDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.financeimport.FinanceImportPreviewRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.financeimport.FinanceImportPreviewResponse;
import site.hexaarch.ecommerce.logistics.interfaces.dto.financeimport.FinanceImportRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.FieldMappingDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.FieldMappingSaveRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.TargetFieldDto;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 第三方电商平台财务数据导入控制器。
 * <p>
 * 接口分组：
 * <ul>
 *     <li>数据获取与预览：{@code POST /api/finance/import/preview}、{@code POST /api/finance/import/upload}</li>
 *     <li>批量导入：{@code POST /api/finance/import}</li>
 *     <li>结果反馈：{@code GET /api/finance/import/jobs}、{@code GET /api/finance/import/jobs/{id}}</li>
 *     <li>字段映射：{@code GET|PUT /api/finance/import/field-mappings}</li>
 * </ul>
 * </p>
 *
 * @author kenyon
 */
@Slf4j
@RestController
@RequestMapping("/api/finance/import")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN') or hasAuthority('finance:import')")
@Tag(name = "财务导入", description = "第三方电商平台财务数据获取、字段映射与批量导入")
public class FinanceImportController {

    private final FinanceImportApplicationService financeImportApplicationService;
    private final ExcelSourceParser excelSourceParser;

    @Operation(summary = "标准目标字段列表", description = "获取可用于字段映射的系统标准财务字段")
    @GetMapping("/target-fields")
    public Result<List<TargetFieldDto>> targetFields() {
        return Result.success(financeImportApplicationService.targetFields());
    }

    @Operation(summary = "字段转换规则列表", description = "获取字段取值转换规则")
    @GetMapping("/transforms")
    public Result<List<TargetFieldDto>> transforms() {
        return Result.success(financeImportApplicationService.transforms());
    }

    @Operation(summary = "交易类型字典", description = "导入时可映射的交易类型")
    @GetMapping("/transaction-types")
    public Result<List<TargetFieldDto>> transactionTypes() {
        return Result.success(financeImportApplicationService.transactionTypes());
    }

    @Operation(summary = "查询字段映射", description = "查询指定租户与平台已保存的字段映射")
    @GetMapping("/field-mappings")
    public Result<List<FieldMappingDto>> getFieldMappings(
            @Parameter(description = "租户ID") @RequestParam(required = false) String tenantId,
            @Parameter(description = "平台名称") @RequestParam String platform) {
        return Result.success(financeImportApplicationService.getMappings(tenantId, platform));
    }

    @Operation(summary = "保存字段映射", description = "按租户+平台整体保存字段映射关系")
    @PutMapping("/field-mappings")
    public Result<List<FieldMappingDto>> saveFieldMappings(@RequestBody FieldMappingSaveRequest request) {
        return Result.success(financeImportApplicationService.saveMappings(request));
    }

    @Operation(summary = "财务数据导入预览", description = "拉取平台财务数据或解析传入内容，并完成字段映射，返回待导入数据")
    @PostMapping("/preview")
    public Result<FinanceImportPreviewResponse> preview(@RequestBody FinanceImportPreviewRequest request) {
        try {
            return Result.success(financeImportApplicationService.preview(request));
        } catch (BusinessException e) {
            log.warn("[FinanceImport] 预览失败: {}", e.getMessage());
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
            @Parameter(description = "平台交易流水号已存在时是否覆盖更新") @RequestParam(required = false) Boolean updateExisting) {
        String content = readContent(file);
        String resolvedPlatform = platform == null ? FinanceImportApplicationService.FILE_PLATFORM : platform;

        if ("import".equalsIgnoreCase(mode)) {
            FinanceImportPreviewRequest previewRequest = new FinanceImportPreviewRequest();
            previewRequest.setTenantId(tenantId);
            previewRequest.setPlatform(resolvedPlatform);
            previewRequest.setContent(content);
            previewRequest.setFileName(file.getOriginalFilename());
            FinanceImportPreviewResponse previewed = financeImportApplicationService.preview(previewRequest);

            FinanceImportRequest importRequest = new FinanceImportRequest();
            importRequest.setTenantId(tenantId);
            importRequest.setPlatform(previewed.getPlatform());
            importRequest.setRows(previewed.getRows());
            importRequest.setUpdateExisting(updateExisting == null || updateExisting);
            importRequest.setFileName(file.getOriginalFilename());
            return Result.success(financeImportApplicationService.importTransactions(importRequest));
        }

        FinanceImportPreviewRequest previewRequest = new FinanceImportPreviewRequest();
        previewRequest.setTenantId(tenantId);
        previewRequest.setPlatform(resolvedPlatform);
        previewRequest.setContent(content);
        previewRequest.setFileName(file.getOriginalFilename());
        return Result.success(financeImportApplicationService.preview(previewRequest));
    }

    @Operation(summary = "批量导入财务数据", description = "执行批量导入，返回导入任务结果（含每行的成功/失败明细）")
    @PostMapping
    public Result<FinanceImportJobDto> importTransactions(@RequestBody FinanceImportRequest request) {
        try {
            return Result.success(financeImportApplicationService.importTransactions(request));
        } catch (BusinessException e) {
            log.warn("[FinanceImport] 导入失败: {}", e.getMessage());
            return Result.error(e.getErrorCode(), e.getMessage());
        }
    }

    @Operation(summary = "查询导入任务列表", description = "分页查询导入任务，用于结果回溯")
    @GetMapping("/jobs")
    public Result<PageResult<FinanceImportJobDto>> listJobs(
            @Parameter(description = "租户ID") @RequestParam(required = false) String tenantId,
            @Parameter(description = "页码，从0开始") @RequestParam(required = false, defaultValue = "0") Integer page,
            @Parameter(description = "每页条数") @RequestParam(required = false, defaultValue = "10") Integer size) {
        return Result.success(financeImportApplicationService.listJobs(tenantId,
                page == null ? 0 : page, size == null ? 10 : size));
    }

    @Operation(summary = "查询导入任务详情", description = "含所有失败行的错误明细")
    @GetMapping("/jobs/{jobId}")
    public Result<FinanceImportJobDto> getJob(@Parameter(description = "导入任务ID") @PathVariable String jobId) {
        return Result.success(financeImportApplicationService.getJob(jobId));
    }

    @Operation(summary = "下载CSV导入模板", description = "返回标准CSV模板文本内容")
    @GetMapping(value = "/template", produces = "text/csv; charset=utf-8")
    public ResponseEntity<ByteArrayResource> csvTemplate() {
        String template = financeImportApplicationService.csvTemplate();
        ByteArrayResource resource = new ByteArrayResource(template.getBytes(StandardCharsets.UTF_8));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=finance_import_template.csv")
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
