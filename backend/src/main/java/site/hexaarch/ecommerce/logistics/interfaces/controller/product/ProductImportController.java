package site.hexaarch.ecommerce.logistics.interfaces.controller.product;

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
import site.hexaarch.ecommerce.logistics.application.service.product.ProductImportApplicationService;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.infrastructure.importing.ExcelSourceParser;
import site.hexaarch.ecommerce.logistics.interfaces.common.Result;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.FieldMappingDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.FieldMappingSaveRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.ProductImportJobDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.ProductImportPreviewRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.ProductImportPreviewResponse;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.ProductImportRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.TargetFieldDto;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 第三方电商平台商品导入控制器。
 * <p>
 * 接口分组：
 * <ul>
 *     <li>数据预览：{@code POST /api/products/import/preview}、{@code POST /api/products/import/upload}</li>
 *     <li>批量导入：{@code POST /api/products/import}</li>
 *     <li>结果反馈：{@code GET /api/products/import/jobs}、{@code GET /api/products/import/jobs/{id}}</li>
 *     <li>字段映射：{@code GET|PUT /api/products/import/field-mappings}</li>
 * </ul>
 * </p>
 *
 * @author kenyon
 */
@Slf4j
@RestController
@RequestMapping("/api/products/import")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN') or hasAuthority('product:import')")
@Tag(name = "商品导入", description = "第三方电商平台商品数据获取、字段映射与批量导入")
public class ProductImportController {

    private final ProductImportApplicationService productImportApplicationService;
    private final ExcelSourceParser excelSourceParser;

    @Operation(summary = "标准目标字段列表", description = "获取可用于字段映射的系统标准商品字段")
    @GetMapping("/target-fields")
    public Result<List<TargetFieldDto>> targetFields() {
        return Result.success(productImportApplicationService.targetFields());
    }

    @Operation(summary = "字段转换规则列表", description = "获取字段取值转换规则（如千克转克）")
    @GetMapping("/transforms")
    public Result<List<TargetFieldDto>> transforms() {
        return Result.success(productImportApplicationService.transforms());
    }

    @Operation(summary = "查询字段映射", description = "查询指定租户与平台已保存的字段映射")
    @GetMapping("/field-mappings")
    public Result<List<FieldMappingDto>> getFieldMappings(
            @Parameter(description = "租户ID") @RequestParam(required = false) String tenantId,
            @Parameter(description = "平台名称") @RequestParam String platform) {
        return Result.success(productImportApplicationService.getMappings(tenantId, platform));
    }

    @Operation(summary = "保存字段映射", description = "按租户+平台整体保存字段映射关系")
    @PutMapping("/field-mappings")
    public Result<List<FieldMappingDto>> saveFieldMappings(@RequestBody FieldMappingSaveRequest request) {
        return Result.success(productImportApplicationService.saveMappings(request));
    }

    @Operation(summary = "商品导入预览", description = "拉取平台商品或解析传入内容，并完成字段映射，返回待导入数据")
    @PostMapping("/preview")
    public Result<ProductImportPreviewResponse> preview(@RequestBody ProductImportPreviewRequest request) {
        try {
            return Result.success(productImportApplicationService.preview(request));
        } catch (BusinessException e) {
            log.warn("[ProductImport] 预览失败: {}", e.getMessage());
            return Result.error(e.getErrorCode(), e.getMessage());
        }
    }

    @Operation(summary = "上传文件导入", description = "上传JSON/CSV文件，mode=preview 仅预览，mode=import 直接执行导入")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Object> upload(
            @Parameter(description = "导入文件(JSON/CSV)") @RequestPart("file") MultipartFile file,
            @Parameter(description = "预览或导入") @RequestParam(required = false, defaultValue = "preview") String mode,
            @Parameter(description = "租户ID") @RequestParam(required = false) String tenantId,
            @Parameter(description = "平台名称") @RequestParam(required = false) String platform,
            @Parameter(description = "SKU已存在时是否覆盖更新") @RequestParam(required = false) Boolean updateExisting) {
        String content = readContent(file);

        if ("import".equalsIgnoreCase(mode)) {
            ProductImportPreviewRequest previewRequest = new ProductImportPreviewRequest();
            previewRequest.setTenantId(tenantId);
            previewRequest.setPlatform(platform == null ? ProductImportApplicationService.FILE_PLATFORM : platform);
            previewRequest.setContent(content);
            previewRequest.setFileName(file.getOriginalFilename());
            ProductImportPreviewResponse previewed = productImportApplicationService.preview(previewRequest);

            ProductImportRequest importRequest = new ProductImportRequest();
            importRequest.setTenantId(tenantId);
            importRequest.setPlatform(previewed.getPlatform());
            importRequest.setRows(previewed.getRows());
            importRequest.setUpdateExisting(updateExisting == null || updateExisting);
            importRequest.setFileName(file.getOriginalFilename());
            return Result.success(productImportApplicationService.importProducts(importRequest));
        }

        ProductImportPreviewRequest previewRequest = new ProductImportPreviewRequest();
        previewRequest.setTenantId(tenantId);
        previewRequest.setPlatform(platform == null ? ProductImportApplicationService.FILE_PLATFORM : platform);
        previewRequest.setContent(content);
        previewRequest.setFileName(file.getOriginalFilename());
        return Result.success(productImportApplicationService.preview(previewRequest));
    }

    @Operation(summary = "批量导入商品", description = "执行批量导入，返回导入任务结果（含每行的成功/失败明细）")
    @PostMapping
    public Result<ProductImportJobDto> importProducts(@RequestBody ProductImportRequest request) {
        try {
            return Result.success(productImportApplicationService.importProducts(request));
        } catch (BusinessException e) {
            log.warn("[ProductImport] 导入失败: {}", e.getMessage());
            return Result.error(e.getErrorCode(), e.getMessage());
        }
    }

    @Operation(summary = "查询导入任务列表", description = "分页查询导入任务，用于结果回溯")
    @GetMapping("/jobs")
    public Result<PageResult<ProductImportJobDto>> listJobs(
            @Parameter(description = "租户ID") @RequestParam(required = false) String tenantId,
            @Parameter(description = "页码，从0开始") @RequestParam(required = false, defaultValue = "0") Integer page,
            @Parameter(description = "每页条数") @RequestParam(required = false, defaultValue = "10") Integer size) {
        return Result.success(productImportApplicationService.listJobs(tenantId, page, size));
    }

    @Operation(summary = "查询导入任务详情", description = "含所有失败行的错误明细")
    @GetMapping("/jobs/{jobId}")
    public Result<ProductImportJobDto> getJob(@Parameter(description = "导入任务ID") @PathVariable String jobId) {
        return Result.success(productImportApplicationService.getJob(jobId));
    }

    @Operation(summary = "下载CSV导入模板", description = "返回标准CSV模板文本内容")
    @GetMapping(value = "/template", produces = "text/csv; charset=utf-8")
    public ResponseEntity<ByteArrayResource> csvTemplate() {
        String template = productImportApplicationService.csvTemplate();
        ByteArrayResource resource = new ByteArrayResource(template.getBytes(StandardCharsets.UTF_8));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=product_import_template.csv")
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
