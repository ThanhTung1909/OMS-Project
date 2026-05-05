package com.oms.notificationservice.config;

import com.oms.common.constant.RabbitMQConstants;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình RabbitMQ cho dịch vụ Thông báo
 */
@Configuration
public class RabbitMQConfig {

    // Tên các hàng đợi (Queues)
    public static final String NOTIFICATION_AUTH_QUEUE = "q.notification.auth";
    public static final String NOTIFICATION_ORDER_QUEUE = "q.notification.order";

    // Hàng đợi nhận các sự kiện liên quan đến xác thực/tạo tài khoản
    @Bean
    public Queue authQueue() {
        return new Queue(NOTIFICATION_AUTH_QUEUE, true);
    }

    // Hàng đợi nhận các sự kiện liên quan đến đơn hàng và vận chuyển
    @Bean
    public Queue orderQueue() {
        return new Queue(NOTIFICATION_ORDER_QUEUE, true);
    }

    // Sử dụng chung Topic Exchange 'oms.exchange'
    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(RabbitMQConstants.EXCHANGE_NAME, true, false);
    }

    // Liên kết: Hàng đợi Auth lắng nghe sự kiện 'identity.account.created'
    @Bean
    public Binding authBinding(Queue authQueue, TopicExchange exchange) {
        return BindingBuilder.bind(authQueue)
                .to(exchange)
                .with(RabbitMQConstants.IDENTITY_ACCOUNT_CREATED);
    }

    // Liên kết: Hàng đợi Order lắng nghe sự kiện cập nhật trạng thái đơn hàng
    @Bean
    public Binding orderStatusBinding(Queue orderQueue, TopicExchange exchange) {
        return BindingBuilder.bind(orderQueue)
                .to(exchange)
                .with(RabbitMQConstants.NOTIFICATION_ORDER_STATUS);
    }

    // Liên kết: Hàng đợi Order cũng lắng nghe cả sự kiện cập nhật từ phía vận chuyển
    @Bean
    public Binding deliveryStatusBinding(Queue orderQueue, TopicExchange exchange) {
        return BindingBuilder.bind(orderQueue)
                .to(exchange)
                .with(RabbitMQConstants.DELIVERY_STATUS_UPDATE);
    }

    // Chuyển đổi dữ liệu sang định dạng JSON khi truyền nhận qua RabbitMQ
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
