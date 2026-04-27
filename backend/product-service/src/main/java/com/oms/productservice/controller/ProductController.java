package com.oms.productservice.controller;

import com.oms.productservice.dto.productDTO.ProductRequest;
import com.oms.productservice.dto.productDTO.ProductResponse;
import com.oms.productservice.service.ProductService;
import lombok.RequiredArgsConstructor;
import com.oms.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;

    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(@RequestBody ProductRequest request){
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
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getAllProduct(){
        return ResponseEntity.ok(
            ApiResponse.<List<ProductResponse>>builder()
                .success(true)
                .status(HttpStatus.OK.value())
                .message("Thành công")
                .result(productService.getAllProducts())
                .build()
        );
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
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(@PathVariable String id, @RequestBody ProductRequest request){
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
