package com.oms.identityservice.service;


import com.oms.identityservice.dto.AuthResponse;
import com.oms.identityservice.dto.LoginRequest;
import com.oms.identityservice.dto.RegisterRequest;
import com.oms.identityservice.entity.Account;
import com.oms.identityservice.entity.Enum.AccountStatus;
import com.oms.identityservice.entity.Enum.Role;
import com.oms.identityservice.entity.User;
import com.oms.identityservice.repository.AccountRepository;
import com.oms.identityservice.repository.UserRepository;
import com.oms.identityservice.security.JwtUtil;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    @Autowired
    AccountRepository accountRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    PasswordEncoder encoder;
    @Autowired
    JwtUtil jwt;

    @Transactional
    public void register(RegisterRequest r){

        User user=new User();
        user.setFullName(r.getFullName());
        user.setCreatedAt(LocalDateTime.now());

        user = userRepository.save(user);

        Account acc=new Account();
        acc.setUsername(r.getUsername());
        acc.setPasswordHash(encoder.encode(r.getPassword()));
        acc.setEmail(r.getEmail());
        acc.setRole(Role.USER);
        acc.setStatus(AccountStatus.ACTIVE);
        acc.setCreatedAt(LocalDateTime.now());
        acc.setUser(user);

        accountRepository.save(acc);

    }

    public AuthResponse login(LoginRequest r){

        Account acc= accountRepository
                        .findByUsername(r.getUsername())
                        .orElseThrow(() -> new RuntimeException("Tài khoản không tồn tại"));

        if(!encoder.matches(r.getPassword(), acc.getPasswordHash()))
            throw new RuntimeException("Sai mật khẩu");

        String token= jwt.generateToken(acc);

        AuthResponse res= new AuthResponse();

        res.setToken(token);
        res.setUsername(acc.getUsername());
        res.setRole(acc.getRole());

        return res;

    }

}