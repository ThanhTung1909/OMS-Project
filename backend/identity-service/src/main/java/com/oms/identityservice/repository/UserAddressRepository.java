package com.oms.identityservice.repository;

import com.oms.identityservice.entity.UserAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface UserAddressRepository
        extends JpaRepository<UserAddress,String> {

}