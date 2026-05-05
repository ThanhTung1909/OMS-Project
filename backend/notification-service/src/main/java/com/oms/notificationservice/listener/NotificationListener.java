package com.oms.notificationservice.listener;

import com.oms.notificationservice.config.RabbitMQConfig;
import com.oms.notificationservice.dto.AccountCreatedEvent;
import com.oms.notificationservice.dto.NotificationEvent;
import com.oms.notificationservice.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Lớp lắng nghe các sự kiện từ RabbitMQ để thực hiện gửi thông báo
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationListener {

    private final EmailService emailService;

    /**
     * Lắng nghe sự kiện tạo tài khoản thành công
     */
    @RabbitListener(queues = RabbitMQConfig.NOTIFICATION_AUTH_QUEUE)
    public void handleAccountCreated(AccountCreatedEvent event) {
        log.info("Nhận sự kiện tạo tài khoản cho user: {}", event.getUserName());
        
        String subject = "Chào mừng bạn đến với OMS!";
        String body = String.format("Chào %s,\n\nTài khoản của bạn đã được tạo thành công với vai trò %s.\nChúc bạn có trải nghiệm tuyệt vời!", 
                event.getFullname(), event.getRole());
        
        emailService.sendEmail(event.getEmail(), subject, body);
    }

    /**
     * Lắng nghe các sự kiện cập nhật trạng thái đơn hàng và vận chuyển
     */
    @RabbitListener(queues = RabbitMQConfig.NOTIFICATION_ORDER_QUEUE)
    public void handleOrderStatusUpdate(NotificationEvent event) {
        log.info("Nhận sự kiện cập nhật trạng thái cho đơn hàng: {}", event.getOrderId());
        
        String subject = "Cập nhật trạng thái đơn hàng #" + event.getOrderId();
        String body = String.format("Chào %s,\n\nĐơn hàng #%s của bạn đã chuyển sang trạng thái: %s.\n%s", 
                event.getCustomerName(), event.getOrderId(), event.getStatus(), 
                event.getMessage() != null ? event.getMessage() : "");
        
        emailService.sendEmail(event.getCustomerEmail(), subject, body);
    }
}
