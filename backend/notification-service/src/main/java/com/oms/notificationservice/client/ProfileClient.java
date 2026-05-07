package com.oms.notificationservice.client;

import com.oms.common.ApiResponse;
import com.oms.notificationservice.dto.CustomerProfileResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "profile-service", url = "${app.services.profile-service.url:http://profile-service:8088}")
public interface ProfileClient {

    @GetMapping("/api/v1/customers/{id}")
    ApiResponse<CustomerProfileResponse> getCustomerById(@PathVariable("id") String id);

    @GetMapping("/api/v1/customers/account/{accountId}")
    ApiResponse<CustomerProfileResponse> getProfileByAccountId(@PathVariable("accountId") String accountId);
}
