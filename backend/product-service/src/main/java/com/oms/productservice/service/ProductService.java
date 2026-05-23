package com.oms.productservice.service;

import com.oms.productservice.dto.productDTO.ProductRequest;
import com.oms.productservice.dto.productDTO.ProductResponse;
import com.oms.productservice.entity.Category;
import com.oms.productservice.entity.Product;
import com.oms.productservice.repository.CategoryRepository;
import com.oms.productservice.repository.ProductRepository;
import com.oms.common.AppException;
import com.oms.common.ApiResponse;
import com.oms.productservice.exception.ProductErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.oms.common.constant.RedisConstants;
import com.oms.productservice.client.InventoryClient;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {
    private final ProductRepository productrepo;
    private final CategoryRepository categoryRepo;
    private final StringRedisTemplate stringRedisTemplate;

    @Autowired
    @Lazy
    private ProductService self;

    @Autowired
    @Lazy
    private InventoryClient inventoryClient;

    @Transactional
    public ProductResponse createProduct(ProductRequest request){
        if(productrepo.existsBySku(request.getSku())) {
            throw new AppException(ProductErrorCode.PRODUCT_SKU_ALREADY_EXISTS);
        }

        Category category = categoryRepo.findById(request.getCategoryId())
                .orElseThrow(() -> new AppException(ProductErrorCode.CATEGORY_NOT_FOUND));

        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .sku(request.getSku())
                .imageUrl(request.getImageUrl())
                .category(category)
                .build();

        try {
            Product savedProduct = productrepo.save(product);
            
            // Đồng bộ Giá lên Redis (CQRS)
            try {
                String priceKey = RedisConstants.PREFIX_PRODUCT_PRICE + savedProduct.getId();
                stringRedisTemplate.opsForValue().set(priceKey, savedProduct.getPrice().toString());
                
                String nameKey = RedisConstants.PREFIX_PRODUCT_NAME + savedProduct.getId();
                stringRedisTemplate.opsForValue().set(nameKey, savedProduct.getName());

                if (savedProduct.getImageUrl() != null && !savedProduct.getImageUrl().isEmpty()) {
                    String imageKey = RedisConstants.PREFIX_PRODUCT_IMAGE + savedProduct.getId();
                    stringRedisTemplate.opsForValue().set(imageKey, savedProduct.getImageUrl().get(0));
                }
            } catch (Exception e) {
                log.error("Lỗi khi đồng bộ dữ liệu lên Redis cho sản phẩm {}: {}", savedProduct.getId(), e.getMessage());
            }

            return mapToProductResponse(savedProduct);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new AppException(ProductErrorCode.PRODUCT_SKU_ALREADY_EXISTS);
        }
    }

    // Pagination & Search
    public Page<ProductResponse> getAllProducts(String name, String categoryId, java.math.BigDecimal minPrice, java.math.BigDecimal maxPrice, Pageable pageable) {
        Page<ProductResponse> page = productrepo.searchProducts(name, categoryId, minPrice, maxPrice, pageable)
                .map(this::mapToProductResponse);
        // Enrich tồn kho theo batch
        enrichStockQuantity(page.getContent());
        return page;
    }

    public ProductResponse getProductById(String id){
        // 1. Lấy dữ liệu tĩnh (Sẽ lôi từ Cache ra nếu có)
        ProductResponse response = self.getCachedProductBase(id);
        // 2. Đắp dữ liệu động
        enrichStockQuantity(Collections.singletonList(response));
        
        return response;
    }

    @Cacheable(value = "products", key = "#id")
    public ProductResponse getCachedProductBase(String id) {
        log.info("Đang lấy thông tin sản phẩm từ DB cho ID: {}", id);
        Product product = productrepo.findById(id)
                .orElseThrow(() -> new AppException(ProductErrorCode.PRODUCT_NOT_FOUND));
        return mapToProductResponse(product);
    }

    @Transactional
    @CacheEvict(value = "products", key = "#id")
    public ProductResponse updateProduct(String id, ProductRequest request){
        Product product = productrepo.findById(id)
                .orElseThrow(() -> new AppException(ProductErrorCode.PRODUCT_NOT_FOUND));
        
        // Kiểm tra SKU duy nhất
        if(!product.getSku().equals(request.getSku()) && productrepo.existsBySku(request.getSku())) {
            throw new AppException(ProductErrorCode.PRODUCT_SKU_ALREADY_EXISTS);
        }

        Category category = categoryRepo.findById(request.getCategoryId())
                .orElseThrow(() -> new AppException(ProductErrorCode.CATEGORY_NOT_FOUND));

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setSku(request.getSku());
        product.setImageUrl(request.getImageUrl());
        product.setCategory(category);

        Product savedProduct = productrepo.save(product);

        // Đồng bộ Giá lên Redis (CQRS)
        try {
            String priceKey = RedisConstants.PREFIX_PRODUCT_PRICE + savedProduct.getId();
            stringRedisTemplate.opsForValue().set(priceKey, savedProduct.getPrice().toString());
            
            String nameKey = RedisConstants.PREFIX_PRODUCT_NAME + savedProduct.getId();
            stringRedisTemplate.opsForValue().set(nameKey, savedProduct.getName());

            if (savedProduct.getImageUrl() != null && !savedProduct.getImageUrl().isEmpty()) {
                String imageKey = RedisConstants.PREFIX_PRODUCT_IMAGE + savedProduct.getId();
                stringRedisTemplate.opsForValue().set(imageKey, savedProduct.getImageUrl().get(0));
            }
        } catch (Exception e) {
            log.error("Lỗi khi đồng bộ dữ liệu lên Redis cho sản phẩm {}: {}", savedProduct.getId(), e.getMessage());
        }

        return mapToProductResponse(savedProduct);
    }

    @Transactional
    @CacheEvict(value = "products", key = "#id")
    public void deleteProduct(String id){
        if(!productrepo.existsById(id)) throw new AppException(ProductErrorCode.PRODUCT_NOT_FOUND);
        productrepo.deleteById(id);
    }

    private ProductResponse mapToProductResponse(Product product){
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .sku(product.getSku())
                .imageUrl(product.getImageUrl() != null ? new java.util.ArrayList<>(product.getImageUrl()) : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    
    private void enrichStockQuantity(List<ProductResponse> products) {
        if (products == null || products.isEmpty()) return;

        try {
            // 1. Tạo list các Key cần tìm
            List<String> redisKeys = products.stream()
                    .map(p -> RedisConstants.PREFIX_INVENTORY_STOCK + p.getId())
                    .collect(Collectors.toList());

            // 2. Chọc 1 phát vào Redis lấy ra cả 1 mảng số lượng (High Performance)
            List<String> stocks = stringRedisTemplate.opsForValue().multiGet(redisKeys);

            // 3. Map ngược lại vào ProductResponse, thu gom danh sách sản phẩm bị lỡ cache
            List<ProductResponse> missingCacheProducts = new java.util.ArrayList<>();
            if (stocks != null) {
                for (int i = 0; i < products.size(); i++) {
                    String stockStr = stocks.get(i);
                    if (stockStr != null) {
                        products.get(i).setStockQuantity(Integer.parseInt(stockStr));
                    } else {
                        missingCacheProducts.add(products.get(i));
                    }
                }
            } else {
                missingCacheProducts.addAll(products);
            }

            // 4. Nếu có sản phẩm lỡ cache, gọi FeignClient sang inventory-service để lấy tồn kho thực tế & nạp lại vào Redis
            if (!missingCacheProducts.isEmpty()) {
                log.info("Phát hiện {} sản phẩm lỡ cache tồn kho. Gọi FeignClient sang inventory-service...", missingCacheProducts.size());
                List<String> missingIds = missingCacheProducts.stream().map(ProductResponse::getId).collect(Collectors.toList());
                ApiResponse<Map<String, Integer>> response = inventoryClient.getBulkStock(missingIds);
                if (response != null && response.isSuccess() && response.getResult() != null) {
                    Map<String, Integer> stockMap = response.getResult();
                    for (ProductResponse p : missingCacheProducts) {
                        int stockQty = stockMap.getOrDefault(p.getId(), 0);
                        p.setStockQuantity(stockQty);
                        
                        // Nạp lại vào Redis để ấm cache lần sau
                        try {
                            String redisKey = RedisConstants.PREFIX_INVENTORY_STOCK + p.getId();
                            stringRedisTemplate.opsForValue().set(redisKey, String.valueOf(stockQty));
                        } catch (Exception redisEx) {
                            log.warn("Không thể ghi đè cache tồn kho cho sản phẩm {}: {}", p.getId(), redisEx.getMessage());
                        }
                    }
                } else {
                    log.warn("Gọi bulk-stock thất bại hoặc trống. Mặc định tồn kho bằng 0 cho các sản phẩm lỡ cache.");
                    missingCacheProducts.forEach(p -> p.setStockQuantity(0));
                }
            }
            
            log.info("Đã enrich tồn kho cho {} sản phẩm.", products.size());
        } catch (Exception e) {
            log.error("Lỗi khi lấy tồn kho: {}. Fallback về 0.", e.getMessage());
            products.forEach(p -> p.setStockQuantity(0));
        }
    }
}
