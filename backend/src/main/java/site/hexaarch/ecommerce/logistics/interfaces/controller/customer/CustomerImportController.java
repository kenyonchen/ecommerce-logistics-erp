package site.hexaarch.ecommerce.logistics.interfaces.controller.customer;

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
import site.hexaarch.ecommerce.logistics.application.service.customer.CustomerImportApplicationService;
import site.hexaarch.ecommerce.logistics.domain.audit.Auditable;
import site.hexaarch.ecommerce.logistics.domain.audit.AuditActions;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;
import site.hexaarch.ecommerce.logistics.infrastructure.importing.ExcelSourceParser;
import site.hexaarch.ecommerce.logistics.interfaces.common.Result;
import site.hexaarch.ecommerce.logistics.interfaces.dto.customerimport.CustomerImportJobDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.customerimport.CustomerImportPreviewRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.customerimport.CustomerImportPreviewResponse;
import site.hexaarch.ecommerce.logistics.interfaces.dto.customerimport.CustomerImportRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.FieldMappingDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.FieldMappingSaveRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.TargetFieldDto;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 第三方电商平台客户导入控制器。
 * <p>
 * 接口分组：
 * <ul>
 *     <li>数据获取与预览：{@code POST /api/customers/import/preview}、{@code POST /api/customers/import/upload}</li>
 *     <li>批量导入：{@code POST /api/customers/import}</li>
 *     <li>结果反馈：{@code GET /api/customers/import/jobs}、{@code GET /api/customers/import/jobs/{id}}</li>
 *     <li>字段映射：{@code GET|PUT /api/customers/import/field-mappings}</li>
 * </ul>
 * </p>
 *
 * @author kenyon
 */
@Slf4j
@RestController
@RequestMapping("/api/customers/import")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN') or hasAuthority('customer:import')")
@Tag(name = "客户导入", description = "第三方电商平台客户数据获取、字段映射与批量导入")
public class CustomerImportController {

    private final CustomerImportApplicationService customerImportApplicationService;
    private final ExcelSourceParser excelSourceParser;

    @Operation(summary = "标准目标字段列表", description = "获取可用于字段映射的系统标准客户字段")
    @GetMapping("/target-fields")
    public Result<List<TargetFieldDto>> targetFields() {
        return Result.success(customerImportApplicationService.targetFields());
    }

    @Operation(summary = "字段转换规则列表", description = "获取字段取值转换规则")
    @GetMapping("/transforms")
    public Result<List<TargetFieldDto>> transforms() {
        return Result.success(customerImportApplicationService.transforms());
    }

    @Operation(summary = "查询字段映射", description = "查询指定租户与平台已保存的字段映射")
    @GetMapping("/field-mappings")
    public Result<List<FieldMappingDto>> getFieldMappings(
            @Parameter(description = "租户ID") @RequestParam(required = false) String tenantId,
            @Parameter(description = "平台名称") @RequestParam String platform) {
        return Result.success(customerImportApplicationService.getMappings(tenantId, platform));
    }

    @Operation(summary = "保存字段映射", description = "按租户+平台整体保存字段映射关系")
    @Auditable(action = AuditActions.IMPORT_FIELD_MAPPING_UPDATE, label = "保存客户导入字段映射", targetType = "CustomerImport")
    @PutMapping("/field-mappings")
    public Result<List<FieldMappingDto>> saveFieldMappings(@RequestBody FieldMappingSaveRequest request) {
        return Result.success(customerImportApplicationService.saveMappings(request));
    }

    @Operation(summary = "客户导入预览", description = "拉取平台客户或解析传入内容，并完成字段映射，返回待导入数据")
    @PostMapping("/preview")
    public Result<CustomerImportPreviewResponse> preview(@RequestBody CustomerImportPreviewRequest request) {
        try {
            return Result.success(customerImportApplicationService.preview(request));
        } catch (BusinessException e) {
            log.warn("[CustomerImport] 预览失败: {}", e.getMessage());
            return Result.error(e.getErrorCode(), e.getMessage());
        }
    }

