package site.hexaarch.ecommerce.logistics.interfaces.controller;

import site.hexaarch.ecommerce.logistics.application.service.customer.CustomerApplicationService;
import site.hexaarch.ecommerce.logistics.domain.customer.aggregate.Customer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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

    @Test
    void testCreateCustomer() throws Exception {
        String customerId = UUID.randomUUID().toString();
        Customer mockCustomer = Customer.create(
                "TEST_TENANT",
                customerId,
                "John Doe",
                "john@example.com",
                "1234567890",
                "123 Main St");
        mockCustomer.activate(); // 设置为激活状态

        when(customerApplicationService.createCustomer(any(String.class), any(String.class), any(String.class), any(String.class)))
                .thenReturn(mockCustomer);

        mockMvc.perform(post("/api/customers")
                .param("customerName", "John Doe")
                .param("email", "john@example.com")
                .param("phone", "1234567890")
                .param("address", "123 Main St"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.customerId").value(customerId));
    }

    @Test
    void testGetCustomerById() throws Exception {
        String customerId = UUID.randomUUID().toString();
        Customer mockCustomer = Customer.create(
                "TEST_TENANT",
                customerId,
                "John Doe",
                "john@example.com",
                "1234567890",
                "123 Main St");
        mockCustomer.activate(); // 设置为激活状态

        when(customerApplicationService.findCustomerById(eq(customerId))).thenReturn(mockCustomer);

        mockMvc.perform(get("/api/customers/" + customerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.customerId").value(customerId));
    }

    @Test
    void testDeleteCustomer() throws Exception {
        String customerId = UUID.randomUUID().toString();

        mockMvc.perform(delete("/api/customers/" + customerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}