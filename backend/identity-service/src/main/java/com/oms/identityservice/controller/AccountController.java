package com.oms.identityservice.controller;

import com.oms.common.ApiResponse;
import com.oms.identityservice.dto.AccountResponse;
import com.oms.identityservice.entity.Account;
import com.oms.identityservice.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountRepository accountRepository;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AccountResponse>> getAccountById(@PathVariable String id) {
        return accountRepository.findById(id)
                .map(account -> ResponseEntity.ok(ApiResponse.<AccountResponse>builder()
                        .success(true)
                        .status(200)
                        .message("Thành công")
                        .result(AccountResponse.builder()
                                .id(account.getId())
                                .username(account.getUsername())
                                .email(account.getEmail())
                                .role(account.getRole().name())
                                .build())
                        .build()))
                .orElse(ResponseEntity.notFound().build());
    }
}
