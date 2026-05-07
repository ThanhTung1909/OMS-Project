package com.oms.identityservice.scheduler;

import com.oms.common.constant.RabbitMQConstants;
import com.oms.identityservice.entity.Enum.OutboxStatus;
import com.oms.identityservice.entity.OutboxEvent;
import com.oms.identityservice.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message; 
import org.springframework.amqp.core.MessageProperties; 
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    
    // Đảm bảo không quét chồng chéo khi có nhiều pod/instance
    private boolean isPublishing = false;

    @Scheduled(fixedRate = 5000)
    @Transactional
    public void publishOutboxEvents() {
        if (isPublishing) {
            return;
        }
        
        isPublishing = true;
        try {
            List<OutboxEvent> pendingEvents = outboxEventRepository.findByStatus(OutboxStatus.PENDING);
            
            for (OutboxEvent event : pendingEvents) {
                try {
                   
                    
                    // 1. Tạo thuộc tính báo cho RabbitMQ biết đây là định dạng JSON
                    MessageProperties properties = new MessageProperties();
                    properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                    
                    // 2. Gói chuỗi JSON String vào một Object Message chuẩn
                    Message message = new Message(event.getPayload().getBytes(), properties);
                    
                    // 3. Sử dụng hàm send()
                    rabbitTemplate.send(RabbitMQConstants.EXCHANGE_NAME, event.getType(), message);
                    
                    // Cập nhật trạng thái thành công
                    event.setStatus(OutboxStatus.PROCESSED);
                    outboxEventRepository.save(event);
                    
                    log.info("Successfully published outbox event: {}", event.getId());
                } catch (Exception e) {
                    log.error("Failed to publish outbox event: {}", event.getId(), e);
                    event.setRetryCount(event.getRetryCount() + 1);
                    if (event.getRetryCount() >= 5) {
                        event.setStatus(OutboxStatus.FAILED);
                    }
                    outboxEventRepository.save(event);
                }
            }
        } finally {
            isPublishing = false;
        }
    }
}