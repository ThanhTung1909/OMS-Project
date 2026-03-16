package com.oms.identityservice.service;

import com.oms.identityservice.entity.User;
import com.oms.identityservice.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


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
