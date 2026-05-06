package com.oms.notificationservice;

import com.oms.notificationservice.dto.AccountCreatedEvent;
import com.oms.notificationservice.entity.NotificationHistory;
import com.oms.notificationservice.listener.NotificationListener;
import com.oms.notificationservice.repository.NotificationHistoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
public class NotificationIntegrationTest {

    @Autowired
    private NotificationListener notificationListener;

    @Autowired
    private NotificationHistoryRepository historyRepository;

    @Test
    public void testHandleAccountCreated_ShouldSaveHistory() {
        // 1. Chuẩn bị dữ liệu sự kiện
        AccountCreatedEvent event = AccountCreatedEvent.builder()
                .email("test@example.com")
                .fullname("Nguyen Van A")
                .role("USER")
                .userName("testuser")
                .build();

        // 2. Gọi listener xử lý (giả lập nhận được message từ RabbitMQ)
        notificationListener.handleAccountCreated(event);

        // 3. Kiểm tra xem lịch sử có được lưu vào database không
        List<NotificationHistory> history = historyRepository.findByRecipientOrderByCreatedAtDesc("test@example.com");
        
        assertFalse(history.isEmpty(), "Lịch sử thông báo không được để trống");
        assertEquals("Chào mừng bạn đến với OMS!", history.get(0).getSubject());
        assertEquals("SENT", history.get(0).getStatus());
        System.out.println("Test gửi mail và lưu lịch sử thành công!");
    }
}
