package com.meetple.backend.domain.category.dto.response;

import com.meetple.backend.domain.category.entity.Category;

public record CategoryResponse(
        Long id,
        String name
) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName());
    }
}
