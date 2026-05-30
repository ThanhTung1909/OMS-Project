package com.oms.identityservice.service;


import com.oms.identityservice.dto.AccountCreatedEvent;
import com.oms.identityservice.dto.AuthResponse;
import com.oms.identityservice.dto.LoginRequest;
import com.oms.identityservice.dto.RegisterRequest;
import com.oms.identityservice.dto.AccountResponse;
import com.oms.identityservice.dto.AccountStatusChangedEvent;
import com.oms.identityservice.entity.Account;
import com.oms.common.AppException;
import java.util.List;
import java.util.stream.Collectors;
import com.oms.identityservice.exception.IdentityErrorCode;
import com.oms.identityservice.entity.Enum.AccountStatus;
import com.oms.identityservice.entity.Enum.Role;
import com.oms.identityservice.repository.AccountRepository;
import com.oms.identityservice.repository.OutboxEventRepository;
import com.oms.identityservice.security.JwtUtil;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.identityservice.entity.OutboxEvent;
import com.oms.identityservice.entity.Enum.OutboxStatus;
import com.oms.common.constant.RabbitMQConstants;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder encoder;
    private final JwtUtil jwt;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    @Transactional
    public AuthResponse register(RegisterRequest r){

        if(!r.getPassword().equals(r.getConfirmPassword())){
            throw new AppException(IdentityErrorCode.INVALID_PASSWORD);
        }

        if(accountRepository.findByUsername(r.getUsername()).isPresent()){
            throw new AppException(IdentityErrorCode.USER_EXISTED); 
        }

        if(accountRepository.findByEmail(r.getEmail()).isPresent()){
            throw new AppException(IdentityErrorCode.EMAIL_EXISTED); 
        }

        Account acc=new Account();
        acc.setUsername(r.getUsername());
        acc.setPasswordHash(encoder.encode(r.getPassword()));
        acc.setEmail(r.getEmail());
        acc.setFullName(r.getFullName());
        acc.setRole(Role.USER);
        acc.setStatus(AccountStatus.ACTIVE);
        
        acc = accountRepository.save(acc);

        // Transactional Outbox: Lưu event vào DB thay vì gửi trực tiếp tới RabbitMQ
        try {
            AccountCreatedEvent event = AccountCreatedEvent.builder()
            .accountId(acc.getId())
            .userName(acc.getUsername())
            .email(acc.getEmail())
            .fullname(r.getFullName())
            .role(acc.getRole().name())
            .phone(r.getPhone())
            .build();

            String payload = objectMapper.writeValueAsString(event);
            
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateId(acc.getId())
                    .type(RabbitMQConstants.IDENTITY_ACCOUNT_CREATED)
                    .payload(payload)
                    .status(OutboxStatus.PENDING)
                    .build();
                    
            outboxEventRepository.save(outboxEvent);
            log.info("Saved AccountCreatedEvent to Outbox for accountId: {}", acc.getId());
        } catch (Exception e) {
           log.error("Failed to serialize and save outbox event: {}", e.getMessage());
           throw new AppException(IdentityErrorCode.UNCATEGORIZED_EXCEPTION);
        }

        return loginAfterRegister(acc, r.getFullName());

    }

    public AuthResponse login(LoginRequest r){

        Account acc= accountRepository
                        .findByUsername(r.getUsername())
                        .orElseThrow(() -> new AppException(IdentityErrorCode.USER_NOT_FOUND));

        if(!encoder.matches(r.getPassword(), acc.getPasswordHash()))
            throw new AppException(IdentityErrorCode.INVALID_CREDENTIALS);

        if (acc.getStatus() == AccountStatus.BANNED) {
            throw new AppException(IdentityErrorCode.ACCOUNT_BANNED);
        }

        String token= jwt.generateToken(acc);

        AuthResponse res = new AuthResponse();
        res.setToken(token);
        res.setUsername(acc.getUsername());
        res.setRole(acc.getRole().name());
        res.setAccountId(acc.getId());
        res.setEmail(acc.getEmail());

        return res;
    }

  private AuthResponse loginAfterRegister(Account acc, String fullname) {
        String token = jwt.generateToken(acc);
        AuthResponse res = new AuthResponse();
        res.setToken(token);
        res.setAccountId(acc.getId());
        res.setUsername(acc.getUsername());
        res.setRole(acc.getRole().name());
        res.setEmail(acc.getEmail());
        res.setFullName(fullname);
        return res;
    }

    @Transactional
    public void forgotPassword(com.oms.identityservice.dto.ForgotPasswordRequest request) {
        String identifier = request.getUsernameOrEmail();
        
        Account acc = accountRepository.findByEmail(identifier)
                .orElseGet(() -> accountRepository.findByUsername(identifier).orElse(null));

        if (acc == null || acc.getStatus() != AccountStatus.ACTIVE) {
            throw new AppException(IdentityErrorCode.USER_NOT_FOUND);
        }

        String otp = String.format("%06d", new Random().nextInt(999999));

        String redisKey = "OTP_FORGOT_PASSWORD:" + acc.getEmail();
        redisTemplate.opsForValue().set(redisKey, otp, 15, TimeUnit.MINUTES);

        try {
            com.oms.identityservice.dto.ForgotPasswordRequestedEvent event = com.oms.identityservice.dto.ForgotPasswordRequestedEvent.builder()
                    .email(acc.getEmail())
                    .username(acc.getUsername())
                    .otp(otp)
                    .message("Yêu cầu lấy lại mật khẩu. Mã OTP của bạn là: " + otp)
                    .build();

            String payload = objectMapper.writeValueAsString(event);
            
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateId(acc.getId())
                    .type(RabbitMQConstants.IDENTITY_FORGOT_PASSWORD_REQUESTED)
                    .payload(payload)
                    .status(OutboxStatus.PENDING)
                    .build();
                    
            outboxEventRepository.save(outboxEvent);
            log.info("Saved ForgotPasswordRequestedEvent to Outbox for email: {}", acc.getEmail());
        } catch (Exception e) {
           log.error("Failed to serialize and save outbox event for forgot password: {}", e.getMessage());
           throw new AppException(IdentityErrorCode.UNCATEGORIZED_EXCEPTION);
        }
    }

    public void verifyOtp(com.oms.identityservice.dto.VerifyOtpRequest request) {
        String identifier = request.getUsernameOrEmail();
        
        Account acc = accountRepository.findByEmail(identifier)
                .orElseGet(() -> accountRepository.findByUsername(identifier).orElse(null));

        if (acc == null || acc.getStatus() != AccountStatus.ACTIVE) {
            throw new AppException(IdentityErrorCode.USER_NOT_FOUND);
        }

        String redisKey = "OTP_FORGOT_PASSWORD:" + acc.getEmail();
        String storedOtp = redisTemplate.opsForValue().get(redisKey);

        if (storedOtp == null || !storedOtp.equals(request.getOtp())) {
            throw new AppException(IdentityErrorCode.INVALID_CREDENTIALS); 
        }
    }

    @Transactional
    public void resetPassword(com.oms.identityservice.dto.ResetPasswordRequest request) {
        String identifier = request.getUsernameOrEmail();
        
        Account acc = accountRepository.findByEmail(identifier)
                .orElseGet(() -> accountRepository.findByUsername(identifier).orElse(null));

        if (acc == null || acc.getStatus() != AccountStatus.ACTIVE) {
            throw new AppException(IdentityErrorCode.USER_NOT_FOUND);
        }

        String redisKey = "OTP_FORGOT_PASSWORD:" + acc.getEmail();
        String storedOtp = redisTemplate.opsForValue().get(redisKey);

        if (storedOtp == null || !storedOtp.equals(request.getOtp())) {
            throw new AppException(IdentityErrorCode.INVALID_CREDENTIALS); 
        }

        acc.setPasswordHash(encoder.encode(request.getNewPassword()));
        accountRepository.save(acc);

        // Delete OTP from Redis
        redisTemplate.delete(redisKey);
        
        log.info("Password successfully reset for account: {}", acc.getEmail());
    }

    @Transactional
    public void changePassword(String accountId, com.oms.identityservice.dto.ChangePasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmNewPassword())) {
            throw new AppException(IdentityErrorCode.INVALID_PASSWORD); // Or a specific error code for password mismatch
        }

        Account acc = accountRepository.findById(accountId)
                .orElseThrow(() -> new AppException(IdentityErrorCode.USER_NOT_FOUND));

        if (!encoder.matches(request.getOldPassword(), acc.getPasswordHash())) {
            throw new AppException(IdentityErrorCode.INVALID_CREDENTIALS); // Or a specific error code for wrong old password
        }

        acc.setPasswordHash(encoder.encode(request.getNewPassword()));
        accountRepository.save(acc);

        log.info("Password successfully changed for account ID: {}", accountId);
    }

    @Transactional
    public AccountResponse changeAccountStatus(String id, AccountStatus status) {
        Account acc = accountRepository.findById(id)
                .orElseThrow(() -> new AppException(IdentityErrorCode.USER_NOT_FOUND));

        acc.setStatus(status);
        acc = accountRepository.save(acc);

        // Redis cache sync for real-time ban checking at API Gateway
        String redisKey = "ACCOUNT_BANNED_STATUS:" + id;
        if (status == AccountStatus.BANNED) {
            redisTemplate.opsForValue().set(redisKey, "BANNED");
            log.info("Banned user saved to Redis cache: {}", id);
        } else {
            redisTemplate.delete(redisKey);
            log.info("Unbanned user removed from Redis cache: {}", id);
        }

        // Transactional Outbox status change event
        try {
            AccountStatusChangedEvent event = AccountStatusChangedEvent.builder()
                    .accountId(acc.getId())
                    .status(acc.getStatus().name())
                    .build();

            String payload = objectMapper.writeValueAsString(event);

            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateId(acc.getId())
                    .type(RabbitMQConstants.IDENTITY_ACCOUNT_STATUS_CHANGED)
                    .payload(payload)
                    .status(OutboxStatus.PENDING)
                    .build();

            outboxEventRepository.save(outboxEvent);
            log.info("Saved AccountStatusChangedEvent to Outbox for accountId: {} with status: {}", acc.getId(), status);
        } catch (Exception e) {
            log.error("Failed to serialize and save outbox status change event: {}", e.getMessage());
            throw new AppException(IdentityErrorCode.UNCATEGORIZED_EXCEPTION);
        }

        return AccountResponse.builder()
                .id(acc.getId())
                .username(acc.getUsername())
                .email(acc.getEmail())
                .role(acc.getRole().name())
                .status(acc.getStatus().name())
                .build();
    }

    public List<AccountResponse> getAccounts() {
        return accountRepository.findAll().stream()
                .map(acc -> AccountResponse.builder()
                        .id(acc.getId())
                        .username(acc.getUsername())
                        .email(acc.getEmail())
                        .role(acc.getRole().name())
                        .status(acc.getStatus() != null ? acc.getStatus().name() : null)
                        .build())
                .collect(Collectors.toList());
    }
}