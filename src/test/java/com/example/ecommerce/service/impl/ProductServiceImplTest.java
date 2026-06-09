package com.example.ecommerce.service.impl;

import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.models.dto.response.ProductResponse;
import com.example.ecommerce.models.entity.Category;
import com.example.ecommerce.models.entity.Product;
import com.example.ecommerce.models.entity.Subcategory;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.repository.SubcategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private SubcategoryRepository subcategoryRepository;

    @InjectMocks
    private ProductServiceImpl productService;

    private Product activeProduct() {
        Category category = Category.builder().name("Electronics").build();
        Subcategory subcategory = Subcategory.builder().name("Laptops").category(category).build();
        return Product.builder()
                .id(3L)
                .name("Laptop")
                .slug("laptop")
                .sku("SKU-1")
                .price(BigDecimal.TEN)
                .stockQuantity(5)
                .isActive(true)
                .subcategory(subcategory)
                .build();
    }

    @Test
    void getProductByIdReturnsActiveProduct() {
        when(productRepository.findByIdAndIsActiveTrue(3L)).thenReturn(Optional.of(activeProduct()));

        ProductResponse response = productService.getProductById(3L);

        assertThat(response.getId()).isEqualTo(3L);
        verify(productRepository).findByIdAndIsActiveTrue(3L);
        verify(productRepository, never()).findById(3L);
    }

    @Test
    void getProductByIdHidesInactiveProduct() {
        when(productRepository.findByIdAndIsActiveTrue(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductById(3L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product not found");
    }

    @Test
    void getProductBySlugReturnsActiveProduct() {
        when(productRepository.findBySlugAndIsActiveTrue("laptop")).thenReturn(Optional.of(activeProduct()));

        ProductResponse response = productService.getProductBySlug("laptop");

        assertThat(response.getSlug()).isEqualTo("laptop");
        verify(productRepository).findBySlugAndIsActiveTrue("laptop");
        verify(productRepository, never()).findBySlug("laptop");
    }

    @Test
    void getProductBySlugHidesInactiveProduct() {
        when(productRepository.findBySlugAndIsActiveTrue("laptop")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductBySlug("laptop"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product not found");
    }
}
