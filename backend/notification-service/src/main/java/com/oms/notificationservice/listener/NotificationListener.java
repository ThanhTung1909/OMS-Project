package com.oms.notificationservice.listener;

import com.oms.notificationservice.config.RabbitMQConfig;
import com.oms.notificationservice.client.OrderClient;
import com.oms.notificationservice.dto.AccountCreatedEvent;
import com.oms.notificationservice.dto.DeliveryUpdatePayload;
import com.oms.notificationservice.dto.NotificationEvent;
import com.oms.notificationservice.entity.UserReplicated;
import com.oms.notificationservice.repository.UserReplicatedRepository;
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
    private final UserReplicatedRepository userReplicatedRepository;
    private final com.oms.notificationservice.repository.NotificationLogRepository notificationLogRepository;

    /**
     * Lắng nghe sự kiện tạo tài khoản thành công
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_NOTIFICATION_ACCOUNT_CREATE)
    public void handleAccountCreated(AccountCreatedEvent event) {
        log.info("Nhận sự kiện tạo tài khoản cho user: {}. Tiến hành đồng bộ dữ liệu.", event.getUserName());
        
        // 1. Đồng bộ dữ liệu người dùng (Data Replication)
        UserReplicated user = UserReplicated.builder()
                .accountId(event.getAccountId())
                .email(event.getEmail())
                .fullname(event.getFullname())
                .build();
        userReplicatedRepository.save(user);
        log.info("Đã đồng bộ thông tin người dùng {} vào DB nội bộ của Notification Service.", event.getAccountId());

        // 2. Gửi email chào mừng
        String subject = "Chào mừng bạn đến với OMS!";
        String body = String.format("Chào %s,\n\nTài khoản của bạn đã được tạo thành công với vai trò %s.\nChúc bạn có trải nghiệm tuyệt vời!", 
                event.getFullname(), event.getRole());
        
        emailService.sendEmail(event.getEmail(), subject, body);
    }

    /**
     * Lắng nghe các sự kiện cập nhật trạng thái đơn hàng (CONFIRMED, SHIPPING, etc.)
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_NOTIFICATION_ORDER_STATUS)
    public void handleOrderStatusUpdate(NotificationEvent event) {
        log.info("Nhận sự kiện cập nhật trạng thái cho đơn hàng: {}, Status: {}", event.getOrderId(), event.getStatus());
        
        try {
            String rawUserId = event.getUserId();
            if (rawUserId == null) {
                // Fallback nếu event cũ không có userId
                rawUserId = orderClient.getUserIdByOrderId(event.getOrderId());
            }
            final String userId = rawUserId; // Effectively final for use in lambda

            // 1. Lưu In-App Notification
            String title = "Cập nhật đơn hàng";
            String content = event.getMessage() != null ? event.getMessage() : "Đơn hàng của bạn đã chuyển sang trạng thái: " + event.getStatus();
            
            com.oms.notificationservice.entity.NotificationLog logEntry = com.oms.notificationservice.entity.NotificationLog.builder()
                    .userId(userId)
                    .title(title)
                    .content(content)
                    .build();
            notificationLogRepository.save(logEntry);
            log.info("Đã lưu In-App Notification (Order Status) cho user: {}", userId);

            // 2. Gửi Email (Lấy Email từ Database cục bộ)
            userReplicatedRepository.findByAccountId(userId).ifPresentOrElse(user -> {
                String subject = "Cập nhật đơn hàng #" + event.getOrderId();
                String body = String.format("Chào bạn,\n\nĐơn hàng #%s của bạn đã có cập nhật: %s\n%s", 
                        event.getOrderId(), event.getStatus(), content);
                
                emailService.sendEmail(user.getEmail(), subject, body);
            }, () -> log.warn("Không tìm thấy thông tin email cho user {} trong DB nội bộ để gửi thông báo đơn hàng.", userId));
            
        } catch (Exception e) {
            log.error("Lỗi khi xử lý thông báo trạng thái đơn hàng: {}", e.getMessage());
        }
    }

    /**
     * Lắng nghe sự kiện từ Delivery Service (không có sẵn Email khách hàng trong Payload)
     * Đã được tinh chỉnh để hỗ trợ Retry Mechanism khi Service phụ trợ (Order/Identity) gặp sự cố
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_NOTIFICATION_DELIVERY_STATUS)
    public void handleDeliveryStatusUpdate(DeliveryUpdatePayload payload) {
        log.info("Nhận sự kiện vận chuyển cho đơn hàng: {}", payload.getOrderId());

        try {
            // Bước 1: Gọi Order Service để lấy accountId (được lưu trong trường userId của đơn hàng)
            log.info("[SYNC] Đang lấy userId từ Order Service...");
            String userId = orderClient.getUserIdByOrderId(payload.getOrderId());
            if (userId == null) {
                log.error("LỖI DỮ LIỆU: Không tìm thấy userId cho đơn hàng {}. Bỏ qua.", payload.getOrderId());
                return;
            }

            // Bước 2: Tạo In-App Notification Log
            String title = "Cập nhật vận chuyển";
            String content = String.format("Đơn hàng %s đang ở trạng thái: %s. Shipper: %s - %s. Mã vận đơn: %s",
                    payload.getOrderId(), payload.getStatus(), payload.getShipperName(), payload.getShipperPhone(), payload.getTrackingNumber());
            
            com.oms.notificationservice.entity.NotificationLog logEntry = com.oms.notificationservice.entity.NotificationLog.builder()
                    .userId(userId)
                    .title(title)
                    .content(content)
                    .build();
            notificationLogRepository.save(logEntry);
            log.info("Đã lưu In-App Notification cho user: {}", userId);

            // Bước 3: Điều kiện gửi Email (CHỈ gửi nếu là DELIVERING hoặc DELIVERED)
            String status = payload.getStatus();
            if ("DELIVERING".equalsIgnoreCase(status) || "DELIVERED".equalsIgnoreCase(status)) {
                log.info("[EMAIL] Đang xử lý gửi email cho trạng thái: {}", status);
                
                // Cần lấy Email từ Database cục bộ (Data Replication)
                userReplicatedRepository.findByAccountId(userId).ifPresentOrElse(user -> {
                    String subject = "Cập nhật trạng thái vận chuyển đơn hàng #" + payload.getOrderId();
                    String body = String.format("Chào bạn,\n\nĐơn hàng #%s của bạn đã chuyển sang trạng thái: %s.\nChi tiết shipper: %s - %s.\nCảm ơn bạn đã sử dụng dịch vụ!",
                            payload.getOrderId(), status, payload.getShipperName(), payload.getShipperPhone());
                    
                    emailService.sendEmail(user.getEmail(), subject, body);
                    log.info("Đã gửi email vận chuyển tới {} thành công.", user.getEmail());
                }, () -> log.warn("Không tìm thấy thông tin email cho user {} trong DB nội bộ để gửi thông báo vận chuyển.", userId));
            } else {
                log.info("[EMAIL] Trạng thái {} không nằm trong danh sách gửi email. Bỏ qua.", status);
            }
            
        } catch (Exception e) {
            log.warn("[CASCADING FAILURE] Có lỗi khi xử lý thông báo. Đang kích hoạt cơ chế Retry... Lỗi: {}", e.getMessage());
            throw e;
        }
    }
}
