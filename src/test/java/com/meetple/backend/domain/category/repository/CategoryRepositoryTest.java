package com.meetple.backend.domain.category.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.meetple.backend.domain.category.entity.Category;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class CategoryRepositoryTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    void findAllByOrderByNameAscReturnsCategoriesOrderedByName() {
        categoryRepository.save(Category.create("study"));
        categoryRepository.save(Category.create("exercise"));

        assertThat(categoryRepository.findAllByOrderByNameAsc())
                .extracting(Category::getName)
                .containsExactly("exercise", "study");
    }

    @Test
    void savesDefaultImageUrl() {
        categoryRepository.save(Category.create(
                "exercise",
                "https://cdn.meetple.com/categories/exercise.png"
        ));

        assertThat(categoryRepository.findAllByOrderByNameAsc())
                .extracting(Category::getDefaultImageUrl)
                .containsExactly("https://cdn.meetple.com/categories/exercise.png");
    }
}
