package com.meetple.backend.domain.category.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meetple.backend.domain.category.dto.response.CategoryResponse;
import com.meetple.backend.domain.category.service.CategoryService;
import com.meetple.backend.global.response.SuccessStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class CategoryControllerTest {

    @Mock
    private CategoryService categoryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CategoryController(categoryService)).build();
    }

    @Test
    void getCategoriesReturnsApiResponse() throws Exception {
        List<CategoryResponse> categories = List.of(
                new CategoryResponse(1L, "exercise", "https://cdn.meetple.com/categories/exercise.png"),
                new CategoryResponse(2L, "study", "https://cdn.meetple.com/categories/study.png")
        );
        given(categoryService.getCategories()).willReturn(categories);

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(SuccessStatus.OK.getCode()))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name").value("exercise"))
                .andExpect(jsonPath("$.data[0].defaultImageUrl")
                        .value("https://cdn.meetple.com/categories/exercise.png"))
                .andExpect(jsonPath("$.data[1].id").value(2))
                .andExpect(jsonPath("$.data[1].name").value("study"))
                .andExpect(jsonPath("$.data[1].defaultImageUrl")
                        .value("https://cdn.meetple.com/categories/study.png"));
    }
}
