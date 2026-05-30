package com.oms.productservice.controller;

import com.oms.common.ApiResponse;
import com.oms.productservice.dto.categoryDTO.CategoryResponse;
import com.oms.productservice.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {
    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getAllCategories() {
        List<CategoryResponse> categories = categoryService.getAllCategories();
        return ResponseEntity.ok(ApiResponse.<List<CategoryResponse>>builder()
                .success(true)
                .status(200)
                .message("Thành công")
                .result(categories)
                .build());
    }
}
