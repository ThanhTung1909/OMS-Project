package se.iuh.identityapi.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import se.iuh.identityapi.entity.User;

@Repository
public interface UserRepository
        extends JpaRepository<User,String>{

}
