package com.oms.notificationservice.listener;

import com.oms.notificationservice.config.RabbitMQConfig;
import com.oms.notificationservice.client.AccountClient;
import com.oms.notificationservice.client.OrderClient;
import com.oms.notificationservice.dto.AccountCreatedEvent;
import com.oms.notificationservice.dto.DeliveryUpdatePayload;
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
    private final OrderClient orderClient;
    private final AccountClient accountClient;

    /**
     * Lắng nghe sự kiện tạo tài khoản thành công
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_NOTIFICATION_ACCOUNT_CREATE)
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
    @RabbitListener(queues = RabbitMQConfig.QUEUE_NOTIFICATION_ORDER_STATUS)
    public void handleOrderStatusUpdate(NotificationEvent event) {
        log.info("Nhận sự kiện cập nhật trạng thái cho đơn hàng: {}", event.getOrderId());
        
        String subject = "Cập nhật trạng thái đơn hàng #" + event.getOrderId();
        String body = String.format("Chào %s,\n\nĐơn hàng #%s của bạn đã chuyển sang trạng thái: %s.\n%s", 
                event.getCustomerName(), event.getOrderId(), event.getStatus(), 
                event.getMessage() != null ? event.getMessage() : "");
        
        emailService.sendEmail(event.getCustomerEmail(), subject, body);
    }

    /**
     * Lắng nghe sự kiện từ Delivery Service (không có sẵn Email khách hàng trong Payload)
     * Đã được tinh chỉnh để hỗ trợ Retry Mechanism khi Service phụ trợ (Order/Identity) gặp sự cố
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_NOTIFICATION_DELIVERY_STATUS)
    public void handleDeliveryStatusUpdate(DeliveryUpdatePayload payload) {
        log.info("Nhận sự kiện vận chuyển cho đơn hàng: {}", payload.getOrderId());

        // Bước 1: Gọi Order Service để lấy userId
        var orderRes = orderClient.getOrderById(payload.getOrderId());
        if (orderRes == null || !orderRes.isSuccess() || orderRes.getResult() == null) {
            log.error("LỖI DỮ LIỆU: Không tìm thấy đơn hàng {} để gửi thông báo. Bỏ qua để tránh treo Queue.", payload.getOrderId());
            return; // Lỗi logic (404) -> Không retry vì dữ liệu không hợp lệ
        }
        String userId = orderRes.getResult().getUserId();

        // Bước 2: Gọi Identity Service để lấy Email khách hàng
        var accountRes = accountClient.getAccountById(userId);
        if (accountRes == null || !accountRes.isSuccess() || accountRes.getResult() == null) {
            log.error("LỖI DỮ LIỆU: Không tìm thấy tài khoản {} để gửi thông báo. Bỏ qua.", userId);
            return; // Lỗi logic (404) -> Không retry
        }
        String customerEmail = accountRes.getResult().getEmail();
        String customerName = accountRes.getResult().getUsername();

        // Bước 3: Gửi email thông báo
        String subject = "Cập nhật trạng thái vận chuyển đơn hàng #" + payload.getOrderId();
        String statusDesc = "COMPLETED".equals(payload.getStatus()) ? "GIAO HÀNG THÀNH CÔNG" : "GIAO HÀNG THẤT BẠI";
        
        String body = String.format("Chào %s,\n\nĐơn hàng #%s của bạn đã có cập nhật vận chuyển: %s.\n%s", 
                customerName, payload.getOrderId(), statusDesc, 
                payload.getFailReason() != null ? "Lý do: " + payload.getFailReason() : "");

        emailService.sendEmail(customerEmail, subject, body);
        log.info("Đã gửi lệnh gửi mail tới {} thành công.", customerEmail);
    }
}
