package com.example.ecommerce.service.impl;

import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.models.dto.request.CategoryRequest;
import com.example.ecommerce.models.entity.Category;
import com.example.ecommerce.models.entity.Subcategory;
import com.example.ecommerce.repository.CategoryRepository;
import com.example.ecommerce.repository.SubcategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private SubcategoryRepository subcategoryRepository;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    private static final CategoryRequest REQUEST = CategoryRequest.builder().name(" Laptops ").slug("laptops").build();

    @Test
    void listsOnlyActiveSubcategories() {
        Category category = Category.builder().id(1L).name("Electronics").slug("electronics")
                .subcategories(new ArrayList<>()).build();
        category.getSubcategories().add(Subcategory.builder().id(2L).category(category).name("On").isActive(true).build());
        category.getSubcategories().add(Subcategory.builder().id(3L).category(category).name("Off").isActive(false).build());
        when(categoryRepository.findByIsActiveTrue()).thenReturn(List.of(category));

        var categories = categoryService.getActiveCategories();

        assertThat(categories).hasSize(1);
        assertThat(categories.get(0).getSubcategories()).extracting("name").containsExactly("On");
    }

    @Test
    void createsSubcategoryUnderActiveCategory() {
        Category category = Category.builder().id(1L).isActive(true).build();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(subcategoryRepository.save(any(Subcategory.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = categoryService.createSubcategory(1L, REQUEST);

        assertThat(response.getCategoryId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Laptops");
    }

    @Test
    void subcategoryUnderMissingOrInactiveCategoryReturnsNotFound() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(Category.builder().id(1L).isActive(false).build()));

        assertThatThrownBy(() -> categoryService.createSubcategory(1L, REQUEST))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Category not found");
        verify(subcategoryRepository, never()).save(any());
    }
}
