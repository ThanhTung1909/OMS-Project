package com.oms.profile.listener;


import com.oms.profile.dto.AccountCreatedEvent;
import com.oms.profile.entity.Customer;
import com.oms.profile.entity.Staff;
import com.oms.profile.repository.CustomerRepository;
import com.oms.profile.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AccountEventListener {

    private final CustomerRepository customerRepository;
    private final StaffRepository staffRepository;

    @RabbitListener(queues = "account.created.queue")
    public void handleAccountCreated(AccountCreatedEvent event) {
        log.info("Nhận sự kiện tạo tài khoản: {} với vai trò: {}", event.getUserName(), event.getRole());

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
        
            staffRepository.save(staff);
            log.info("Đã tạo xong hồ sơ Nhân viên cho: {}", event.getUserName());
        }
    }
}