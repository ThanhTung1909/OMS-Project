package com.oms.notificationservice;

import com.oms.notificationservice.dto.AccountCreatedEvent;
import com.oms.notificationservice.listener.NotificationListener;
import com.oms.notificationservice.service.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Bài kiểm tra Unit Test thuần túy (không cần Database/RabbitMQ)
 * Kiểm tra logic xử lý sự kiện trong NotificationListener
 */
@ExtendWith(MockitoExtension.class)
public class NotificationListenerUnitTest {

    @Mock
    private EmailService emailService;

    @InjectMocks
    private NotificationListener notificationListener;

    @Test
    public void testHandleAccountCreated_Logic() {
        // 1. Chuẩn bị dữ liệu
        AccountCreatedEvent event = AccountCreatedEvent.builder()
                .email("test@example.com")
                .fullname("Tài")
                .role("ADMIN")
                .userName("tai_admin")
                .build();

        // 2. Chạy listener
        notificationListener.handleAccountCreated(event);

        // 3. Xác nhận xem EmailService có được gọi đúng tham số không
        verify(emailService).sendEmail(
                eq("test@example.com"), 
                eq("Chào mừng bạn đến với OMS!"), 
                anyString()
        );
        
        System.out.println("Unit Test logic xử lý sự kiện thành công!");
    }
}
