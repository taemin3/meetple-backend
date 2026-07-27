package com.meetple.backend.domain.category.controller;

import com.meetple.backend.domain.category.dto.response.CategoryResponse;
import com.meetple.backend.domain.category.service.CategoryService;
import com.meetple.backend.global.response.ApiResponse;
import com.meetple.backend.global.response.SuccessStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Category", description = "카테고리 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "전체 카테고리 조회", description = "전체 카테고리 목록을 이름 오름차순으로 조회합니다.")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getCategories() {
        return ApiResponse.success(SuccessStatus.OK, categoryService.getCategories());
    }
}
