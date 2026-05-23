package com.oms.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageResponse {
    private String reply;
    private List<ProductSuggestion> suggestions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductSuggestion {
        private String id;
        private String name;
        private Double price;
        private String description;
        private Integer stockQuantity;
    }
}
