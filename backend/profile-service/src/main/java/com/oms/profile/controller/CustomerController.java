package com.oms.profile.controller;


import com.oms.common.ApiResponse;
import com.oms.profile.dto.AddressRequest;
import com.oms.profile.dto.CustomerProfileResponse;
import com.oms.profile.dto.UpdateProfileRequest;
import com.oms.profile.service.CustomerService;
import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {
    private final CustomerService customerService;
    
    @GetMapping({"/me", "/account/{accountId}"})
    public ResponseEntity<ApiResponse<CustomerProfileResponse>> getProfile(
            @RequestHeader(value = "X-Account-Id", required = false) String headerId,
            @PathVariable(value = "accountId", required = false) String pathId) {
        // Ưu tiên pathId (cho admin/internal), sau đó mới đến headerId (cho user tự xem)
        String id = (pathId != null) ? pathId : headerId;
        return ok(customerService.getProfileByAccountId(id));
    }

    @PostMapping("/addresses")
    public ResponseEntity<ApiResponse<Void>> addAddress(@RequestHeader("X-Account-Id") String accountId, @RequestBody AddressRequest request) {
        customerService.addAddress(accountId, request);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .status(200)
                .message("Address added successfully")
                .build());
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<CustomerProfileResponse>> updateProfile(
            @RequestHeader("X-Account-Id") String accountId,
            @RequestBody UpdateProfileRequest request) {
        return ok(customerService.updateProfile(accountId, request));
    }

    @PutMapping("/addresses/{addressId}/default")
    public ResponseEntity<ApiResponse<Void>> setDefaultAddress(@RequestHeader("X-Account-Id") String accountId, @PathVariable String addressId) {
        customerService.setDefaultAddress(accountId, addressId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .status(200)
                .message("Default address updated successfully")
                .build());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CustomerProfileResponse>>> getAllCustomers() {
        return ResponseEntity.ok(ApiResponse.<List<CustomerProfileResponse>>builder()
                .success(true)
                .status(200)
                .message("Thành công")
                .result(customerService.getAllCustomers())
                .build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerProfileResponse>> getCustomerById(@PathVariable String id) {
        return ok(customerService.getProfileById(id));
    }

    private ResponseEntity<ApiResponse<CustomerProfileResponse>> ok(CustomerProfileResponse result) {
        return ResponseEntity.ok(ApiResponse.<CustomerProfileResponse>builder()
                .success(true)
                .status(200)
                .message("Thành công")
                .result(result)
                .build());
    }
}
