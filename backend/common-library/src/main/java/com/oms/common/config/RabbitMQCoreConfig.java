package com.oms.common.config;

import com.oms.common.constant.RabbitMQConstants;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình cơ bản cho RabbitMQ dùng chung cho các service.
 */
@Configuration
public class RabbitMQCoreConfig {

    /**
     * Khai báo một Bean TopicExchange sử dụng hằng số EXCHANGE_NAME.
     * Thuộc tính durable = true đảm bảo exchange không bị mất khi RabbitMQ khởi động lại.
     * 
     * @return TopicExchange
     */
    @Bean
    public TopicExchange commonExchange() {
        return new TopicExchange(RabbitMQConstants.EXCHANGE_NAME, true, false);
    }
}
