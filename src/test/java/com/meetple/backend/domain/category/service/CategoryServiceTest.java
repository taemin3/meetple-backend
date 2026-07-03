package com.meetple.backend.domain.category.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.meetple.backend.domain.category.dto.response.CategoryResponse;
import com.meetple.backend.domain.category.entity.Category;
import com.meetple.backend.domain.category.repository.CategoryRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void getCategoriesReturnsCategoryResponses() {
        given(categoryRepository.findAllByOrderByNameAsc())
                .willReturn(List.of(
                        Category.create("exercise", "https://cdn.meetple.com/categories/exercise.png"),
                        Category.create("study", "https://cdn.meetple.com/categories/study.png")
                ));

        List<CategoryResponse> responses = categoryService.getCategories();

        assertThat(responses).extracting(CategoryResponse::name)
                .containsExactly("exercise", "study");
        assertThat(responses).extracting(CategoryResponse::defaultImageUrl)
                .containsExactly(
                        "https://cdn.meetple.com/categories/exercise.png",
                        "https://cdn.meetple.com/categories/study.png"
                );
    }
}
