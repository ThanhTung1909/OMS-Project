package com.oms.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@Slf4j
public class InternalAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String accountId = request.getHeader("X-Account-Id");
        String role = request.getHeader("X-User-Role");

        if (accountId != null && role != null) {
            // Map Role sang Authority với prefix ROLE_ (Ví dụ: ADMIN -> ROLE_ADMIN)
            String authority = role.startsWith("ROLE_") ? role : "ROLE_" + role;
            
            log.info("[SECURITY] Authenticated Account: {} with Role: {}", accountId, authority);
            
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    accountId, null, Collections.singletonList(new SimpleGrantedAuthority(authority)));
            
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        
        filterChain.doFilter(request, response);
    }
}
