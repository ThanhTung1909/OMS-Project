package se.iuh.identityapi.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name="user_addresses")

@Getter
@Setter

public class UserAddress {

    @Id
    @GeneratedValue(strategy= GenerationType.UUID)

    private String id;

    private String street;

    private String ward;

    private String district;

    private String city;

    private boolean isDefault;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @ManyToOne

    @JoinColumn(name="user_id")

    private User user;

}