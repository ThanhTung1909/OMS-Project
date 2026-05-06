package com.oms.profile.controller;


import com.oms.profile.dto.AddressRequest;
import com.oms.profile.dto.CustomerProfileResponse;
import com.oms.profile.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {
    private final CustomerService customerService;
    @GetMapping("/me")
    public ResponseEntity<CustomerProfileResponse> getMyProfile(@RequestHeader("X-Account-Id") String accountId) {
        return ResponseEntity.ok(customerService.getProfileByAccountId(accountId));
    }

    @PostMapping("/addresses")
    public ResponseEntity<String> addAddress(@RequestHeader("X-Account-Id") String accountId, @RequestBody AddressRequest request) {
        customerService.addAddress(accountId, request);
        return ResponseEntity.ok("Address added successfully");
    }

    @PutMapping("/addresses/{addressId}/default")
    public ResponseEntity<String> setDefaultAddress(@RequestHeader("X-Account-Id") String accountId, @PathVariable String addressId) {
        customerService.setDefaultAddress(accountId, addressId);
        return ResponseEntity.ok("Default address updated successfully");
    }

    @GetMapping
    public ResponseEntity<List<CustomerProfileResponse>> getAllCustomers() {
        return ResponseEntity.ok(customerService.getAllCustomers());
    }
}
