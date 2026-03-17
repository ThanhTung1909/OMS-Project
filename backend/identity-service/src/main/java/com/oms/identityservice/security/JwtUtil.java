package com.oms.identityservice.security;

import com.oms.identityservice.entity.Account;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


import java.util.Date;

@Component

public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;


    public String generateToken(Account acc){

        return Jwts.builder()
                .setSubject(acc.getUsername())
                .claim("role", acc.getRole().name())
                .claim("userId", acc.getUser().getId())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes()))
                .compact();

    }

    public String getUsername(String token){

        return Jwts.parserBuilder()

                .setSigningKey(secret.getBytes())

                .build()

                .parseClaimsJws(token)

                .getBody()

                .getSubject();

    }

}