package com.oms.identityservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.oms.identityservice", "com.oms.common"})
public class IdentityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityServiceApplication.class, args);
    }

}
