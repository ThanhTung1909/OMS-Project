package se.iuh.identityapi.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import se.iuh.identityapi.entity.Enum.Gender;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name="users")

@Getter
@Setter

public class User {

    @Id
    @GeneratedValue(strategy= GenerationType.UUID)

    private String id;

    private String fullName;

    private String phone;

    private String avatarUrl;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Enumerated(EnumType.STRING)

    private Gender gender;

    private LocalDate dateOfBirth;

    @OneToMany(mappedBy="user",
            cascade=CascadeType.ALL)

    private List<UserAddress> addresses;

}