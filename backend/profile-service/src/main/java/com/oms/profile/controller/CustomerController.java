package com.oms.profile.controller;


import com.oms.profile.dto.CustomerProfileResponse;
import com.oms.profile.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {
    private final CustomerService customerService;
    @GetMapping("/me")
    public ResponseEntity<CustomerProfileResponse> getMyProfile(@RequestHeader("X-Account-Id") String accountId) {
        return ResponseEntity.ok(customerService.getProfileByAccountId(accountId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomerProfileResponse> getCustomerById(@PathVariable String id) {
        return ResponseEntity.ok(customerService.getProfileById(id));
    }
}
