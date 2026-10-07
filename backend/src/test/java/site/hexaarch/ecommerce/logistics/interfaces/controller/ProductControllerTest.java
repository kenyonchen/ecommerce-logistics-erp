package site.hexaarch.ecommerce.logistics.interfaces.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import site.hexaarch.ecommerce.logistics.application.command.product.UpdateProductCommand;
import site.hexaarch.ecommerce.logistics.application.service.product.ProductApplicationService;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.product.aggregate.Product;
import site.hexaarch.ecommerce.logistics.domain.product.entity.ProductCategory;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.product.ProductMapperImpl;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.product.ProductMapper;
import site.hexaarch.ecommerce.logistics.interfaces.controller.product.ProductController;

/**
 * 商品控制器单元测试：覆盖列表分页、创建、更新、删除与批量操作。
 */
class ProductControllerTest {

    private static final String PRODUCT_ID = "p-001";

    @Mock
    private ProductApplicationService productApplicationService;

    @InjectMocks
    private ProductController productController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ProductMapper productMapper = new ProductMapperImpl();
        productController = new ProductController(productApplicationService, productMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(productController).build();
    }

    @Test
    void testListProducts() throws Exception {
        PageResult<Product> pageResult = new PageResult<>(List.of(mockProduct()), 1L, 0, 10);
        when(productApplicationService.findProducts(any())).thenReturn(pageResult);

        mockMvc.perform(get("/api/products")
                        .param("keyword", "phone")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.content[0].id").value(PRODUCT_ID))
                .andExpect(jsonPath("$.data.content[0].name").value("Smartphone"))
                .andExpect(jsonPath("$.data.content[0].categoryName").value("Electronics"))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void testCreateProduct() throws Exception {
        when(productApplicationService.createProduct(any())).thenReturn(mockProduct());

        String body = """
                {
                  "tenantId": "default-tenant",
                  "name": "Smartphone",
                  "description": "Latest smartphone",
                  "categoryName": "Electronics",
                  "skus": [
                    {"skuCode": "PHONE-001", "skuName": "黑色 128G", "sellingPrice": 1999.00,
                     "costPrice": 1500.00, "currency": "CNY", "weightInGrams": 200,
                     "attributes": [{"name": "颜色", "value": "黑色"}]}
                  ]
                }
                """;

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.id").value(PRODUCT_ID))
                .andExpect(jsonPath("$.data.name").value("Smartphone"))
                .andExpect(jsonPath("$.data.categoryName").value("Electronics"));
    }

    @Test
    void testUpdateProduct() throws Exception {
        when(productApplicationService.updateProduct(eq(PRODUCT_ID), any(UpdateProductCommand.class)))
                .thenReturn(mockProduct());

        String body = """
                {"name": "Smartphone", "description": "Latest smartphone", "active": true}
                """;

        mockMvc.perform(put("/api/products/" + PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(PRODUCT_ID));
    }

    @Test
    void testGetProductById() throws Exception {
        when(productApplicationService.findProductById(PRODUCT_ID)).thenReturn(mockProduct());

        mockMvc.perform(get("/api/products/" + PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.id").value(PRODUCT_ID));
    }

    @Test
    void testDeleteProduct() throws Exception {
        mockMvc.perform(delete("/api/products/" + PRODUCT_ID).param("tenantId", "default-tenant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"));
    }

    @Test
    void testBatchChangeActive() throws Exception {
        when(productApplicationService.batchChangeActive(ArgumentMatchers.<List<String>>any(), eq("default-tenant"), eq(true)))
                .thenReturn(2);

        mockMvc.perform(put("/api/products/batch/active")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\":[\"p-001\",\"p-002\"],\"tenantId\":\"default-tenant\",\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(2));
    }

    private Product mockProduct() {
        ProductCategory category = ProductCategory.builder()
                .id("cat-001")
                .name("Electronics")
                .build();

        return Product.builder()
                .id(PRODUCT_ID)
                .tenantId("default-tenant")
                .name("Smartphone")
                .description("Latest smartphone")
                .category(category)
                .skus(new java.util.ArrayList<>())
                .active(true)
                .domainEvents(new java.util.ArrayList<>())
                .createdAt(LocalDateTime.of(2026, 1, 1, 10, 0))
                .updatedAt(LocalDateTime.of(2026, 1, 1, 10, 0))
                .build();
    }
}
