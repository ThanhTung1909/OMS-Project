package com.oms.identityservice.controller;

import com.oms.common.ApiResponse;
import com.oms.identityservice.dto.AccountResponse;
import com.oms.identityservice.entity.Account;
import com.oms.identityservice.entity.Enum.AccountStatus;
import com.oms.identityservice.repository.AccountRepository;
import com.oms.identityservice.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountRepository accountRepository;
    private final AuthService authService;

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
                                .status(account.getStatus() != null ? account.getStatus().name() : null)
                                .build())
                        .build()))
                .orElse(ResponseEntity.notFound().build());
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<AccountResponse>>> getAllAccounts() {
        return ResponseEntity.ok(ApiResponse.<List<AccountResponse>>builder()
                .success(true)
                .status(200)
                .message("Thành công")
                .result(authService.getAccounts())
                .build());
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<AccountResponse>> changeAccountStatus(
            @PathVariable String id,
            @RequestParam AccountStatus status) {
        return ResponseEntity.ok(ApiResponse.<AccountResponse>builder()
                .success(true)
                .status(200)
                .message("Cập nhật trạng thái tài khoản thành công")
                .result(authService.changeAccountStatus(id, status))
                .build());
    }
}