    @Operation(summary = "上传文件导入", description = "上传JSON/CSV文件，mode=preview 仅预览，mode=import 直接执行导入")
    @Auditable(action = AuditActions.IMPORT_UPLOAD, label = "上传客户导入文件", targetType = "CustomerImportJob")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Object> upload(
            @Parameter(description = "导入文件(JSON/CSV)") @RequestPart("file") MultipartFile file,
            @Parameter(description = "预览或导入") @RequestParam(required = false, defaultValue = "preview") String mode,
            @Parameter(description = "租户ID") @RequestParam(required = false) String tenantId,
            @Parameter(description = "平台名称") @RequestParam(required = false) String platform,
            @Parameter(description = "数据来源类型(CUSTOMERS|ORDERS)") @RequestParam(required = false) String sourceType,
            @Parameter(description = "平台买家已存在时是否覆盖更新") @RequestParam(required = false) Boolean updateExisting) {
        String content = readContent(file);
        String resolvedPlatform = platform == null ? CustomerImportApplicationService.FILE_PLATFORM : platform;

        if ("import".equalsIgnoreCase(mode)) {
            CustomerImportPreviewRequest previewRequest = new CustomerImportPreviewRequest();
            previewRequest.setTenantId(tenantId);
            previewRequest.setPlatform(resolvedPlatform);
            previewRequest.setSourceType(sourceType);
            previewRequest.setContent(content);
            previewRequest.setFileName(file.getOriginalFilename());
            CustomerImportPreviewResponse previewed = customerImportApplicationService.preview(previewRequest);

            CustomerImportRequest importRequest = new CustomerImportRequest();
            importRequest.setTenantId(tenantId);
            importRequest.setPlatform(previewed.getPlatform());
            importRequest.setRows(previewed.getRows());
            importRequest.setUpdateExisting(updateExisting == null || updateExisting);
            importRequest.setFileName(file.getOriginalFilename());
            return Result.success(customerImportApplicationService.importCustomers(importRequest));
        }

        CustomerImportPreviewRequest previewRequest = new CustomerImportPreviewRequest();
        previewRequest.setTenantId(tenantId);
        previewRequest.setPlatform(resolvedPlatform);
        previewRequest.setSourceType(sourceType);
        previewRequest.setContent(content);
        previewRequest.setFileName(file.getOriginalFilename());
        return Result.success(customerImportApplicationService.preview(previewRequest));
    }

    @Operation(summary = "批量导入客户", description = "执行批量导入，返回导入任务结果（含每行的成功/失败明细）")
    @Auditable(action = AuditActions.CUSTOMER_IMPORT_EXECUTE, label = "执行客户导入", targetType = "CustomerImportJob")
    @PostMapping
    public Result<CustomerImportJobDto> importCustomers(@RequestBody CustomerImportRequest request) {
        try {
            return Result.success(customerImportApplicationService.importCustomers(request));
        } catch (BusinessException e) {
            log.warn("[CustomerImport] 导入失败: {}", e.getMessage());
            return Result.error(e.getErrorCode(), e.getMessage());
        }
    }

    @Operation(summary = "查询导入任务列表", description = "分页查询导入任务，用于结果回溯")
    @GetMapping("/jobs")
    public Result<PageResult<CustomerImportJobDto>> listJobs(
            @Parameter(description = "租户ID") @RequestParam(required = false) String tenantId,
            @Parameter(description = "页码，从0开始") @RequestParam(required = false, defaultValue = "0") Integer page,
            @Parameter(description = "每页条数") @RequestParam(required = false, defaultValue = "10") Integer size) {
        return Result.success(customerImportApplicationService.listJobs(tenantId,
                page == null ? 0 : page, size == null ? 10 : size));
    }

    @Operation(summary = "查询导入任务详情", description = "含所有失败行的错误明细")
    @GetMapping("/jobs/{jobId}")
    public Result<CustomerImportJobDto> getJob(@Parameter(description = "导入任务ID") @PathVariable String jobId) {
        return Result.success(customerImportApplicationService.getJob(jobId));
    }

    @Operation(summary = "下载CSV导入模板", description = "返回标准CSV模板文本内容")
    @GetMapping(value = "/template", produces = "text/csv; charset=utf-8")
    public ResponseEntity<ByteArrayResource> csvTemplate() {
        String template = customerImportApplicationService.csvTemplate();
        ByteArrayResource resource = new ByteArrayResource(template.getBytes(StandardCharsets.UTF_8));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=customer_import_template.csv")
                .body(resource);
    }

    private String readContent(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("上传文件为空");
        }
        try {
            if (excelSourceParser.isExcel(file.getOriginalFilename())) {
                return excelSourceParser.toCsv(file.getBytes());
            }
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ValidationException("读取上传文件失败: " + e.getMessage());
        }
    }
}
