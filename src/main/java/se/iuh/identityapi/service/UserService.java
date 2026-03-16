package se.iuh.identityapi.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import se.iuh.identityapi.entity.User;
import se.iuh.identityapi.repository.UserRepository;

import java.util.List;

@Service

public class UserService {

    @Autowired
    UserRepository userRepository;

    public List<User> getAll(){

        return userRepository.findAll();

    }

    public User getById(String id){

        return userRepository
                .findById(id)
                .orElseThrow();

    }

}
