package com.oms.notificationservice.service;

import com.oms.notificationservice.entity.NotificationHistory;
import com.oms.notificationservice.repository.NotificationHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Dịch vụ xử lý gửi Email và lưu lịch sử thông báo
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final NotificationHistoryRepository historyRepository;

    /**
     * Gửi email và lưu vào cơ sở dữ liệu
     * @param to Địa chỉ người nhận
     * @param subject Tiêu đề email
     * @param body Nội dung email
     */
    @Async
    public void sendEmail(String to, String subject, String body) {
        log.info("Đang chuẩn bị gửi mail tới: {}, tiêu đề: {}", to, subject);
        String status = "SENT";
        
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("oms-notification@gmail.com");
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            
            // mailSender.send(message); // Thực tế sẽ mở comment này khi có SMTP thật
            log.info("Gửi mail thành công (GIẢ LẬP) tới {}", to);
            
        } catch (Exception e) {
            status = "FAILED";
            log.error("Lỗi khi gửi mail tới {}: {}", to, e.getMessage());
        } finally {
            // Lưu lịch sử vào database cho dù gửi thành công hay thất bại
            NotificationHistory history = NotificationHistory.builder()
                    .recipient(to)
                    .subject(subject)
                    .body(body)
                    .status(status)
                    .build();
            historyRepository.save(history);
            log.info("Đã lưu lịch sử thông báo vào cơ sở dữ liệu.");
        }
    }
}
