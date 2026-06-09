package com.example.ecommerce.service.impl;

import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.models.dto.request.ProductRequest;
import com.example.ecommerce.models.dto.response.ProductResponse;
import com.example.ecommerce.models.entity.Product;
import com.example.ecommerce.models.entity.Subcategory;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.repository.SubcategoryRepository;
import com.example.ecommerce.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final SubcategoryRepository subcategoryRepository;

    @Override
    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse createProduct(ProductRequest request) {
        Subcategory subcategory = subcategoryRepository.findById(request.getSubcategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Subcategory not found"));

        Product product = Product.builder()
                .subcategory(subcategory)
                .name(request.getName())
                .description(request.getDescription())
                .slug(request.getSlug())
                .sku(request.getSku())
                .price(request.getPrice())
                .discountPrice(request.getDiscountPrice())
                .stockQuantity(request.getStockQuantity())
                .weight(request.getWeight())
                .dimensions(request.getDimensions())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();

        product = productRepository.save(product);
        return mapToResponse(product);
    }

    @Override
    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        Subcategory subcategory = subcategoryRepository.findById(request.getSubcategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Subcategory not found"));

        product.setSubcategory(subcategory);
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setSlug(request.getSlug());
        product.setSku(request.getSku());
        product.setPrice(request.getPrice());
        product.setDiscountPrice(request.getDiscountPrice());
        product.setStockQuantity(request.getStockQuantity());
        product.setWeight(request.getWeight());
        product.setDimensions(request.getDimensions());
        product.setIsActive(request.getIsActive());

        product = productRepository.save(product);
        return mapToResponse(product);
    }

    @Override
    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse patchProduct(Long id, Map<String, Object> updates) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        final Product finalProduct = product;
        updates.forEach((key, value) -> {
            switch (key) {
                case "name" -> finalProduct.setName((String) value);
                case "description" -> finalProduct.setDescription((String) value);
                case "slug" -> finalProduct.setSlug((String) value);
                case "sku" -> finalProduct.setSku((String) value);
                case "price" -> finalProduct.setPrice(new BigDecimal(value.toString()));
                case "discountPrice" -> finalProduct.setDiscountPrice(value != null ? new BigDecimal(value.toString()) : null);
                case "stockQuantity" -> finalProduct.setStockQuantity((Integer) value);
                case "weight" -> finalProduct.setWeight(value != null ? new BigDecimal(value.toString()) : null);
                case "dimensions" -> finalProduct.setDimensions((String) value);
                case "isActive" -> finalProduct.setIsActive((Boolean) value);
                case "subcategoryId" -> {
                    Subcategory subcategory = subcategoryRepository.findById(((Number) value).longValue())
                            .orElseThrow(() -> new ResourceNotFoundException("Subcategory not found"));
                    finalProduct.setSubcategory(subcategory);
                }
            }
        });

        product = productRepository.save(finalProduct);
        return mapToResponse(product);
    }

    @Override
    @Cacheable(value = "products", key = "#id")
    public ProductResponse getProductById(Long id) {
        Product product = productRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        return mapToResponse(product);
    }

    @Override
    @Cacheable(value = "products", key = "#slug")
    public ProductResponse getProductBySlug(String slug) {
        Product product = productRepository.findBySlugAndIsActiveTrue(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        return mapToResponse(product);
    }

    @Override
    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "products", key = "'all-page-' + #pageable.pageNumber + '-' + #pageable.pageSize")
    public Page<ProductResponse> getAllProducts(Pageable pageable) {
        return productRepository.findAll(pageable)
                .map(this::mapToResponse);
    }

    @Override
    public List<ProductResponse> getActiveProducts() {
        return productRepository.findByIsActiveTrue().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "products", key = "'active-page-' + #pageable.pageNumber + '-' + #pageable.pageSize")
    public Page<ProductResponse> getActiveProducts(Pageable pageable) {
        Page<Product> products = productRepository.findByIsActiveTrue(pageable);
        return products.map(this::mapToResponse);
    }

    @Override
    public List<ProductResponse> searchProductsByName(String keyword) {
        return productRepository.searchByName(keyword).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "products", key = "'search-' + #keyword + '-page-' + #pageable.pageNumber + '-' + #pageable.pageSize")
    public Page<ProductResponse> searchProductsByName(String keyword, Pageable pageable) {
        Page<Product> products = productRepository.searchByName(keyword, pageable);
        return products.map(this::mapToResponse);
    }

    @Override
    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public void deleteProduct(Long id) {
        if (!productRepository.existsById(id)) {
            throw new ResourceNotFoundException("Product not found");
        }
        productRepository.deleteById(id);
    }

    private ProductResponse mapToResponse(Product product) {
        List<String> imageUrls = product.getImages().stream()
                .map(img -> img.getImageUrl())
                .collect(Collectors.toList());

        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .slug(product.getSlug())
                .sku(product.getSku())
                .price(product.getPrice())
                .discountPrice(product.getDiscountPrice())
                .stockQuantity(product.getStockQuantity())
                .weight(product.getWeight())
                .dimensions(product.getDimensions())
                .isActive(product.getIsActive())
                .subcategoryName(product.getSubcategory().getName())
                .categoryName(product.getSubcategory().getCategory().getName())
                .imageUrls(imageUrls)
                .createdAt(product.getCreatedAt())
                .build();
    }
}
