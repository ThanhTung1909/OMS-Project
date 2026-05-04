package com.oms.productservice.controller;

import com.oms.productservice.dto.productDTO.ProductRequest;
import com.oms.productservice.dto.productDTO.ProductResponse;
import com.oms.productservice.service.ProductService;
import lombok.RequiredArgsConstructor;
import com.oms.common.ApiResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;

    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(@Valid @RequestBody ProductRequest request){
        ProductResponse response = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponse.<ProductResponse>builder()
                .success(true)
                .status(HttpStatus.CREATED.value())
                .message("Thành công")
                .result(response)
                .build()
        );
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> getAllProduct(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) java.math.BigDecimal minPrice,
            @RequestParam(required = false) java.math.BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        
        // Xử lý sort từ string (ví dụ: "price,asc")
        String[] sortParams = sort.split(",");
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortParams[1].equalsIgnoreCase("desc") ? 
                Sort.Direction.DESC : Sort.Direction.ASC, sortParams[0]));
        
        Page<ProductResponse> result = productService.getAllProducts(name, categoryId, minPrice, maxPrice, pageable);
        
        return ResponseEntity.ok(ApiResponse.<Page<ProductResponse>>builder()
                .success(true)
                .status(HttpStatus.OK.value())
                .message("Thành công")
                .result(result)
                .build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(@PathVariable String id){
        return ResponseEntity.ok(
            ApiResponse.<ProductResponse>builder()
                .success(true)
                .status(HttpStatus.OK.value())
                .message("Thành công")
                .result(productService.getProductById(id))
                .build()
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(@PathVariable String id, @Valid @RequestBody ProductRequest request){
        return ResponseEntity.ok(
            ApiResponse.<ProductResponse>builder()
                .success(true)
                .status(HttpStatus.OK.value())
                .message("Thành công")
                .result(productService.updateProduct(id, request))
                .build()
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deleteProduct(@PathVariable String id){
        productService.deleteProduct(id);
        return ResponseEntity.ok(
            ApiResponse.<String>builder()
                .success(true)
                .status(HttpStatus.OK.value())
                .message("Thành công")
                .result("Product deleted successfully")
                .build()
        );
    }

}
