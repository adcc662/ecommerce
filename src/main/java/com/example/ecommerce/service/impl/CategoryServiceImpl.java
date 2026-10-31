package com.example.ecommerce.service.impl;

import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.models.dto.request.CategoryRequest;
import com.example.ecommerce.models.dto.response.CategoryResponse;
import com.example.ecommerce.models.dto.response.SubcategoryResponse;
import com.example.ecommerce.models.entity.Category;
import com.example.ecommerce.models.entity.Subcategory;
import com.example.ecommerce.repository.CategoryRepository;
import com.example.ecommerce.repository.SubcategoryRepository;
import com.example.ecommerce.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final SubcategoryRepository subcategoryRepository;

    @Override
    public List<CategoryResponse> getActiveCategories() {
        return categoryRepository.findByIsActiveTrue().stream()
                .map(category -> mapToResponse(category, category.getSubcategories().stream()
                        .filter(sub -> Boolean.TRUE.equals(sub.getIsActive()))
                        .toList()))
                .toList();
    }

    @Override
    public List<SubcategoryResponse> getActiveSubcategories(Long categoryId) {
        Category category = findActiveCategory(categoryId);
        return subcategoryRepository.findByCategoryId(category.getId()).stream()
                .filter(sub -> Boolean.TRUE.equals(sub.getIsActive()))
                .map(CategoryServiceImpl::mapToResponse)
                .toList();
    }

    @Override
    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        Category category = Category.builder()
                .name(request.getName().trim())
                .slug(request.getSlug())
                .description(request.getDescription())
                .imageUrl(request.getImageUrl())
                .build();
        return mapToResponse(categoryRepository.save(category), List.of());
    }

    @Override
    @Transactional
    public SubcategoryResponse createSubcategory(Long categoryId, CategoryRequest request) {
        Category category = findActiveCategory(categoryId);
        Subcategory subcategory = Subcategory.builder()
                .category(category)
                .name(request.getName().trim())
                .slug(request.getSlug())
                .description(request.getDescription())
                .imageUrl(request.getImageUrl())
                .build();
        return mapToResponse(subcategoryRepository.save(subcategory));
    }

    private Category findActiveCategory(Long id) {
        return categoryRepository.findById(id)
                .filter(category -> Boolean.TRUE.equals(category.getIsActive()))
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
    }

    private static CategoryResponse mapToResponse(Category category, List<Subcategory> subcategories) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .description(category.getDescription())
                .imageUrl(category.getImageUrl())
                .subcategories(subcategories.stream().map(CategoryServiceImpl::mapToResponse).toList())
                .build();
    }

    private static SubcategoryResponse mapToResponse(Subcategory subcategory) {
        return SubcategoryResponse.builder()
                .id(subcategory.getId())
                .categoryId(subcategory.getCategory().getId())
                .name(subcategory.getName())
                .slug(subcategory.getSlug())
                .description(subcategory.getDescription())
                .imageUrl(subcategory.getImageUrl())
                .build();
    }
}
