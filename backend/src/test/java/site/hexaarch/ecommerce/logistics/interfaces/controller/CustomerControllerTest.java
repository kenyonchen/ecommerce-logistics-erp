package site.hexaarch.ecommerce.logistics.interfaces.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import site.hexaarch.ecommerce.logistics.application.service.customer.CustomerApplicationService;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.customer.aggregate.Customer;
import site.hexaarch.ecommerce.logistics.domain.customer.repository.CustomerQuery;

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
import site.hexaarch.ecommerce.logistics.interfaces.controller.customer.CustomerController;

/**
 * 客户控制器接口契约测试（standalone MockMvc，校验统一响应信封与 DTO 字段）。
 *
 * @author kenyon
 */
class CustomerControllerTest {

    @Mock
    private CustomerApplicationService customerApplicationService;

    @InjectMocks
    private CustomerController customerController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(customerController).build();
    }

    private Customer sampleCustomer(String customerId) {
        return Customer.create("TEST_TENANT", customerId, "John Doe",
                "john@example.com", "1234567890", "123 Main St", null, null, null);
    }

    @Test
    void testCreateCustomer() throws Exception {
        String customerId = "CUST-0001";
        when(customerApplicationService.createCustomer(any(), any(), any(), any(), any(), any()))
                .thenReturn(sampleCustomer(customerId));

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"TEST_TENANT\",\"customerName\":\"John Doe\",\"email\":\"john@example.com\","
                                + "\"phone\":\"1234567890\",\"address\":\"123 Main St\",\"company\":\"ACME\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.customerId").value(customerId))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void testGetCustomerById() throws Exception {
        String customerId = "CUST-0002";
        when(customerApplicationService.findCustomerById(eq(customerId))).thenReturn(sampleCustomer(customerId));

        mockMvc.perform(get("/api/customers/" + customerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.customerId").value(customerId));
    }

    @Test
    void testListCustomers() throws Exception {
        Customer customer = sampleCustomer("CUST-0003");
        when(customerApplicationService.searchCustomers(any(CustomerQuery.class)))
                .thenReturn(new PageResult<>(List.of(customer), 1, 0, 10));

        mockMvc.perform(get("/api/customers")
                        .param("tenantId", "TEST_TENANT")
                        .param("keyword", "John")
                        .param("createdFrom", "2026-01-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.content[0].customerId").value("CUST-0003"));
    }

    @Test
    void testUpdateCustomerStatus() throws Exception {
        String customerId = "CUST-0004";
        Customer suspended = sampleCustomer(customerId);
        suspended.suspend();
        when(customerApplicationService.suspendCustomer(eq(customerId))).thenReturn(suspended);

        mockMvc.perform(put("/api/customers/" + customerId + "/suspend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));
    }

    @Test
    void testDeleteCustomer() throws Exception {
        String customerId = "CUST-0005";
        Customer deleted = sampleCustomer(customerId);
        deleted.delete();
        when(customerApplicationService.deleteCustomer(eq(customerId))).thenReturn(deleted);

        mockMvc.perform(delete("/api/customers/" + customerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.status").value("DELETED"));
    }
}
