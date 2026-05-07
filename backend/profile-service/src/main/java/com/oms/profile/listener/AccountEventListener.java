package com.oms.profile.listener;


import com.oms.profile.dto.AccountCreatedEvent;
import com.oms.profile.entity.Customer;
import com.oms.profile.entity.Staff;
import com.oms.profile.repository.CustomerRepository;
import com.oms.profile.repository.StaffRepository;
import com.oms.profile.entity.Department;
import com.oms.profile.entity.ProcessedEvent;
import com.oms.profile.repository.DepartmentRepository;
import com.oms.profile.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class AccountEventListener {

    private final CustomerRepository customerRepository;
    private final StaffRepository staffRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final DepartmentRepository departmentRepository;

    @RabbitListener(queues = com.oms.profile.config.RabbitMQConfig.QUEUE_PROFILE_ACCOUNT_CREATE)
    @Transactional
    public void handleAccountCreated(AccountCreatedEvent event) {
        log.info("Nhận sự kiện tạo tài khoản: {} với vai trò: {}", event.getUserName(), event.getRole());

        // BƯỚC 1: Kiểm tra Idempotency
        if (processedEventRepository.existsById(event.getAccountId())) {
            log.warn("Sự kiện cho tài khoản {} đã được xử lý trước đó. Bỏ qua.", event.getAccountId());
            return;
        }

        if ("USER".equalsIgnoreCase(event.getRole())) {
            
            Customer customer = new Customer();
            
            customer.setAccountId(event.getAccountId());
            customer.setFullname(event.getFullname());
            customer.setPhone(event.getPhone());
            customer.setActive(true);

            customerRepository.save(customer);
            log.info("Đã tạo xong hồ sơ Khách hàng cho: {}", event.getUserName());

        } else if ("STAFF".equalsIgnoreCase(event.getRole()) || "ADMIN".equalsIgnoreCase(event.getRole())) {
            
            Staff staff = new Staff();
            staff.setAccountId(event.getAccountId());
            staff.setFullname(event.getFullname());
            staff.setPhone(event.getPhone());
            staff.setEmployeeCode("NV-" + System.currentTimeMillis()); 
            staff.setActive(true);
        
            // BƯỚC 2: Gán Department mặc định
            Department defaultDept = departmentRepository.findByName("Chưa phân bổ")
                    .orElseGet(() -> {
                        Department dept = new Department();
                        dept.setName("Chưa phân bổ");
                        return departmentRepository.save(dept);
                    });
            staff.setDepartment(defaultDept);

            staffRepository.save(staff);
            log.info("Đã tạo xong hồ sơ Nhân viên cho: {}", event.getUserName());
        }

        // BƯỚC 3: Lưu trạng thái đã xử lý (Idempotency)
        ProcessedEvent processedEvent = ProcessedEvent.builder()
                .accountId(event.getAccountId())
                .eventType("ACCOUNT_CREATED")
                .processedAt(LocalDateTime.now())
                .build();
        processedEventRepository.save(processedEvent);
    }
}