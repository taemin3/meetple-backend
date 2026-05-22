package com.meetple.backend.domain.category.service;

import com.meetple.backend.domain.category.dto.response.CategoryResponse;
import com.meetple.backend.domain.category.repository.CategoryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<CategoryResponse> getCategories() {
        return categoryRepository.findAllByOrderByNameAsc()
                .stream()
                .map(CategoryResponse::from)
                .toList();
    }
}
