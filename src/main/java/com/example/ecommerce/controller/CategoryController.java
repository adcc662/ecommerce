package com.example.ecommerce.controller;

import com.example.ecommerce.models.dto.request.CategoryRequest;
import com.example.ecommerce.models.dto.response.CategoryResponse;
import com.example.ecommerce.models.dto.response.SubcategoryResponse;
import com.example.ecommerce.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> getCategories() {
        return ResponseEntity.ok(categoryService.getActiveCategories());
    }

    @GetMapping("/{id}/subcategories")
    public ResponseEntity<List<SubcategoryResponse>> getSubcategories(@PathVariable Long id) {
        return ResponseEntity.ok(categoryService.getActiveSubcategories(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.createCategory(request));
    }

    @PostMapping("/{id}/subcategories")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SubcategoryResponse> createSubcategory(
            @PathVariable Long id,
            @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.createSubcategory(id, request));
    }
}
