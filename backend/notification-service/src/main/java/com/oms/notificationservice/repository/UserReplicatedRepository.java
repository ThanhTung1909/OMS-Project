package com.oms.notificationservice.repository;

import com.oms.notificationservice.entity.UserReplicated;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserReplicatedRepository extends JpaRepository<UserReplicated, String> {
    Optional<UserReplicated> findByAccountId(String accountId);
}
