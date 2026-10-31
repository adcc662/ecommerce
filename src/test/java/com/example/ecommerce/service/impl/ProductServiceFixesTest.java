package com.example.ecommerce.service.impl;

import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.models.entity.Category;
import com.example.ecommerce.models.entity.Product;
import com.example.ecommerce.models.entity.Subcategory;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.repository.SubcategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceFixesTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private SubcategoryRepository subcategoryRepository;

    @InjectMocks
    private ProductServiceImpl productService;

    private static Product product() {
        Category category = Category.builder().id(1L).name("Electronics").build();
        Subcategory subcategory = Subcategory.builder().id(2L).name("Laptops").category(category).build();
        return Product.builder()
                .id(3L).name("Laptop").slug("laptop")
                .price(new BigDecimal("100.00"))
                .stockQuantity(5).isActive(true).subcategory(subcategory)
                .build();
    }

    // S10: delete keeps order history

    @Test
    void deleteProductDeactivatesInsteadOfRemoving() {
        Product product = product();
        when(productRepository.findById(3L)).thenReturn(Optional.of(product));

        productService.deleteProduct(3L);

        assertThat(product.getIsActive()).isFalse();
        verify(productRepository).save(product);
        verify(productRepository, never()).deleteById(any());
        verify(productRepository, never()).delete(any());
    }

    @Test
    void deleteUnknownProductStillReturnsNotFound() {
        when(productRepository.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.deleteProduct(3L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product not found");
    }

    // S11: PATCH validates types and values

    static Stream<Map<String, Object>> invalidPatches() {
        return Stream.of(
                Map.of("stockQuantity", "5"),
                Map.of("stockQuantity", -1),
                Map.of("price", -3),
                Map.of("price", "abc"),
                Map.of("discountPrice", 150),
                Map.of("name", " "),
                Map.of("isActive", "yes"),
                Map.of("unknownField", 1));
    }

    @ParameterizedTest
    @MethodSource("invalidPatches")
    void patchRejectsInvalidInputWithoutSaving(Map<String, Object> updates) {
        when(productRepository.findById(3L)).thenReturn(Optional.of(product()));

        assertThatThrownBy(() -> productService.patchProduct(3L, updates))
                .isInstanceOf(IllegalArgumentException.class);
        verify(productRepository, never()).save(any());
    }

    @Test
    void patchStillAppliesValidChanges() {
        Product product = product();
        when(productRepository.findById(3L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);
        Map<String, Object> updates = new HashMap<>();
        updates.put("price", "120.50");
        updates.put("discountPrice", 99);
        updates.put("stockQuantity", 7);
        updates.put("isActive", false);

        productService.patchProduct(3L, updates);

        assertThat(product.getPrice()).isEqualByComparingTo("120.50");
        assertThat(product.getDiscountPrice()).isEqualByComparingTo("99");
        assertThat(product.getStockQuantity()).isEqualTo(7);
        assertThat(product.getIsActive()).isFalse();
    }

    @Test
    void patchCanClearDiscountPrice() {
        Product product = product();
        product.setDiscountPrice(new BigDecimal("80"));
        when(productRepository.findById(3L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);
        Map<String, Object> updates = new HashMap<>();
        updates.put("discountPrice", null);

        productService.patchProduct(3L, updates);

        assertThat(product.getDiscountPrice()).isNull();
    }

    // S8: bounded lists, no caching of pages or searches

    @Test
    void nonPaginatedActiveListIsBounded() {
        when(productRepository.findByIsActiveTrue(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(product())));

        assertThat(productService.getActiveProducts()).hasSize(1);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findByIsActiveTrue(pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(ProductServiceImpl.MAX_LIST_SIZE);
    }

    @Test
    void nonPaginatedSearchIsBounded() {
        when(productRepository.searchByName(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        productService.searchProductsByName("lap");

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).searchByName(org.mockito.ArgumentMatchers.eq("lap"), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(ProductServiceImpl.MAX_LIST_SIZE);
    }

    @Test
    void searchRejectsBlankOrOversizedKeyword() {
        assertThatThrownBy(() -> productService.searchProductsByName(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> productService.searchProductsByName("x".repeat(101)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pagesAndSearchesAreNotCached() throws NoSuchMethodException {
        assertThat(ProductServiceImpl.class.getMethod("getAllProducts", Pageable.class).isAnnotationPresent(Cacheable.class)).isFalse();
        assertThat(ProductServiceImpl.class.getMethod("getActiveProducts", Pageable.class).isAnnotationPresent(Cacheable.class)).isFalse();
        assertThat(ProductServiceImpl.class.getMethod("searchProductsByName", String.class, Pageable.class).isAnnotationPresent(Cacheable.class)).isFalse();
    }

    @Test
    void singleProductCacheKeysCannotCollide() throws NoSuchMethodException {
        String byId = ProductServiceImpl.class.getMethod("getProductById", Long.class).getAnnotation(Cacheable.class).key();
        String bySlug = ProductServiceImpl.class.getMethod("getProductBySlug", String.class).getAnnotation(Cacheable.class).key();

        assertThat(byId).startsWith("'id:'");
        assertThat(bySlug).startsWith("'slug:'");
    }
}
