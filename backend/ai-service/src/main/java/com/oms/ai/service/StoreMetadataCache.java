package com.oms.ai.service;

import org.springframework.stereotype.Component;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class StoreMetadataCache {
    private final AtomicInteger totalProducts = new AtomicInteger(0);
    private final Set<String> categories = ConcurrentHashMap.newKeySet();

    public void update(int total, Set<String> newCategories) {
        totalProducts.set(total);
        categories.clear();
        if (newCategories != null) {
            categories.addAll(newCategories);
        }
    }

    public int getTotalProducts() {
        return totalProducts.get();
    }

    public Set<String> getCategories() {
        return categories;
    }
}
