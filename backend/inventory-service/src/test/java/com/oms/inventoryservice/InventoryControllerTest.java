package com.oms.inventoryservice;

import com.oms.inventoryservice.controller.InventoryController;
import com.oms.inventoryservice.dto.UpdateInventoryResponse;
import com.oms.inventoryservice.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InventoryService inventoryService;

    @Test
    public void testGetLowStockAlerts() throws Exception {
        UpdateInventoryResponse response = UpdateInventoryResponse.builder()
                .productId("p1")
                .availableQuantity(2)
                .lowStockThreshold(10)
                .message("LOW STOCK ALERT")
                .build();

        when(inventoryService.getLowStockProducts()).thenReturn(Collections.singletonList(response));

        mockMvc.perform(get("/api/v1/inventory/low-stock")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result[0].productId").value("p1"))
                .andExpect(jsonPath("$.result[0].availableQuantity").value(2));
    }
}
