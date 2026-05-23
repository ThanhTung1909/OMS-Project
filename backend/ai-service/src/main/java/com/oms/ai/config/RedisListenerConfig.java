package com.oms.ai.config;

import com.oms.ai.listener.RedisKeyspaceNotificationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisListenerConfig {

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisKeyspaceNotificationListener listener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        // Đăng ký lắng nghe sự kiện keyevent "set" cho tất cả các key thuộc DB 0
        container.addMessageListener(listener, new PatternTopic("__keyevent@0__:set"));
        return container;
    }
}
