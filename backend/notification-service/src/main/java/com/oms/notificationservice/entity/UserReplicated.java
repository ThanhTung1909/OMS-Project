package com.oms.notificationservice.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users_replicated")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserReplicated {
    @Id
    private String accountId;
    private String email;
    private String fullname;
}
