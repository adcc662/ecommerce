package com.example.ecommerce.service;

import com.example.ecommerce.models.dto.request.ProductRequest;
import com.example.ecommerce.models.dto.response.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

public interface ProductService {
    ProductResponse createProduct(ProductRequest request);
    ProductResponse updateProduct(Long id, ProductRequest request);
    ProductResponse patchProduct(Long id, Map<String, Object> updates);
    ProductResponse getProductById(Long id);
    ProductResponse getProductBySlug(String slug);
    List<ProductResponse> getAllProducts();
    Page<ProductResponse> getAllProducts(Pageable pageable);
    List<ProductResponse> getActiveProducts();
    Page<ProductResponse> getActiveProducts(Pageable pageable);
    List<ProductResponse> searchProductsByName(String keyword);
    Page<ProductResponse> searchProductsByName(String keyword, Pageable pageable);
    void deleteProduct(Long id);
}
