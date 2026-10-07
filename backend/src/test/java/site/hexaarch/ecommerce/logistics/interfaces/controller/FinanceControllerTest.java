package site.hexaarch.ecommerce.logistics.interfaces.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import site.hexaarch.ecommerce.logistics.application.service.finance.FinanceApplicationService;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.finance.aggregate.FinancialTransaction;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionStatus;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionType;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.finance.FinancialTransactionMapper;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.finance.FinancialTransactionMapperImpl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import site.hexaarch.ecommerce.logistics.interfaces.controller.finance.FinanceController;

/**
 * 财务控制器单元测试：按新契约（JSON body 创建、分页列表、DTO 统一响应形状）覆盖各端点。
 * <p>
 * 修复说明：此前测试未 mock/装配 FinancialTransactionMapper，controller 内 NPE 被 catch 吞掉。
 * 现改用真实 MapStruct 生成实现 FinancialTransactionMapperImpl，与 ProductControllerTest 同构。
 * </p>
 */
class FinanceControllerTest {

    private static final String TXN_ID = "TXN-00000001";

    @Mock
    private FinanceApplicationService financeApplicationService;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        FinancialTransactionMapper financialTransactionMapper = new FinancialTransactionMapperImpl();
        FinanceController financeController = new FinanceController(financeApplicationService, financialTransactionMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(financeController).build();
    }

    @Test
    void testCreateFinancialTransactionWithJsonBody() throws Exception {
        when(financeApplicationService.createFinancialTransaction(
                eq(TransactionType.INCOME),
                any(BigDecimal.class),
                eq("USD"),
                eq("REF001"),
                eq("ORDER"),
                eq("示例收入"),
                eq("tenant-a")))
                .thenReturn(mockTransaction(TransactionType.INCOME, TransactionStatus.PENDING));

        String body = """
                {
                  "type": "INCOME",
                  "amount": 100.00,
                  "currency": "USD",
                  "referenceId": "REF001",
                  "referenceType": "ORDER",
                  "description": "示例收入",
                  "tenantId": "tenant-a"
                }
                """;

        mockMvc.perform(post("/api/finance/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.transactionId").value(TXN_ID))
                .andExpect(jsonPath("$.data.tenantId").doesNotExist())
                // 枚举线上格式实测：Jackson 默认按枚举名序列化（无 @JsonValue）
                .andExpect(jsonPath("$.data.type").value("INCOME"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void testSearchFinancialTransactions() throws Exception {
        PageResult<FinancialTransaction> pageResult = new PageResult<>(
                List.of(mockTransaction(TransactionType.EXPENSE, TransactionStatus.SUCCESS)), 1L, 0, 20);
        when(financeApplicationService.searchFinancialTransactions(any(), any(), any(), any(), any(),
                any(), any(), anyInt(), anyInt())).thenReturn(pageResult);

        mockMvc.perform(get("/api/finance/transactions")
                        .param("page", "0")
                        .param("size", "20")
                        .param("type", "EXPENSE")
                        .param("status", "SUCCESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].transactionId").value(TXN_ID))
                .andExpect(jsonPath("$.data.content[0].type").value("EXPENSE"))
                .andExpect(jsonPath("$.data.content[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20));
    }

    @Test
    void testFindFinancialTransactionById() throws Exception {
        when(financeApplicationService.findFinancialTransactionById(eq(TXN_ID)))
                .thenReturn(mockTransaction(TransactionType.INCOME, TransactionStatus.PENDING));

        mockMvc.perform(get("/api/finance/transactions/" + TXN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.transactionId").value(TXN_ID))
                .andExpect(jsonPath("$.data.amount").value(100.00));
    }

    @Test
    void testFindFinancialTransactionsByStatus() throws Exception {
        when(financeApplicationService.findFinancialTransactionsByStatus(eq(TransactionStatus.SUCCESS)))
                .thenReturn(List.of(mockTransaction(TransactionType.INCOME, TransactionStatus.SUCCESS)));

        mockMvc.perform(get("/api/finance/transactions/by-status").param("status", "SUCCESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("SUCCESS"));
    }

    @Test
    void testFindFinancialTransactionsByType() throws Exception {
        when(financeApplicationService.findFinancialTransactionsByType(eq(TransactionType.REFUND)))
                .thenReturn(List.of(mockTransaction(TransactionType.REFUND, TransactionStatus.PENDING)));

        mockMvc.perform(get("/api/finance/transactions/by-type").param("type", "REFUND"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].type").value("REFUND"));
    }

    @Test
    void testFindFinancialTransactionsByReference() throws Exception {
        when(financeApplicationService.findFinancialTransactionsByReference(eq("REF001"), eq("ORDER")))
                .thenReturn(List.of(mockTransaction(TransactionType.INCOME, TransactionStatus.PENDING)));

        mockMvc.perform(get("/api/finance/transactions/by-reference")
                        .param("referenceId", "REF001")
                        .param("referenceType", "ORDER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void testCompleteFinancialTransaction() throws Exception {
        when(financeApplicationService.completeFinancialTransaction(eq(TXN_ID)))
                .thenReturn(mockTransaction(TransactionType.INCOME, TransactionStatus.SUCCESS));

        mockMvc.perform(put("/api/finance/transactions/" + TXN_ID + "/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));
    }

    @Test
    void testFailFinancialTransaction() throws Exception {
        when(financeApplicationService.failFinancialTransaction(eq(TXN_ID)))
                .thenReturn(mockTransaction(TransactionType.INCOME, TransactionStatus.FAILED));

        mockMvc.perform(put("/api/finance/transactions/" + TXN_ID + "/fail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.status").value("FAILED"));
    }

    @Test
    void testCancelFinancialTransaction() throws Exception {
        when(financeApplicationService.cancelFinancialTransaction(eq(TXN_ID)))
                .thenReturn(mockTransaction(TransactionType.INCOME, TransactionStatus.CANCELLED));

        mockMvc.perform(put("/api/finance/transactions/" + TXN_ID + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    /**
     * 枚举线上格式实测（直接序列化路径）：保持 Jackson 默认枚举名序列化，无 @JsonValue 小写化
     */
    @Test
    void transactionEnumsSerializeByConstantName() throws Exception {
        String json = objectMapper.writeValueAsString(new EnumProbe(TransactionType.ADJUSTMENT,
                TransactionStatus.CANCELLED));
        assertTrue(json.contains("\"ADJUSTMENT\""), "类型应按枚举名序列化: " + json);
        assertTrue(json.contains("\"CANCELLED\""), "状态应按枚举名序列化: " + json);
    }

    private record EnumProbe(TransactionType type, TransactionStatus status) {
    }

    private FinancialTransaction mockTransaction(TransactionType type, TransactionStatus status) {
        return FinancialTransaction.builder()
                .transactionId(TXN_ID)
                .tenantId("tenant-a")
                .type(type)
                .amount(BigDecimal.valueOf(100.00))
                .currency("USD")
                .referenceId("REF001")
                .referenceType("ORDER")
                .description("示例收入")
                .status(status)
                .createdAt(LocalDateTime.of(2026, 9, 1, 10, 0, 0))
                .updatedAt(LocalDateTime.of(2026, 9, 1, 10, 0, 0))
                .build();
    }
}
