package com.oms.inventoryservice.client;

import com.oms.common.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "product-service", url = "${app.services.product-service.url:http://product-service:8081}")
public interface ProductClient {

    @GetMapping("/api/v1/products/{id}")
    ApiResponse<Object> getProductById(@PathVariable("id") String id);
}
