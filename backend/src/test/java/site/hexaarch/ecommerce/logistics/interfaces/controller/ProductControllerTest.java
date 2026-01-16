package site.hexaarch.ecommerce.logistics.interfaces.controller;

import site.hexaarch.ecommerce.logistics.application.service.ProductApplicationService;
import site.hexaarch.ecommerce.logistics.domain.product.aggregate.Product;
import site.hexaarch.ecommerce.logistics.domain.product.entity.ProductCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProductControllerTest {

    @Mock
    private ProductApplicationService productApplicationService;

    @InjectMocks
    private ProductController productController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(productController).build();
    }

    @Test
    void testCreateProduct() throws Exception {
        String productId = UUID.randomUUID().toString();
        String categoryId = UUID.randomUUID().toString();
        
        ProductCategory category = ProductCategory.builder()
                .id(categoryId)
                .name("Electronics")
                .build();
        
        Product mockProduct = Product.builder()
                .id(productId)
                .name("Smartphone")
                .description("Latest smartphone")
                .category(category)
                .build();

        String smartphone = eq("Smartphone");
        String latestSmartphone = eq("Latest smartphone");
        ProductCategory productCategory = eq(category);
        Product product = productApplicationService.createProduct(smartphone, latestSmartphone, "I'm description", productCategory);
        when(product).thenReturn(mockProduct);

        mockMvc.perform(post("/api/products")
                .param("name", "Smartphone")
                .param("description", "Latest smartphone")
                .param("categoryName", "Electronics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(productId));
    }

    @Test
    void testGetProductById() throws Exception {
        String productId = UUID.randomUUID().toString();
        String categoryId = UUID.randomUUID().toString();
        
        ProductCategory category = ProductCategory.builder()
                .id(categoryId)
                .name("Electronics")
                .build();
        
        Product mockProduct = Product.builder()
                .id(productId)
                .name("Smartphone")
                .description("Latest smartphone")
                .category(category)
                .build();

        when(productApplicationService.findProductById(eq(productId))).thenReturn(mockProduct);

        mockMvc.perform(get("/api/products/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(productId));
    }

    @Test
    void testGetProductBySkuCode() throws Exception {
        String productId = UUID.randomUUID().toString();
        String categoryId = UUID.randomUUID().toString();
        String skuCode = "PHONE-001";
        
        ProductCategory category = ProductCategory.builder()
                .id(categoryId)
                .name("Electronics")
                .build();
        
        Product mockProduct = Product.builder()
                .id(productId)
                .name("Smartphone")
                .description("Latest smartphone")
                .category(category)
                .build();

        when(productApplicationService.findProductBySkuCode(eq(skuCode))).thenReturn(mockProduct);

        mockMvc.perform(get("/api/products/sku/" + skuCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(productId));
    }

    @Test
    void testActivateProduct() throws Exception {
        String productId = UUID.randomUUID().toString();
        String categoryId = UUID.randomUUID().toString();
        
        ProductCategory category = ProductCategory.builder()
                .id(categoryId)
                .name("Electronics")
                .build();
        
        Product mockProduct = Product.builder()
                .id(productId)
                .name("Smartphone")
                .description("Latest smartphone")
                .category(category)
                .build();

        when(productApplicationService.activateProduct(eq(productId))).thenReturn(mockProduct);

        mockMvc.perform(put("/api/products/" + productId + "/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(productId));
    }

    @Test
    void testDeactivateProduct() throws Exception {
        String productId = UUID.randomUUID().toString();
        String categoryId = UUID.randomUUID().toString();
        
        ProductCategory category = ProductCategory.builder()
                .id(categoryId)
                .name("Electronics")
                .build();
        
        Product mockProduct = Product.builder()
                .id(productId)
                .name("Smartphone")
                .description("Latest smartphone")
                .category(category)
                .build();

        when(productApplicationService.deactivateProduct(eq(productId))).thenReturn(mockProduct);

        mockMvc.perform(put("/api/products/" + productId + "/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(productId));
    }
}