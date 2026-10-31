package com.example.ecommerce.service;

import com.example.ecommerce.models.dto.request.CategoryRequest;
import com.example.ecommerce.models.dto.response.CategoryResponse;
import com.example.ecommerce.models.dto.response.SubcategoryResponse;

import java.util.List;

public interface CategoryService {
    List<CategoryResponse> getActiveCategories();

    List<SubcategoryResponse> getActiveSubcategories(Long categoryId);

    CategoryResponse createCategory(CategoryRequest request);

    SubcategoryResponse createSubcategory(Long categoryId, CategoryRequest request);
}
