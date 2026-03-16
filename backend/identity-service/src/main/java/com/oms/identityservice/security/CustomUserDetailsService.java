package com.oms.identityservice.security;

import com.oms.identityservice.entity.Account;
import com.oms.identityservice.repository.AccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;


@Service

public class CustomUserDetailsService
        implements UserDetailsService {

    @Autowired
    AccountRepository accountRepository;

    @Override
    public UserDetails loadUserByUsername(
            String username)
            throws UsernameNotFoundException {

        Account acc =
                accountRepository
                        .findByUsername(username)
                        .orElseThrow(() ->
                                new UsernameNotFoundException(
                                        "User not found"));

        return org.springframework.security
                .core.userdetails.User

                .builder()

                .username(acc.getUsername())

                .password(acc.getPasswordHash())

                .roles(acc.getRole().name())

                .build();

    }

}