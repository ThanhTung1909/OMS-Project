package com.oms.notificationservice.client;

import com.oms.common.ApiResponse;
import com.oms.notificationservice.dto.AccountResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "identity-service", url = "${app.services.identity-service.url:http://identity-service:8080}")
public interface AccountClient {

    @GetMapping("/api/v1/accounts/{id}")
    ApiResponse<AccountResponse> getAccountById(@PathVariable("id") String id);
}
