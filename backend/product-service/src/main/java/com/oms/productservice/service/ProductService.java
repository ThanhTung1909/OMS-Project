package com.oms.productservice.service;

import com.oms.productservice.dto.productDTO.ProductRequest;
import com.oms.productservice.dto.productDTO.ProductResponse;
import com.oms.productservice.entity.Category;
import com.oms.productservice.entity.Product;
import com.oms.productservice.repository.CategoryRepository;
import com.oms.productservice.repository.ProductRepository;
import com.oms.common.AppException;
import com.oms.productservice.exception.ProductErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {
    private final ProductRepository productrepo;
    private final CategoryRepository categoryRepo;

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
            return mapToProductResponse(productrepo.save(product));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new AppException(ProductErrorCode.PRODUCT_SKU_ALREADY_EXISTS);
        }
    }

    // Pagination & Search
    public Page<ProductResponse> getAllProducts(String name, String categoryId, java.math.BigDecimal minPrice, java.math.BigDecimal maxPrice, Pageable pageable) {
        return productrepo.searchProducts(name, categoryId, minPrice, maxPrice, pageable)
                .map(this::mapToProductResponse);
    }

    @Cacheable(value = "products", key = "#id")
    public ProductResponse getProductById(String id){
        log.info("Fetching product from DB for ID: {}", id);
        Product product = productrepo.findById(id)
                .orElseThrow(() -> new AppException(ProductErrorCode.PRODUCT_NOT_FOUND));
        return mapToProductResponse(product);
    }

    @Transactional
    @CacheEvict(value = "products", key = "#id")
    public ProductResponse updateProduct(String id, ProductRequest request){
        Product product = productrepo.findById(id)
                .orElseThrow(() -> new AppException(ProductErrorCode.PRODUCT_NOT_FOUND));
        
        // Kiểm tra SKU duy nhất (nếu thay đổi SKU)
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

        return mapToProductResponse(productrepo.save(product));
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
                .imageUrl(product.getImageUrl())
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
