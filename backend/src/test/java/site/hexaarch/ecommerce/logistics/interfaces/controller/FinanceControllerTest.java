package site.hexaarch.ecommerce.logistics.interfaces.controller;

import site.hexaarch.ecommerce.logistics.application.service.finance.FinanceApplicationService;
import site.hexaarch.ecommerce.logistics.domain.finance.aggregate.FinancialTransaction;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionStatus;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FinanceControllerTest {

    @Mock
    private FinanceApplicationService financeApplicationService;

    @InjectMocks
    private FinanceController financeController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(financeController).build();
    }

    @Test
    void testCreateFinancialTransaction() throws Exception {
        String transactionId = UUID.randomUUID().toString();
        FinancialTransaction mockTransaction = FinancialTransaction.builder()
                .transactionId(transactionId)
                .type(TransactionType.INCOME)
                .amount(BigDecimal.valueOf(100.00))
                .currency("USD")
                .referenceId("REF001")
                .referenceType("ORDER")
                .status(TransactionStatus.SUCCESS)
                .build();

        when(financeApplicationService.createFinancialTransaction(
                eq(TransactionType.INCOME),
                any(BigDecimal.class),
                eq("USD"),
                eq("REF001"),
                eq("ORDER"),
                any(String.class)))
                .thenReturn(mockTransaction);

        mockMvc.perform(post("/api/finance/transactions")
                .param("type", "INCOME")
                .param("amount", "100.00")
                .param("currency", "USD")
                .param("referenceId", "REF001")
                .param("referenceType", "ORDER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.transactionId").value(transactionId));
    }

    @Test
    void testFindFinancialTransactionById() throws Exception {
        String transactionId = UUID.randomUUID().toString();
        FinancialTransaction mockTransaction = FinancialTransaction.builder()
                .transactionId(transactionId)
                .type(TransactionType.INCOME)
                .amount(BigDecimal.valueOf(100.00))
                .currency("USD")
                .referenceId("REF001")
                .referenceType("ORDER")
                .status(TransactionStatus.SUCCESS)
                .build();

        when(financeApplicationService.findFinancialTransactionById(eq(transactionId))).thenReturn(mockTransaction);

        mockMvc.perform(get("/api/finance/transactions/" + transactionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.transactionId").value(transactionId));
    }

    @Test
    void testFindFinancialTransactionsByStatus() throws Exception {
        String transactionId = UUID.randomUUID().toString();
        FinancialTransaction mockTransaction = FinancialTransaction.builder()
                .transactionId(transactionId)
                .type(TransactionType.INCOME)
                .amount(BigDecimal.valueOf(100.00))
                .currency("USD")
                .referenceId("REF001")
                .referenceType("ORDER")
                .status(TransactionStatus.SUCCESS)
                .build();

        when(financeApplicationService.findFinancialTransactionsByStatus(eq(TransactionStatus.SUCCESS)))
                .thenReturn(List.of(mockTransaction));

        mockMvc.perform(get("/api/finance/transactions/by-status")
                .param("status", "SUCCESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void testCompleteFinancialTransaction() throws Exception {
        String transactionId = UUID.randomUUID().toString();
        FinancialTransaction mockTransaction = FinancialTransaction.builder()
                .transactionId(transactionId)
                .type(TransactionType.INCOME)
                .amount(BigDecimal.valueOf(100.00))
                .currency("USD")
                .referenceId("REF001")
                .referenceType("ORDER")
                .status(TransactionStatus.SUCCESS)
                .build();

        when(financeApplicationService.completeFinancialTransaction(eq(transactionId))).thenReturn(mockTransaction);

        mockMvc.perform(put("/api/finance/transactions/" + transactionId + "/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.transactionId").value(transactionId));
    }
}