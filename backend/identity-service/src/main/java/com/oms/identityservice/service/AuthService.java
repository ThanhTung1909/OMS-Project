package com.oms.identityservice.service;


import com.oms.identityservice.dto.AccountCreatedEvent;
import com.oms.identityservice.dto.AuthResponse;
import com.oms.identityservice.dto.LoginRequest;
import com.oms.identityservice.dto.RegisterRequest;
import com.oms.identityservice.entity.Account;
import com.oms.identityservice.entity.Enum.AccountStatus;
import com.oms.identityservice.entity.Enum.Role;
import com.oms.identityservice.repository.AccountRepository;
import com.oms.identityservice.security.JwtUtil;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder encoder;
    private final JwtUtil jwt;
    private final RabbitTemplate rabbitTemplate;

    @Transactional
    public AuthResponse register(RegisterRequest r){

        if(!r.getPassword().equals(r.getConfirmPassword())){
            throw new RuntimeException("Mật khẩu nhập lại không khớp với mật khẩu đã nhập");
        }

        if(accountRepository.findByUsername(r.getUsername()).isPresent()){
            throw new RuntimeException("Tên đăng nhập này đã tồn tại trên hệ thống");
        }

        if(accountRepository.findByEmail(r.getEmail()).isPresent()){
            throw new RuntimeException("Email này đã được sử dụng bởi một tài khoản khác");
        }

        Account acc=new Account();
        acc.setUsername(r.getUsername());
        acc.setPasswordHash(encoder.encode(r.getPassword()));
        acc.setEmail(r.getEmail());
        acc.setRole(Role.USER);
        acc.setStatus(AccountStatus.ACTIVE);
        acc.setCreatedAt(LocalDateTime.now());
        
        acc = accountRepository.save(acc);

        // Bắn tin nhắn sang profile service
        AccountCreatedEvent event = AccountCreatedEvent.builder()
            .accountId(acc.getId())
            .userName(acc.getUsername())
            .email(acc.getEmail())
            .fullname(r.getFullName())
            .role(acc.getRole().name())
            .phone(r.getPhone())
            .build();

        rabbitTemplate.convertAndSend("account.created.queue", event);

        return loginAfterRegister(acc);

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

  private AuthResponse loginAfterRegister(Account acc) {
        String token = jwt.generateToken(acc);
        AuthResponse res = new AuthResponse();
        res.setToken(token);
        res.setAccountId(acc.getId());
        res.setUsername(acc.getUsername());
        res.setRole(acc.getRole().name());
        res.setEmail(acc.getEmail());
        return res;
    }

}