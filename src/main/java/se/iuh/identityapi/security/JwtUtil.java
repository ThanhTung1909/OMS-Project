package se.iuh.identityapi.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import se.iuh.identityapi.entity.Account;

import java.util.Date;

@Component

public class JwtUtil {

    @Value("${jwt.secret}")

    private String secret;

    public String generateToken(Account acc){

        return Jwts.builder()

                .setSubject(acc.getUsername())

                .claim("role",
                        acc.getRole().name())

                .setIssuedAt(new Date())

                .setExpiration(
                        new Date(
                                System.currentTimeMillis()
                                        +86400000))

                .signWith(
                        Keys.hmacShaKeyFor(
                                secret.getBytes()))

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