package com.oms.identityservice.entity;


import com.oms.identityservice.entity.Enum.AccountStatus;
import com.oms.identityservice.entity.Enum.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;


import java.time.LocalDateTime;

@Entity
@Table(name="accounts")

@Getter
@Setter

public class Account {

    @Id
    @GeneratedValue(strategy= GenerationType.UUID)
    private String id;

    @Column(unique=true)
    private String username;

    private String passwordHash;

    @Column(unique=true)
    private String email;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Enumerated(EnumType.STRING)
    private AccountStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;


}