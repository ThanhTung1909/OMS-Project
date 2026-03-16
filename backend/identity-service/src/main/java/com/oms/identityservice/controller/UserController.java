package com.oms.identityservice.controller;

import com.oms.identityservice.entity.User;
import com.oms.identityservice.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


import java.util.List;

@RestController

@RequestMapping("/api/users")

public class UserController {

    @Autowired
    UserService userService;

    @GetMapping

    public List<User> getAll(){

        return userService.getAll();

    }

    @GetMapping("/{id}")

    public User getById(

            @PathVariable String id){

        return userService.getById(id);

    }

}
