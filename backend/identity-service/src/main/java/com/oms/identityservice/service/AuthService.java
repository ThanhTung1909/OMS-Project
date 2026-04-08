package com.oms.identityservice.service;


import com.oms.identityservice.dto.AccountCreatedEvent;
import com.oms.identityservice.dto.AuthResponse;
import com.oms.identityservice.dto.LoginRequest;
import com.oms.identityservice.dto.RegisterRequest;
import com.oms.identityservice.entity.Account;
import com.oms.identityservice.entity.Enum.AccountStatus;
import com.oms.identityservice.entity.Enum.ErrorCode;
import com.oms.identityservice.entity.Enum.Role;
import com.oms.identityservice.exception.AppException;
import com.oms.identityservice.repository.AccountRepository;
import com.oms.identityservice.security.JwtUtil;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder encoder;
    private final JwtUtil jwt;
    private final RabbitTemplate rabbitTemplate;

    @Transactional
    public AuthResponse register(RegisterRequest r){

        if(!r.getPassword().equals(r.getConfirmPassword())){
            throw new AppException(ErrorCode.INVALID_PASSWORD);
        }

        if(accountRepository.findByUsername(r.getUsername()).isPresent()){
            throw new AppException(ErrorCode.USER_EXISTED); 
        }

        if(accountRepository.findByEmail(r.getEmail()).isPresent()){
            throw new AppException(ErrorCode.EMAIL_EXISTED); 
        }


        Account acc=new Account();
        acc.setUsername(r.getUsername());
        acc.setPasswordHash(encoder.encode(r.getPassword()));
        acc.setEmail(r.getEmail());
        acc.setRole(Role.USER);
        acc.setStatus(AccountStatus.ACTIVE);
        
        acc = accountRepository.save(acc);

        // Bắn tin nhắn sang profile service
        try {
            AccountCreatedEvent event = AccountCreatedEvent.builder()
            .accountId(acc.getId())
            .userName(acc.getUsername())
            .email(acc.getEmail())
            .fullname(r.getFullName())
            .role(acc.getRole().name())
            .phone(r.getPhone())
            .build();

            rabbitTemplate.convertAndSend("account.created.queue", event);
        } catch (Exception e) {
           log.error("Failed to send message to RabbitMQ: {}", e.getMessage());
        }

        return loginAfterRegister(acc, r.getFullName());

    }

    public AuthResponse login(LoginRequest r){

        Account acc= accountRepository
                        .findByUsername(r.getUsername())
                        .orElseThrow(() -> new RuntimeException("Tài khoản không tồn tại"));

        if(!encoder.matches(r.getPassword(), acc.getPasswordHash()))
            throw new RuntimeException("Sai mật khẩu");

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

}