package com.oms.productservice.controller;

import com.oms.productservice.dto.productDTO.ProductRequest;
import com.oms.productservice.dto.productDTO.ProductResponse;
import com.oms.productservice.service.ProductService;
import com.oms.common.service.UploadService;
import lombok.RequiredArgsConstructor;
import com.oms.common.ApiResponse;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;
    private final UploadService uploadService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Valid @ModelAttribute ProductRequest request,
            @RequestParam(value = "files", required = false) List<MultipartFile> files){
        try {
            List<String> imageUrls = new ArrayList<>();
            if (files != null && !files.isEmpty()) {
                for (MultipartFile file : files) {
                    if (file != null && !file.isEmpty()) {
                        imageUrls.add(uploadService.uploadFile(file, "products"));
                    }
                }
            }
            if (!imageUrls.isEmpty()) {
                request.setImageUrl(imageUrls);
            }
            ProductResponse response = productService.createProduct(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<ProductResponse>builder()
                    .success(true)
                    .status(HttpStatus.CREATED.value())
                    .message("Thành công")
                    .result(response)
                    .build()
            );
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to upload product images", e);
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> getAllProduct(
            @org.springframework.data.web.PageableDefault(size = 10, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) 
            org.springframework.data.domain.Pageable pageable) {
        
        Page<ProductResponse> result = productService.getAllProducts(null, null, null, null, pageable);
        
        return ResponseEntity.ok(ApiResponse.<Page<ProductResponse>>builder()
                .success(true)
                .status(HttpStatus.OK.value())
                .message("Thành công")
                .result(result)
                .build());
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> searchProduct(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) java.math.BigDecimal minPrice,
            @RequestParam(required = false) java.math.BigDecimal maxPrice,
            @org.springframework.data.web.PageableDefault(size = 10, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) 
            org.springframework.data.domain.Pageable pageable) {
        
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

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable String id,
            @Valid @ModelAttribute ProductRequest request,
            @RequestParam(value = "files", required = false) List<MultipartFile> files){
        try {
            List<String> imageUrls = new ArrayList<>();
            if (files != null && !files.isEmpty()) {
                for (MultipartFile file : files) {
                    if (file != null && !file.isEmpty()) {
                        imageUrls.add(uploadService.uploadFile(file, "products"));
                    }
                }
            }
            if (!imageUrls.isEmpty()) {
                request.setImageUrl(imageUrls);
            }
            return ResponseEntity.ok(
                ApiResponse.<ProductResponse>builder()
                    .success(true)
                    .status(HttpStatus.OK.value())
                    .message("Thành công")
                    .result(productService.updateProduct(id, request))
                    .build()
            );
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to upload product images", e);
        }
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
