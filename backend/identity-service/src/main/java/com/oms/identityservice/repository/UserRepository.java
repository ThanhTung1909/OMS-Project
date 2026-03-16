package com.oms.identityservice.repository;

import com.oms.identityservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface UserRepository
        extends JpaRepository<User,String>{

}
