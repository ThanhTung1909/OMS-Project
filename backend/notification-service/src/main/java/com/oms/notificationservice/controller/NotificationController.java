package com.oms.notificationservice.controller;

import com.oms.common.ApiResponse;
import com.oms.notificationservice.entity.NotificationLog;
import com.oms.notificationservice.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationLogRepository notificationLogRepository;

    @GetMapping("/me")
    public ApiResponse<Page<NotificationLog>> getMyNotifications(
            @RequestHeader("X-Account-Id") String userId,
            Pageable pageable) {
        
        Page<NotificationLog> result = notificationLogRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        
        return ApiResponse.<Page<NotificationLog>>builder()
                .success(true)
                .status(HttpStatus.OK.value())
                .message("Thành công")
                .result(result)
                .build();
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<Void> markAsRead(@PathVariable String id) {
        NotificationLog notification = notificationLogRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        
        notification.setRead(true);
        notificationLogRepository.save(notification);
        
        return ApiResponse.<Void>builder()
                .success(true)
                .status(HttpStatus.OK.value())
                .message("Đã đánh dấu đã đọc")
                .build();
    }
}
