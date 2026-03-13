package se.iuh.identityapi.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import se.iuh.identityapi.entity.UserAddress;

@Repository
public interface UserAddressRepository
        extends JpaRepository<UserAddress,String> {

}