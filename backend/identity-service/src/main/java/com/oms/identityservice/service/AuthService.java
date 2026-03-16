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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service

public class AuthService {

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    JwtUtil jwt;

    public void register(RegisterRequest r){

        User user=new User();

        user.setFullName(r.fullName);

        user.setCreatedAt(LocalDateTime.now());

        userRepository.save(user);

        Account acc=new Account();

        acc.setUsername(r.username);

        acc.setPasswordHash(
                encoder.encode(r.password));

        acc.setEmail(r.email);

        acc.setRole(Role.USER);

        acc.setStatus(AccountStatus.ACTIVE);

        acc.setCreatedAt(LocalDateTime.now());

        acc.setUser(user);

        accountRepository.save(acc);

    }

    public AuthResponse login(LoginRequest r){

        Account acc=
                accountRepository
                        .findByUsername(r.username)
                        .orElseThrow();

        if(!encoder.matches(
                r.password,
                acc.getPasswordHash()))

            throw new RuntimeException();

        String token=
                jwt.generateToken(acc);

        AuthResponse res=
                new AuthResponse();

        res.token=token;

        res.username=
                acc.getUsername();

        res.role=
                acc.getRole();

        return res;

    }

}