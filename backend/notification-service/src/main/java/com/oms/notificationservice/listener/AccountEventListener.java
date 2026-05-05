package com.oms.notificationservice.listener;

import com.oms.notificationservice.config.RabbitMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class AccountEventListener {

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NOTIFICATION_ACCOUNT_CREATE)
    public void handleAccountCreated(Map<String, Object> event) {
        log.info("Notification Service received Account Created event: {}", event);
        // Logic gửi email hoặc notification ở đây
    }
}
