package com.oms.productservice.service;

import com.oms.productservice.dto.productDTO.ProductRequest;
import com.oms.productservice.dto.productDTO.ProductResponse;
import com.oms.productservice.entity.Category;
import com.oms.productservice.entity.Product;
import com.oms.productservice.repository.CategoryRepository;
import com.oms.productservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository productrepo;
    private final CategoryRepository categoryRepo;

    // Create product
    public ProductResponse createProduct(ProductRequest request){
        Category category = categoryRepo.findById(request.getCategoryId())
                .orElseThrow(() -> new RuntimeException("Category not found"));

        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .sku(request.getSku())
                .imageUrl(request.getImageUrl())
                .category(category)
                .build();

        Product saveProduct = productrepo.save(product);
        return mapToProductResponse(saveProduct);
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
