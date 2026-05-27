package com.oms.ai.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.redis.RedisEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;

@Configuration
public class AiConfig {

    @Value("${langchain4j.google.ai.gemini.api-key}")
    private String geminiApiKey;

    @Value("${langchain4j.google.ai.gemini.model-name:gemini-2.5-flash}")
    private String geminiModelName;

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public ChatLanguageModel geminiChatModel() {
        return GoogleAiGeminiChatModel.builder()
                .apiKey(geminiApiKey)
                .modelName(geminiModelName)
                .temperature(0.2)
                .build();
    }

    @Bean
    @Primary
    public EmbeddingModel embeddingModel() {
        // Sử dụng mô hình nhúng đa ngôn ngữ của Google để hiểu ngữ nghĩa Tiếng Việt xuất sắc
        return GoogleAiEmbeddingModel.builder()
                .apiKey(geminiApiKey)
                .modelName("gemini-embedding-001")
                .outputDimensionality(768)
                .build();
    }

    @Bean
    public EmbeddingStore redisEmbeddingStore() {
        return RedisEmbeddingStore.builder()
                .host(redisHost)
                .port(redisPort)
                .indexName("oms_products")
                .dimension(768) // Kích thước Vector của Google text-embedding-004 là 768
                .metadataKeys(java.util.Arrays.asList("productId", "productName", "price", "description", "stockQuantity", "imageUrl"))
                .build();
    }
}
