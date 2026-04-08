package com.oms.profile.repository;

import com.oms.profile.entity.Staff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;


@Repository
public interface StaffRepository extends JpaRepository<Staff, String> {


    Optional<Staff> findByAccountId(String accountId);

    Optional<Staff> findByEmployeeCode(String employeeCode);
}
