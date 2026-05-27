package com.oms.identityservice.config;

import com.oms.common.constant.RabbitMQConstants;
import com.oms.identityservice.dto.AccountCreatedEvent;
import com.oms.identityservice.entity.Account;
import com.oms.identityservice.entity.OutboxEvent;
import com.oms.identityservice.entity.Enum.AccountStatus;
import com.oms.identityservice.entity.Enum.OutboxStatus;
import com.oms.identityservice.entity.Enum.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.identityservice.repository.AccountRepository;
import com.oms.identityservice.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DatabaseSeeder {

    private final PasswordEncoder passwordEncoder;
    private final AccountRepository accountRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Bean
    @Order(1)
    public CommandLineRunner seedData() {
        return args -> {
            // 1. Seed tài khoản Admin (Tuân thủ Validation mới)
            if (accountRepository.findByUsername("admin").isEmpty()) {
                Account admin = new Account();
                admin.setUsername("admin");
                admin.setPasswordHash(passwordEncoder.encode("Admin123")); 
                admin.setEmail("admin@oms.com");
                admin.setFullName("Quản trị viên hệ thống");
                admin.setRole(Role.ADMIN);
                admin.setStatus(AccountStatus.ACTIVE);
                
                accountRepository.save(admin);

                AccountCreatedEvent event = AccountCreatedEvent.builder()
                    .accountId(admin.getId())
                    .userName(admin.getUsername())
                    .email(admin.getEmail())
                    .fullname("Quản trị viên")
                    .role(admin.getRole().name())
                    .phone("0987654321")
                    .build();

                try {
                    OutboxEvent outboxEvent = OutboxEvent.builder()
                            .aggregateId(admin.getId())
                            .type(RabbitMQConstants.IDENTITY_ACCOUNT_CREATED)
                            .payload(objectMapper.writeValueAsString(event))
                            .status(OutboxStatus.PENDING)
                            .build();

                    outboxEventRepository.save(outboxEvent);
                    log.info("Đã tạo tài khoản Admin mặc định (admin/Admin123)");
                } catch (Exception e) {
                    log.error("Lỗi khi lưu Outbox event cho Admin: {}", e.getMessage());
                }
            }

            // 2. Seed tài khoản User mẫu
            if (accountRepository.findByUsername("user").isEmpty()) {
                Account user = new Account();
                user.setUsername("user");
                user.setPasswordHash(passwordEncoder.encode("User123"));
                user.setEmail("user@oms.com");
                user.setFullName("Người dùng mẫu");
                user.setRole(Role.USER);
                user.setStatus(AccountStatus.ACTIVE);
                
                accountRepository.save(user);

                AccountCreatedEvent event = AccountCreatedEvent.builder()
                    .accountId(user.getId())
                    .userName(user.getUsername())
                    .email(user.getEmail())
                    .fullname("Người dùng mẫu")
                    .role(user.getRole().name())
                    .phone("0912345678")
                    .build();

                try {
                    OutboxEvent outboxEvent = OutboxEvent.builder()
                            .aggregateId(user.getId())
                            .type(RabbitMQConstants.IDENTITY_ACCOUNT_CREATED)
                            .payload(objectMapper.writeValueAsString(event))
                            .status(OutboxStatus.PENDING)
                            .build();

                    outboxEventRepository.save(outboxEvent);
                    log.info("Đã tạo tài khoản User mẫu (user/User123)");
                } catch (Exception e) {
                    log.error("Lỗi khi lưu Outbox event cho User: {}", e.getMessage());
                }
            }
            // 3. Seed tài khoản Shipper mẫu
            if (accountRepository.findByUsername("shipper").isEmpty()) {
                Account shipper = new Account();
                shipper.setId("shipper_demo");
                shipper.setUsername("shipper");
                shipper.setPasswordHash(passwordEncoder.encode("Shipper123"));
                shipper.setEmail("shipper@oms.com");
                shipper.setFullName("Shipper Demo");
                shipper.setRole(Role.STAFF);
                shipper.setStatus(AccountStatus.ACTIVE);
                
                accountRepository.save(shipper);

                AccountCreatedEvent event = AccountCreatedEvent.builder()
                    .accountId(shipper.getId())
                    .userName(shipper.getUsername())
                    .email(shipper.getEmail())
                    .fullname("Shipper Demo")
                    .role(shipper.getRole().name())
                    .phone("0988888888")
                    .build();

                try {
                    OutboxEvent outboxEvent = OutboxEvent.builder()
                            .aggregateId(shipper.getId())
                            .type(RabbitMQConstants.IDENTITY_ACCOUNT_CREATED)
                            .payload(objectMapper.writeValueAsString(event))
                            .status(OutboxStatus.PENDING)
                            .build();

                    outboxEventRepository.save(outboxEvent);
                    log.info("Đã tạo tài khoản Shipper mẫu (shipper/Shipper123) với ID shipper_demo");
                } catch (Exception e) {
                    log.error("Lỗi khi lưu Outbox event cho Shipper: {}", e.getMessage());
                }
            }
        };
    }
}
