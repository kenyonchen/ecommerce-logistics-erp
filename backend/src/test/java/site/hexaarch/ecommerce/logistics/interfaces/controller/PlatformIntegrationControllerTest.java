package site.hexaarch.ecommerce.logistics.interfaces.controller;

import site.hexaarch.ecommerce.logistics.application.service.platform.PlatformIntegrationApplicationService;
import site.hexaarch.ecommerce.logistics.domain.platform.aggregate.PlatformIntegration;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformName;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PlatformIntegrationControllerTest {

    @Mock
    private PlatformIntegrationApplicationService platformIntegrationApplicationService;

    @InjectMocks
    private PlatformIntegrationController platformIntegrationController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(platformIntegrationController).build();
    }

    @Test
    void testCreatePlatformIntegration() throws Exception {
        UUID platformId = UUID.randomUUID();
        PlatformIntegration mockPlatform = PlatformIntegration.builder()
                .id(platformId)
                .platformName(PlatformName.AMAZON)
                .apiKey("test-api-key")
                .apiSecret("test-api-secret")
                .status(PlatformStatus.ACTIVE)
                .build();

        when(platformIntegrationApplicationService.createPlatformIntegration(any(PlatformIntegration.class)))
                .thenReturn(mockPlatform);

        mockMvc.perform(post("/api/platform-integrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + platformId + "\",\"platformName\":\"AMAZON\",\"apiKey\":\"test-api-key\",\"apiSecret\":\"test-api-secret\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(platformId.toString()));
    }

    @Test
    void testGetPlatformIntegrationById() throws Exception {
        UUID platformId = UUID.randomUUID();
        PlatformIntegration mockPlatform = PlatformIntegration.builder()
                .id(platformId)
                .platformName(PlatformName.AMAZON)
                .apiKey("test-api-key")
                .apiSecret("test-api-secret")
                .status(PlatformStatus.ACTIVE)
                .build();

        when(platformIntegrationApplicationService.getPlatformIntegrationById(eq(platformId))).thenReturn(mockPlatform);

        mockMvc.perform(get("/api/platform-integrations/" + platformId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(platformId.toString()));
    }

    @Test
    void testGetAllPlatformIntegrations() throws Exception {
        UUID platformId = UUID.randomUUID();
        PlatformIntegration mockPlatform = PlatformIntegration.builder()
                .id(platformId)
                .platformName(PlatformName.AMAZON)
                .apiKey("test-api-key")
                .apiSecret("test-api-secret")
                .status(PlatformStatus.ACTIVE)
                .build();

        when(platformIntegrationApplicationService.getAllPlatformIntegrations())
                .thenReturn(List.of(mockPlatform));

        mockMvc.perform(get("/api/platform-integrations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void testUpdatePlatformIntegration() throws Exception {
        UUID platformId = UUID.randomUUID();
        PlatformIntegration mockPlatform = PlatformIntegration.builder()
                .id(platformId)
                .platformName(PlatformName.AMAZON)
                .apiKey("updated-api-key")
                .apiSecret("updated-api-secret")
                .status(PlatformStatus.ACTIVE)
                .build();

        when(platformIntegrationApplicationService.updatePlatformIntegration(eq(platformId), any(PlatformIntegration.class)))
                .thenReturn(mockPlatform);

        mockMvc.perform(put("/api/platform-integrations/" + platformId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + platformId + "\",\"platformName\":\"AMAZON\",\"apiKey\":\"updated-api-key\",\"apiSecret\":\"updated-api-secret\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.apiKey").value("updated-api-key"));
    }

    @Test
    void testDeletePlatformIntegration() throws Exception {
        UUID platformId = UUID.randomUUID();

        mockMvc.perform(delete("/api/platform-integrations/" + platformId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void testActivatePlatformIntegration() throws Exception {
        UUID platformId = UUID.randomUUID();
        PlatformIntegration mockPlatform = PlatformIntegration.builder()
                .id(platformId)
                .platformName(PlatformName.AMAZON)
                .apiKey("test-api-key")
                .apiSecret("test-api-secret")
                .status(PlatformStatus.ACTIVE)
                .build();

        when(platformIntegrationApplicationService.activatePlatformIntegration(eq(platformId))).thenReturn(mockPlatform);

        mockMvc.perform(post("/api/platform-integrations/" + platformId + "/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void testDeactivatePlatformIntegration() throws Exception {
        UUID platformId = UUID.randomUUID();
        PlatformIntegration mockPlatform = PlatformIntegration.builder()
                .id(platformId)
                .platformName(PlatformName.AMAZON)
                .apiKey("test-api-key")
                .apiSecret("test-api-secret")
                .status(PlatformStatus.INACTIVE)
                .build();

        when(platformIntegrationApplicationService.deactivatePlatformIntegration(eq(platformId))).thenReturn(mockPlatform);

        mockMvc.perform(post("/api/platform-integrations/" + platformId + "/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));
    }

    @Test
    void testSyncOrdersFromPlatform() throws Exception {
        UUID platformId = UUID.randomUUID();

        mockMvc.perform(post("/api/platform-integrations/" + platformId + "/sync-orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void testSyncProductsFromPlatform() throws Exception {
        UUID platformId = UUID.randomUUID();

        mockMvc.perform(post("/api/platform-integrations/" + platformId + "/sync-products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void testSyncInventoryToPlatform() throws Exception {
        UUID platformId = UUID.randomUUID();

        mockMvc.perform(post("/api/platform-integrations/" + platformId + "/sync-inventory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}