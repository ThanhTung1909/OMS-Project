package se.iuh.identityapi.service;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import se.iuh.identityapi.dto.AuthResponse;
import se.iuh.identityapi.dto.LoginRequest;
import se.iuh.identityapi.dto.RegisterRequest;
import se.iuh.identityapi.entity.Account;
import se.iuh.identityapi.entity.Enum.AccountStatus;
import se.iuh.identityapi.entity.Enum.Role;
import se.iuh.identityapi.entity.User;
import se.iuh.identityapi.repository.AccountRepository;
import se.iuh.identityapi.repository.UserRepository;
import se.iuh.identityapi.security.JwtUtil;

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